(ns district-voting.components.voting-bar
  (:require
    [cljs-react-material-ui.reagent :as ui]
    [district-voting.styles :as styles]
    [district0x.components.misc :as misc :refer [row row-with-cols col center-layout paper]]
    [district0x.utils :as u]
    [re-frame.core :refer [subscribe dispatch]]))

(defn voting-bar []
  (let [can-submit? (subscribe [:district0x/can-submit-into-blockchain?])]
    (fn [{:keys [:votes-total :votes :index :loading? :voting-key :form-key]}]
      (when-not loading?
        [:div
         [ui/linear-progress
          {:mode "determinate"
           :style styles/votes-progress
           :color styles/theme-orange
           :max (if (zero? votes-total) 1 votes-total)
           :value (votes index)}]
         [:div
          {:style styles/text-center}
          [:div
           (u/to-locale-string (votes index) 0) " DNT ("
           (u/to-locale-string (if (pos? votes-total)
                                 (* (/ (votes index) votes-total) 100)
                                 0) 2) "%)"]]
         (let [active-address-voted? (subscribe [:voting/active-address-voted? voting-key index])]
           [row
            {:end "xs"}
            [ui/flat-button
             {:label (if @active-address-voted?
                       "Voted"
                       "Vote")
              :disabled (or (not @can-submit?) @active-address-voted?)
              :primary true
              :on-touch-tap #(dispatch [:voting/vote voting-key form-key {:candidate/index index}])}]])]))))