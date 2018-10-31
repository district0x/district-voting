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
   [district0x.spec-interceptors :refer [validate-db]]
   [district0x.big-number :as bn]
   [district0x.debounce-fx]
   [district0x.events :refer [get-contract get-instance all-contracts-loaded?]]
   [district0x.utils :as u]
   [goog.string :as gstring]
   [goog.string.format]
   [medley.core :as medley]
   [print.foo :include-macros true]
   [district-voting.db :refer [setup-candidates]]
   [district-voting.web3-fx :as web3-fx]
   [print.foo :refer [look tap]]
   [re-frame.core :as re-frame :refer [reg-event-db reg-event-fx inject-cofx path trim-v after debug reg-fx console dispatch]]))

(def interceptors [trim-v (validate-db :district-voting.db/db)])

(def subdomain->initial-events
  {"vote" (fn [p]
            {:async-flow
             {:first-dispatch [:repos/load]
              :rules [{:when :seen?
                       :events [:repos/loaded]
                       :dispatch [:proposals/load p]}
                      {:when :seen?
                       :events [:proposals/loaded]
                       :dispatch-n [[:load-voters-count p]
                                    [:setup-update-now-interval]]}]}})
   "feedback" (fn [_]
                {:dispatch-n [[:load-voters-count :bittrex-fee]
                              [:setup-update-now-interval]]})})

(def d0x-init-event
  [:district0x/initialize
   {:default-db district-voting.db/default-db
    :effects
    {:async-flow {:first-dispatch [:district0x/load-smart-contracts {:version "1.0.0"}]
                  :rules [{:when :seen?
                           :events [:district0x/smart-contracts-loaded :district0x/my-addresses-loaded]
                           :dispatch-n [[:initialize]]}]}
     :dispatch-interval {:dispatch [:load-voters-dnt-balances]
                         :ms 300000
                         :db-path [:load-voters-dnt-balances-interval]}}}])

(reg-event-fx
 :initialize-web3
 []
 (fn [{:keys [:db]} _]
   {:db district-voting.db/default-db
    ::web3-fx/authorize-ethereum-provider
    {:on-accept d0x-init-event
     :on-reject d0x-init-event
     :on-error d0x-init-event
     :on-legacy d0x-init-event}}))

