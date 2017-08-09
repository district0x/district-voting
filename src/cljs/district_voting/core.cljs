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
    [print.foo :include-macros true]
    [re-frame.core :refer [dispatch dispatch-sync clear-subscription-cache!]]
    [reagent.core :as r]
    [district-voting.constants :as constants]
    [district0x.utils :as u]))

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
                                          :dispatch-n [[:initialize]]}]}
                    :dispatch-interval {:dispatch [:load-voters-dnt-balances]
                                        :ms 300000
                                        :db-path [:load-voters-dnt-balances-interval]}}}])
  (set! (.-onhashchange js/window)
        #(dispatch [:district0x/set-active-page (u/match-current-location constants/routes)]))
  (mount-root))

