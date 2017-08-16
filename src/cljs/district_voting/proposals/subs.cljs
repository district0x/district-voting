(ns district-voting.proposals.subs
  (:require
   [cljs-time.core :as t]
   [cljs-web3.core :as web3]
   [clojure.set :as set]
   [goog.string :as gstring]
   [goog.string.format]
   [medley.core :as medley]
   [re-frame.core :refer [reg-sub]]
   [district-voting.constants :as constants]))

(reg-sub
 ::list
 (fn [db [_ project]]
   ;;constants/next-district-candidates
   (get-in db [:votings project :voting/proposals])))
