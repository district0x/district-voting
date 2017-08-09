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
  :votings
  (fn [db]
    (:votings db)))

(reg-sub
  :contract-address
  (fn [db [_ contract-key]]
    (get-in db [:smart-contracts contract-key :address])))

(reg-sub
  :current-subdomain
  (fn [db]
    (:current-subdomain db)))

(reg-sub
  :voting-loading?
  (fn [db [_ voting-key]]
    (get-in db [:votings voting-key :loading?])))

(reg-sub
  :form.next-district/vote
  (fn [db]
    (:default (:form.next-district/vote db))))

(reg-sub
  :form.bittrex-fee/vote
  (fn [db]
    (:default (:form.bittrex-fee/vote db))))

(reg-sub
  :voting/voters-dnt-total
  :<- [:district0x/balances]
  :<- [:votings]
  (fn [[balances votings] [_ voting-key]]
    (->> (vals (get-in votings [voting-key :voting/candidates]))
      (reduce #(set/union %1 (:candidate/voters %2)) #{})
      (select-keys balances)
      vals
      (map :dnt)
      (reduce + 0))))

(reg-sub
  :voting/candidates-voters-dnt-total
  :<- [:district0x/balances]
  :<- [:votings]
  (fn [[balances votings] [_ voting-key]]
    (medley/map-vals (fn [{:keys [:candidate/voters]}]
                       (->> voters
                         (select-keys balances)
                         vals
                         (map :dnt)
                         (reduce + 0)))
                     (get-in votings [voting-key :voting/candidates]))))

(reg-sub
  :voting/active-address-voted?
  :<- [:district0x/active-address]
  :<- [:votings]
  (fn [[active-address votings] [_ voting-key candidate-index]]
    (contains? (get-in votings [voting-key :voting/candidates candidate-index :candidate/voters])
               active-address)))


