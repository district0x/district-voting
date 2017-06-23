(ns district-voting.subs
  (:require
    [cljs-time.core :as t]
    [cljs-web3.core :as web3]
    [clojure.set :as set]
    [goog.string :as gstring]
    [goog.string.format]
    [medley.core :as medley]
    [re-frame.core :refer [reg-sub]]))

(reg-sub
  :district-voting/candidates
  (fn [db]
    (:district-voting/candidates db)))

(reg-sub
  :votes-loading?
  (fn [db]
    (:votes-loading? db)))

(reg-sub
  :form.district-voting/vote
  (fn [db]
    (:form.district-voting/vote db)))

(reg-sub
  :voters-dnt-total
  :<- [:district0x/balances]
  :<- [:district-voting/candidates]
  (fn [[balances candidates]]
    (->> (vals candidates)
      (reduce #(set/union %1 (:candidate/voters %2)) #{})
      (select-keys balances)
      vals
      (map :dnt)
      (reduce + 0))))

(reg-sub
  :candidates-voters-dnt-total
  :<- [:district0x/balances]
  :<- [:district-voting/candidates]
  (fn [[balances candidates] [_ candidate-index]]
    (medley/map-vals (fn [{:keys [:candidate/voters]}]
                       (->> voters
                         (select-keys balances)
                         vals
                         (map :dnt)
                         (reduce + 0)))
                     candidates)))

(reg-sub
  :active-address-voted?
  :<- [:district0x/active-address]
  :<- [:district-voting/candidates]
  (fn [[active-address candidates] [_ candidate-index]]
    (contains? (get-in candidates [candidate-index :candidate/voters])
               active-address)))


