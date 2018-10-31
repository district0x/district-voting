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
    [district-voting.web3-fx :as web3-fx]
    [district0x.events]
    [district0x.subs]
    [goog.string.format]
    [madvas.re-frame.google-analytics-fx :as google-analytics-fx]
    [print.foo :include-macros true]
    [re-frame.core :refer [dispatch dispatch-sync clear-subscription-cache! reg-event-fx]]
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
  (dispatch-sync [:initialize-web3])
  (aset js/window "onhashchange"
        #(dispatch [:set-active-page (u/match-current-location constants/routes)]))
  (mount-root))
