(ns district-voting.web3-fx
  (:require
   [re-frame.core :refer [reg-fx dispatch]]))


(defn supports-ethereum-provider?
  "Determines whether the browser has the window.ethereum object. All
  browsers are encouraged to implement this object with the method
  `.enable` to invoke an authorization dialog as defined by EIP-1102."
  []
  (boolean (some-> js/window .-ethereum .-enable)))


(defn web3-legacy? []
  (not (some-> js/window .-ethereum)))


(reg-fx
 ::authorize-ethereum-provider
 (fn [{:keys [:on-accept :on-reject :on-error :on-legacy]}]
   (.log js/console "Using EIP-1102 Provider? " (supports-ethereum-provider?))
   (cond
     (supports-ethereum-provider?)
     (doto (-> js/window .-ethereum .enable) ;; js/Promise
       (.then
        #(dispatch on-accept)
        #(dispatch on-reject)))
     (web3-legacy?)
     (dispatch on-legacy)
     :else
     (dispatch on-error))))