(reg-event-fx
 :initialize
 interceptors
 (fn [{:keys [:db]}]
   (let [project (keyword (get-in db [:active-page :route-params :project] :next-district))]
     (merge
      {:dispatch [:watch-my-dnt-balances]}
      ((subdomain->initial-events constants/current-subdomain)
       project)))))

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
                                                            :voting-key :namebazaar
                                                            :candidates constants/next-district-candidates}]
                                          [:generate-votes {:random? true
                                                            :voting-key :bittrex-fee
                                                            :candidates constants/bittrex-fee-candidates}]]
                             :halt? true}
                            {:when :seen?
                             :events [:district0x/smart-contracts-loaded]
                             :dispatch-n [[:deploy-district-voting-contract {:voting-key :next-district}]
                                          [:deploy-district-voting-contract {:voting-key :bittrex-fee}]
                                          [:deploy-district-voting-contract {:voting-key :namebazaar}]
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
  (fn [{:keys [db]} [voting-key form-data address]]
    (let [form-key (get-in db [:voting-forms voting-key])
          v-data {:form-data form-data
                  :address address
                  :fn-key (keyword voting-key :vote)
                  :fn-args [:candidate/index]
                  :tx-opts {:gas-price (web3/to-wei 4 :gwei)}
                  :form-key form-key
                  :on-tx-receipt [:district0x.snackbar/show-message "Thank you! Your vote was successfully processed"]}]
      {:dispatch [:district0x.form/submit
                  v-data]})))


(def repos-url
  "https://api.github.com/users/district0x/repos")

(reg-event-fx
 :repos/load
 interceptors
 (fn [{:keys [db]} [project]]
   {:db (assoc-in db [:repos :loading?] true)
    :http-xhrio
    {:method          :get
     :uri             repos-url
     :params          {:per_page 1000}
     :timeout         8000
     :response-format (ajax/text-response-format)
     :on-success      [:repos/loaded]
     :on-failure      [:repos/load-failure]}}))

(reg-event-fx
 :repos/loaded
 interceptors
 (fn [{:keys [db]} [raw-result :as p]]
   (let [parsed (.parse js/JSON raw-result)
         repos (js->clj parsed :keywordize-keys true)]
     {:db (-> db
              (assoc-in [:repos :list] repos)
              (assoc-in [:repos :loading?] false))})))

(reg-event-fx
 :repos/load-failure
 interceptors
 (fn [{:keys [db]} [result]]
   {:db (-> db
            (assoc-in [:repos :loading?] false))
    :dispatch [:district0x.snackbar/show-message "Sorry, we couldn't fetch projects descriptions"]}))

(defn- project-desc [project]
  "Resolve project's url by project name"
  (let [resolve-table {:next-district "district-proposals"
                       :namebazaar "name-bazaar"}]
    ;;XSS example https://api.github.com/repos/wambat/ateam/issues
    (when-let [nm (get resolve-table project)]
      {:name nm
       :url (str "https://api.github.com/repos/district0x/" nm "/issues")})))

(reg-event-fx
  :proposals/load
  interceptors
  (fn [{:keys [db]} [project paging]]
    (let [paging (if paging paging
                     {:per_page 50
                      :page 1})]
      (if-let [pdesc (project-desc project)]
        {:db (assoc-in db [:proposals project :loading?] true)
         :http-xhrio
         {:method          :get
          :uri             (:url pdesc)
          :params          paging
          :headers         {"Accept"  "application/vnd.github.squirrel-girl-preview"}
          :timeout         8000
          ;;:response-format (ajax/json-response-format {:keywords? true}) ;;Troubles reading json viag goog lib
          :response-format (ajax/text-response-format)
          :on-success      [:proposals/chunk-loaded project paging]
          :on-failure      [:proposals/load-failure project]}}
        {:db db
         :dispatch [:district0x.snackbar/show-message "Sorry, we can't find the project."]}))))

(reg-event-fx
  :proposals/chunk-loaded
  interceptors
  (fn [{:keys [db]} [project paging raw-result :as p]]
    (let [repos (get-in db [:repos :list])
          repo-name (:name (project-desc project))
          issues-count (some (fn [repo]
                               (if (= (:name repo)
                                      repo-name)
                                 (:open_issues_count repo)))
                             repos)
          parsed (.parse js/JSON raw-result)
          result (js->clj parsed :keywordize-keys true)

          proposals ((fnil concat [])
                     (get-in db [:votings project :voting/proposals])
                     result)]
      {:db (-> db
               (assoc-in [:votings project :voting/proposals] proposals))
       :dispatch (if (< (* (:per_page paging)
                           (:page paging)) issues-count)
                   [:proposals/load project (update paging :page inc)]
                   [:proposals/loaded project])})))

(reg-event-fx
  :proposals/loaded
  interceptors
  (fn [{:keys [db]} [project]]
    (let [index-proposals-fn #(into {}
                                    (map (fn [p]
                                           [(:number p) p])
                                         %))]
      {:db (-> db
               (assoc-in [:votings project :voting/candidates] (setup-candidates
                                                                (index-proposals-fn
                                                                 (get-in db [:votings project :voting/proposals]))))
               (assoc-in [:votings project :loading?] false))})))

(reg-event-fx
 :proposals/load-failure
 interceptors
 (fn [{:keys [db]} [project result]]
   {:db (-> db
            (assoc-in [:votings project :loading?] false))
    :dispatch [:district0x.snackbar/show-message "Sorry, we couldn't fetch voting issues."]}))

(reg-event-fx
 :reinit
 interceptors
 (fn [{:keys [:db]} _]
   {:db db
    :dispatch [:initialize]
    ;; :async-flow {:first-dispatch [:district0x/load-smart-contracts {:version "1.0.0"}]
    ;;              :rules [{:when :seen?
    ;;                       :events [:district0x/smart-contracts-loaded :district0x/my-addresses-loaded]
    ;;                       :dispatch-n [[:initialize]]}]}
    }))

(reg-event-fx
 :set-active-page
 interceptors
 (fn [{:keys [:db]} _]
   (let [match (u/match-current-location constants/routes)
         new-p (get-in (u/match-current-location constants/routes)
                       [:route-params :project] "next-district")
         old-p (get-in db [:active-page :route-params :project] "next-district")]
     (if-not (= new-p old-p)
       {:dispatch-n [[:district0x/set-active-page match]
                     [:reinit]]}
       {:db db
        :dispatch [:district0x/set-active-page match]}))))
