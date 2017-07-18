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
#_(s/def :candidate/voters-count integer?)
(s/def :candidate/index integer?)
(s/def :district-voting/voters-count integer?)

(s/def :district-voting/candidate (s/keys :opt [:candidate/voters
                                                #_:candidate/voters-count]))

(s/def :district-voting/candidates (s/map-of :candidate/index :district-voting/candidate))

(s/def :form.district-voting/vote (s/map-of :district0x.db/only-default-kw :district0x.db/submit-form))

(def default-db
  (merge
    district0x.db/default-db
    {:load-node-addresses? false
     :node-url "https://mainnet.infura.io" #_"http://localhost:8549"
     :smart-contracts {:district-voting {:name "DistrictVoting" :address "0xb1618a0bff4e017e1932c4f0ac93d27e4c08d17a"}
                       :dnt-token {:name "District0xNetworkToken" :address "0x0abdace70d3790235af448c88547603b945604ea"}}

     :votes-loading? true
     :district-voting/voters-count 0
     :district-voting/candidates (into {}
                                       (for [[id] constants/candidates]
                                         {id {:candidate/voters #{}}}))

     :form.district-voting/vote {:default {:loading? false
                                           :gas-limit 40000
                                           :data {:candidate/index 1}
                                           :errors #{}}}}))