(ns district-voting.events
  (:require
    [ajax.core :as ajax]
    [akiroz.re-frame.storage :as re-frame-storage]
    [cljs-time.coerce :as time-coerce]
    [cljs-time.core :as t]
    [cljs-web3.core :as web3]
    [cljs-web3.eth :as web3-eth]
    [cljs-web3.personal :as web3-personal]
    [cljs-web3.utils :as web3-utils]
    [cljs.spec.alpha :as s]
    [clojure.data :as data]
    [clojure.set :as set]
    [clojure.string :as string]
    [day8.re-frame.async-flow-fx]
    [district-voting.constants :as constants]
    [district-voting.proposals.events :as proposal-events]
    [district0x.spec-interceptors :refer [validate-db]]
    [district0x.big-number :as bn]
    [district0x.debounce-fx]
    [district0x.events :refer [get-contract get-instance all-contracts-loaded?]]
    [district0x.utils :as u]
    [goog.string :as gstring]
    [goog.string.format]
    [medley.core :as medley]
    [print.foo :include-macros true]
    [re-frame.core :as re-frame :refer [reg-event-db reg-event-fx inject-cofx path trim-v after debug reg-fx console dispatch]]))

(def interceptors [trim-v (validate-db :district-voting.db/db)])

(def subdomain->initial-dispatch-n
  {"vote" [[:load-voters-count :next-district]
           [::proposal-events/load "district-proposals"]]
   "feedback" [[:load-voters-count :bittrex-fee]
               [:setup-update-now-interval]]})

(reg-event-fx
  :initialize
  interceptors
  (fn [{:keys [:db]}]
    {:dispatch [:watch-my-dnt-balances]
     :dispatch-n (subdomain->initial-dispatch-n constants/current-subdomain)}))

(reg-event-fx
  :setup-update-now-interval
  interceptors
  (fn [{:keys [db]}]
    {:dispatch-interval {:dispatch [:update-now]
                         :ms 1000
                         :db-path [:update-now-interval]}}))

(reg-event-fx
  :update-now
  interceptors
  (fn [{:keys [db]}]
    {:db (assoc db :now (t/now))}))

(reg-event-fx
  :load-voters-count
  interceptors
  (fn [{:keys [db]} [voting-key]]
    (let [district-voting (get-instance db voting-key)]
      {:web3-fx.contract/constant-fns
       {:fns [[(get-instance db voting-key)
               :voters-count
               [:voters-count-loaded voting-key]
               [:district0x.log/error :load-voters-count]]]}})))

(reg-event-fx
  :voters-count-loaded
  interceptors
  (fn [{:keys [db]} [voting-key voters-count]]
    {:db (assoc-in db [:votings voting-key :voting/voters-count] (bn/->number voters-count))
     :dispatch [:load-votes voting-key]}))


(reg-event-fx
  :load-votes
  interceptors
  (fn [{:keys [db]} [voting-key]]
    (let [instance (get-instance db voting-key)
          {:keys [:voting/voters-count]} (get-in db [:votings voting-key])]
      (merge
        {:web3-fx.contract/constant-fns
         {:fns
          (for [i (range (js/Math.ceil (/ voters-count constants/load-votes-limit)))]
            [instance
             :get-voters
             (* i constants/load-votes-limit)
             constants/load-votes-limit
             [:voters-loaded voting-key]
             [:district0x.log/error :load-votes]])}}
        {:web3-fx.contract/events
         {:db-path [:web3-event-listeners]
          :events [[instance :on-vote {} "latest"
                    [:district-voting/on-vote voting-key]
                    [:district0x.log/error :on-vote voting-key]]]}}))))


(reg-event-fx
  :voters-loaded
  interceptors
  (fn [{:keys [db]} [voting-key [voters candidates]]]
    (let [candidates (map bn/->number candidates)]
      {:db (-> db
             (update-in [:votings voting-key :voting/candidates]
                        (partial reduce
                                 (fn [result [i candidate]]
                                   (update-in result [candidate :candidate/voters] conj (nth voters i))))
                        (medley/indexed candidates))
             (assoc-in [:votings voting-key :loading?] false))
       :dispatch [:load-dnt-balances voters]})))

(defn- include-voter [voter voters]
  (conj (set voters) voter))

