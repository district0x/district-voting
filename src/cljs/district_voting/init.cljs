(ns district-voting.init
  (:require
   [district-voting.db]
   ))

(def start-loading-event
  [:district0x/initialize
   {:effects
    {:async-flow {:first-dispatch [:district0x/load-smart-contracts {:version "1.0.0"}]
                  :rules [{:when :seen?
                           :events [:district0x/smart-contracts-loaded :district0x/my-addresses-loaded]
                           :dispatch-n [[:initialize]]}]}
     :dispatch-interval {:dispatch [:load-voters-dnt-balances]
                         :ms 300000
                         :db-path [:load-voters-dnt-balances-interval]}}}])
