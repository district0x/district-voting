(ns district-voting.core
  (:require
    [cljs-time.extend]
    [cljs.spec.alpha :as s]
    [cljsjs.material-ui]
    [cljsjs.react-flexbox-grid]
    [cljsjs.web3]
    [district-voting.components.main-panel :refer [main-panel]]
    [district-voting.db]
    [district-voting.events]
    [district-voting.subs]
    [district0x.events]
    [district0x.subs]
    [goog.string.format]
    [madvas.re-frame.google-analytics-fx :as google-analytics-fx]
    [re-frame.core :refer [dispatch dispatch-sync clear-subscription-cache!]]
    [reagent.core :as r]))

(defn mount-root []
  (s/check-asserts goog.DEBUG)
  (google-analytics-fx/set-enabled! (not goog.DEBUG))
  (clear-subscription-cache!)
  ;(.clear js/console)
  (r/render [main-panel] (.getElementById js/document "app")))

(defn ^:export init []
  (s/check-asserts goog.DEBUG)
  (google-analytics-fx/set-enabled! (not goog.DEBUG))
  (dispatch-sync [:district0x/initialize
                  {:default-db district-voting.db/default-db
                   :effects
                   {:async-flow {:first-dispatch [:district0x/load-smart-contracts {:version "1.0.0"}]
                                 :rules [{:when :seen?
                                          :events [:district0x/smart-contracts-loaded :district0x/my-addresses-loaded]
                                          :dispatch-n [[:watch-my-dnt-balances]
                                                       [:load-voters-count]]}
                                         {:when :seen?
                                          :events [:voters-count-loaded]
                                          :dispatch-n [[:load-votes]]
                                          :halt? true}]}
                    :dispatch-interval {:dispatch [:load-voters-dnt-balances]
                                        :ms 300000
                                        :db-path [:load-voters-dnt-balances-interval]}}}])
  (mount-root))

