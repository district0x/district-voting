(ns district-voting.proposals.events
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
   [day8.re-frame.http-fx] ;;Although not referenced, this needed to register an effect
   [district0x.spec-interceptors :refer [validate-db]]
   [district0x.big-number :as bn]
   [district0x.debounce-fx]
   [district0x.events :refer [get-contract get-instance all-contracts-loaded?]]
   [district0x.utils :as u]
   [goog.string :as gstring]
   [goog.string.format]
   [medley.core :as medley]
   [print.foo :as pf :include-macros true]
   [re-frame.core :as re-frame :refer [reg-event-db reg-event-fx inject-cofx path trim-v after debug reg-fx console dispatch]]))

;;TODO: refactor this out to interceptors dir
(def interceptors [trim-v (validate-db :district-voting.db/db)])

(defn- project-url [project]
  "Resolve project's url by project name"
  (str "https://api.github.com/repos/district0x/" project "/issues"))

(reg-event-fx
  ::load
  interceptors
  (fn [{:keys [db]} [project]]
    {:db (assoc-in db [:proposals project :loading?] true)
     :http-xhrio
     {:method          :get
      :uri             (project-url project)
      :timeout         8000                                           ;; optional see API docs
      :response-format (ajax/json-response-format {:keywords? true})  ;; IMPORTANT!: You must provide this.
      :on-success      [::loaded project]
      :on-failure      [::load-failure project]}}))


(reg-event-fx
  ::loaded
  interceptors
  (fn [{:keys [db]} [project result]]
    (pf/look [project result])
    {:db (-> db
             (assoc-in [:proposals project :list] result)
             (assoc-in [:proposals project :loading?] false))}))

(reg-event-fx
 ::load-failure
 interceptors
 (fn [{:keys [db]} [project result]]
   {:db (-> db
            (assoc-in [:proposals project :loading?] false))}))
