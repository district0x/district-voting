(ns district-voting.db
  (:require
    [cljs-time.core :as t]
    [cljs-web3.core :as web3]
    [cljs.spec :as s]
    [district-voting.constants :as constants]
    [district0x.db]
    [district0x.utils :as u]
    [re-frame.core :refer [dispatch]]))

(s/def :candidate/voters (s/coll-of u/address?))
#_(s/def :candidate/voters-count integer?)
(s/def :candidate/index integer?)

(s/def :district-voting/candidate (s/keys :opt [:candidate/voters
                                                #_:candidate/voters-count]))

(s/def :district-voting/candidates (s/map-of :candidate/index :district-voting/candidate))

(s/def :form.district-voting/vote ::district0x.db/submit-form)

(def default-db
  (merge
    district0x.db/default-db
    {:load-node-addresses? true
     :node-url #_"https://mainnet.infura.io/" "http://localhost:8549"
     :smart-contracts {:district-voting {:name "DistrictVoting" :address "0xfbfe6376417ec60322909ae8b9de3ee3de268d9d"}
                       :dnt-token {:name "District0xNetworkToken" :address "0x9188ca329c7a6bb7f9fd8346624cb6e14487d557"}}

     :votes-loading? true
     :district-voting/candidates (into {}
                                       (for [i (range (count constants/candidates))]
                                         {i {:candidate/voters #{}}}))

     :form.district-voting/vote {:loading? false
                                 :gas-limit 50000
                                 :data {:candidate/index 1}
                                 :errors #{}}}))