(defn- exclude-voter [voter voters]
  (set/difference (set voters) #{voter}))

(reg-event-fx
  :district-voting/on-vote
  interceptors
  (fn [{:keys [:db]} [voting-key {:keys [:candidate :voter]}]]
    (let [candidate-index (bn/->number candidate)]
      {:db (update-in db [:votings voting-key :voting/candidates]
                      (fn [candidates]
                        (medley/map-kv (fn [i candidate]
                                         (let [update-fn (if (= candidate-index i) include-voter exclude-voter)]
                                           [i (update candidate :candidate/voters (partial update-fn voter))]))
                                       candidates)))
       :dispatch [:load-dnt-balances [voter]]})))

(reg-event-fx
  :deploy-district-voting-contract
  interceptors
  (fn [{:keys [db]} [{:keys [:address-index :voting-key :voting/end-block]}]]
    {:dispatch [:district0x/deploy-contract {:address-index address-index
                                             :contract-key voting-key
                                             :args (when end-block [end-block])
                                             :gas 2000000}]}))

(reg-event-fx
  :deploy-dnt-token-contract
  interceptors
  (fn [{:keys [db]} [{:keys [:address-index :dnt-token/controller :dnt-token/minime-token-factory]}]]
    {:dispatch [:district0x/deploy-contract {:address-index address-index
                                             :contract-key :dnt-token
                                             :args [controller minime-token-factory]
                                             :on-success [:dnt-token-contract-deployed]}]}))

(reg-event-fx
  :dnt-token-contract-deployed
  interceptors
  (fn [{:keys [db]}]
    ))

(reg-event-fx
  :generate-dnt-tokens
  interceptors
  (fn [{:keys [:db]} [{:keys [:from :dnt-balances :gas :random?]
                       :or {gas 300000}}]]
    (let [{:keys [:my-addresses :web3]} db
          dnt-balances (if random?
                         (map #(vec [% (u/eth->wei (/ (rand-int 1000000) 100))]) my-addresses))]
      {:web3-fx.contract/state-fns
       {:web3 web3
        :db-path [:contract/state-fns]
        :fns (for [[address balance] dnt-balances]
               [(get-instance db :dnt-token)
                :generate-tokens
                address
                balance
                {:gas gas
                 :from from}
                [:district0x.log/info]
                [:district0x.log/error :generate-dnt-tokens]
                [:district0x.form/receipt-loaded gas {:fn-key :generate-tokens}]])}})))

(reg-event-fx
  :generate-votes
  interceptors
  (fn [{:keys [db]} [{:keys [:votes :gas :random? :voting-key :candidates]
                      :or {gas 200000}}]]
    (let [{:keys [:web3 :my-addresses]} db
          votes (if random?
                  (map #(vec [% (first (shuffle (keys candidates)))]) my-addresses)
                  votes)]
      {:web3-fx.contract/state-fns
       {:web3 web3
        :db-path [:contract/state-fns]
        :fns (for [[from candidate-index] votes]
               [(get-instance db voting-key)
                :vote
                candidate-index
                {:gas gas
                 :from from}
                [:district0x.log/info]
                [:district0x.log/error :vote]
                [:district0x.form/receipt-loaded gas {:fn-key :vote
                                                      :candidate candidate-index
                                                      :voting-key voting-key}]])}})))

(reg-event-fx
  :reinitialize
  interceptors
  (fn [{:keys [:db]} args]
    (let [{:keys [:my-addresses]} db]
      (.clear js/console)
      {:dispatch [:district0x/clear-smart-contracts]
       :async-flow {:first-dispatch [:district0x/load-smart-contracts]
                    :rules [{:when :seen?
                             :events [:dnt-token-contract-deployed]
                             :dispatch-n [[:generate-dnt-tokens {:random? true
                                                                 :from (first my-addresses)}]
                                          [:generate-votes {:random? true
                                                            :voting-key :next-district
                                                            :candidates constants/next-district-candidates}]
                                          [:generate-votes {:random? true
                                                            :voting-key :bittrex-fee
                                                            :candidates constants/bittrex-fee-candidates}]]
                             :halt? true}
                            {:when :seen?
                             :events [:district0x/smart-contracts-loaded]
                             :dispatch-n [[:deploy-district-voting-contract {:voting-key :next-district}]
                                          [:deploy-district-voting-contract {:voting-key :bittrex-fee}]
                                          [:deploy-dnt-token-contract
                                           {:dnt-token/controller (first my-addresses)
                                            :dnt-token/minime-token-factory 0x0}]]}]}})))


(reg-event-fx
  :load-dnt-balances
  [interceptors]
  (fn [{:keys [db]} [addresses]]
    {:dispatch [:district0x/load-token-balances
                {:addresses (remove #(get-in db [:balances % :dnt]) addresses)
                 :instance (get-instance db :dnt-token)
                 :token-code :dnt}]}))

(reg-event-fx
  :load-voters-dnt-balances
  [interceptors]
  (fn [{:keys [db]}]
    (let [addresses (->> (:votings db)
                      vals
                      (map :voting/candidates)
                      (map vals)
                      flatten
                      (map :candidate/voters)
                      (reduce set/union))]
      {:dispatch [:district0x/load-token-balances
                  {:addresses addresses
                   :instance (get-instance db :dnt-token)
                   :token-code :dnt}]})))

(reg-event-fx
  :watch-my-dnt-balances
  [interceptors]
  (fn [{:keys [db]}]
    {:dispatch [:district0x/watch-token-balances
                {:addresses (:my-addresses db)
                 :instance (get-instance db :dnt-token)
                 :token-code :dnt}]}))

(reg-event-fx
  :voting/vote
  interceptors
  (fn [{:keys [db]} [voting-key form-key form-data address]]
    {:dispatch [:district0x.form/submit
                {:form-data form-data
                 :address address
                 :fn-key (keyword voting-key :vote)
                 :fn-args [:candidate/index]
                 :tx-opts {:gas-price (web3/to-wei 4 :gwei)}
                 :form-key form-key
                 :on-tx-receipt [:district0x.snackbar/show-message "Thank you! Your vote was successfully processed"]}]}))

