(ns district-voting.proposals.subs
  (:require
   [cljs-time.core :as t]
   [cljs-web3.core :as web3]
   [clojure.set :as set]
   [goog.string :as gstring]
   [goog.string.format]
   [medley.core :as medley]
   [re-frame.core :refer [reg-sub]]
   [re-frame.subs :as sbs]
   [print.foo :as pf :include-macros true]
   [district-voting.constants :as constants]))

(defn count-reactions [reactions]
  (let [r (dissoc reactions :url :total_count)
        ops [[#{:+1} +]]]
    (reduce (fn [acc [sign cnt]]
              (let [op (some (fn [[signs op]]
                               (if-let [o (contains? signs sign)]
                                 op
                                 #(identity %1))) ops)]
                (op acc cnt))) 0 r)))

(reg-sub
 ::list
 (fn [db [_ project]]
   (get-in db [:votings project :voting/proposals])))

(reg-sub
 ::list-open-with-votes-and-reactions
 (fn [[_ project]]
   {:lst  (sbs/subscribe [::list project])
    :votes (sbs/subscribe [:voting/candidates-voters-dnt-total project])})
 (fn [{:keys [lst votes]} _]
   (doall (map (fn [p]
                 (-> p
                     (assoc :dnt-votes (get votes (:number p)))
                     (update :reactions count-reactions)))
               (filter (fn [p]
                         (= (:state p)
                            "open")) lst)))))


