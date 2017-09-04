(ns district-voting.components.contract-info
  (:require
    [district-voting.styles :as styles]
    [district0x.components.misc :as misc]
    [re-frame.core :refer [subscribe dispatch]]))

(defn contract-info []
  (fn [{:keys [:contract-key] :as props}]
    [:div
     (dissoc props :contract-key)
     [:div {:style (merge styles/full-width
                          styles/margin-top-gutter-less)}
      "Contract Address: " [misc/etherscan-link {:address @(subscribe [:contract-address contract-key])}]]
     [:div {:style styles/full-width}
      [:a {:href (str "https://raw.githubusercontent.com/district0x/district-voting/master/resources/public/contracts/build/" @(subscribe [:contract-name contract-key]) ".abi") :target :_blank}
       "ABI / JSON Interface"]]]))
