(ns district-voting.db
  (:require
    [cljs-time.core :as t]
    [cljs-web3.core :as web3]
    [cljs.spec.alpha :as s]
    [district-voting.constants :as constants]
    [district0x.db]
    [district0x.utils :as u]
    [re-frame.core :refer [dispatch]]))

(s/def :candidate/voters (s/coll-of u/address?))
(s/def :candidate/index integer?)
(s/def :district-voting/candidate (s/keys :opt [:candidate/voters]))

(s/def :voting/candidates (s/map-of :candidate/index :district-voting/candidate))
(s/def :voting/voters-count integer?)
(s/def ::voting-key keyword?)
(s/def ::loading? boolean?)

(s/def ::votings (s/map-of ::voting-key (s/keys :req-un [::loading?]
                                                :req [:voting/voters-count
                                                      :voting/candidates])))

(s/def :form.next-district/vote (s/map-of :district0x.db/only-default-kw :district0x.db/submit-form))
(s/def :form.bittrex-fee/vote (s/map-of :district0x.db/only-default-kw :district0x.db/submit-form))

(s/def ::db (s/merge :district0x.db/db
                     (s/keys :req-un [::votings]
                             :req [:form.next-district/vote
                                   :form.bittrex-fee/vote])))

(defn setup-candidates [candidates]
  (into {}
        (for [[id] candidates]
          {id {:candidate/voters #{}}})))

(def default-db
  (merge
    district0x.db/default-db
    {:load-node-addresses? true
     :node-url ;;"https://mainnet.infura.io" 
     "http://localhost:8549"
     :active-page (u/match-current-location constants/routes)
     :routes constants/routes
     :smart-contracts {:dnt-token {:name "District0xNetworkToken" :address "0x0abdace70d3790235af448c88547603b945604ea"}
                       :next-district {:name "DistrictVoting" :address "0xb1618a0bff4e017e1932c4f0ac93d27e4c08d17a"}
                       :bittrex-fee {:name "DistrictVoting" :address "0x2643957a7fbb444755ded8b3615fb54d648411eb"}}

     :now (t/now)
     :votings {:next-district {:voting/voters-count 0
                               :voting/candidates (setup-candidates {})
                               :loading? true}

               :bittrex-fee {:voting/voters-count 0
                             :voting/candidates (setup-candidates constants/bittrex-fee-candidates)
                             :loading? true
                             :end-time (t/date-time 2017 8 12 4)}}

     :form.next-district/vote {:default {:loading? false
                                         :gas-limit 100000
                                         :data {:candidate/index 1}
                                         :errors #{}}}

     :form.bittrex-fee/vote {:default {:loading? false
                                       :gas-limit 100000
                                       :data {:candidate/index 1}
                                       :errors #{}}}}))
