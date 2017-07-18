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
    [district0x.big-number :as bn]
    [district0x.events :refer [get-contract get-instance all-contracts-loaded?]]
    [district0x.utils :as u]
    [district0x.debounce-fx]
    [goog.string :as gstring]
    [goog.string.format]
    [medley.core :as medley]
    [print.foo :include-macros true]
    [re-frame.core :as re-frame :refer [reg-event-db reg-event-fx inject-cofx path trim-v after debug reg-fx console dispatch]]))

(def interceptors district0x.events/interceptors)

(reg-event-fx
  :load-voters-count
  interceptors
  (fn [{:keys [db]}]
    (let [district-voting (get-instance db :district-voting)]
      {:web3-fx.contract/constant-fns
       {:fns [[(get-instance db :district-voting)
               :voters-count
               [:voters-count-loaded]
               [:district0x.log/error :load-voters-count]]]}})))

(reg-event-fx
  :voters-count-loaded
  interceptors
  (fn [{:keys [db]} [voters-count]]
    {:db (assoc db :district-voting/voters-count (bn/->number voters-count))}))


(reg-event-fx
  :load-votes
  interceptors
  (fn [{:keys [db]}]
    (let [district-voting (get-instance db :district-voting)
          {:keys [:district-voting/voters-count]} db]
      (merge
        {:web3-fx.contract/constant-fns
         {:fns
          (for [i (range (js/Math.ceil (/ voters-count constants/load-votes-limit)))]
            [(get-instance db :district-voting)
             :get-voters
             (* i constants/load-votes-limit)
             constants/load-votes-limit
             [:voters-loaded]
             [:district0x.log/error :load-votes]])}}
        {:web3-fx.contract/events
         {:db-path [:web3-event-listeners]
          :events [[district-voting :on-vote {} "latest"
                    :district-voting/on-vote [:district0x.log/error :on-vote]]]}}))))


(reg-event-fx
  :voters-loaded
  interceptors
  (fn [{:keys [db]} [[voters candidates]]]
    (let [candidates (map bn/->number candidates)]
      {:db (-> db
             (update :district-voting/candidates
                     (partial reduce
                              (fn [result [i candidate]]
                                (update-in result [candidate :candidate/voters] conj (nth voters i))))
                     (medley/indexed candidates))
             (assoc :votes-loading? false))
       :dispatch [:load-dnt-balances voters]})))

(defn- include-voter [voter voters]
  (conj (set voters) voter))

(defn- exclude-voter [voter voters]
  (set/difference (set voters) #{voter}))

(reg-event-fx
  :district-voting/on-vote
  interceptors
  (fn [{:keys [db]} [{:keys [:candidate :voter]}]]
    (let [candidate-index (bn/->number candidate)]
      {:db (update db :district-voting/candidates
                   (fn [candidates]
                     (medley/map-kv (fn [i candidate]
                                      (let [update-fn (if (= candidate-index i) include-voter exclude-voter)]
                                        [i (update candidate :candidate/voters (partial update-fn voter))]))
                                    candidates)))
       :dispatch [:load-dnt-balances [voter]]})))

(reg-event-fx
  :all-votes-loaded
  interceptors
  (fn [{:keys [db]}]
    {:db (assoc db :votes-loading? false)}))

(reg-event-fx
  :deploy-district-voting-contract
  interceptors
  (fn [{:keys [db]} [{:keys [:address-index]}]]
    {:dispatch [:district0x/deploy-contract {:address-index address-index
                                             :contract-key :district-voting
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
  (fn [{:keys [db]} [{:keys [:votes :gas :random?]
                      :or {gas 200000}}]]
    (let [{:keys [:web3 :my-addresses]} db
          votes (if random?
                  (map #(vec [% (first (shuffle (keys constants/candidates)))]) my-addresses)
                  votes)]
      {:web3-fx.contract/state-fns
       {:web3 web3
        :db-path [:contract/state-fns]
        :fns (for [[from candidate-index] votes]
               [(get-instance db :district-voting)
                :vote
                candidate-index
                {:gas gas
                 :from from}
                [:district0x.log/info]
                [:district0x.log/error :vote]
                [:district0x.form/receipt-loaded gas {:fn-key :vote}]])}})))

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
                                          [:generate-votes {:random? true}]]
                             :halt? true}
                            {:when :seen?
                             :events [:district0x/smart-contracts-loaded]
                             :dispatch-n [[:deploy-district-voting-contract]
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
    (let [addresses (->> (vals (:district-voting/candidates db))
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
  :district-voting/vote
  interceptors
  (fn [{:keys [db]} [form-data address]]
    {:dispatch [:district0x.form/submit
                {:form-data form-data
                 :address address
                 :fn-key :district-voting/vote
                 :fn-args [:candidate/index]
                 :form-key :form.district-voting/vote
                 :on-tx-receipt [:district0x.snackbar/show-message "Thank you! Your vote was successfully processed"]}]}))

