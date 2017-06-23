(ns district-voting.components.main-panel
  (:require
    [cljs-react-material-ui.reagent :as ui]
    [clojure.set :as set]
    [district-voting.styles :as styles]
    [district0x.components.active-address-select-field :refer [active-address-select-field]]
    [district0x.components.misc :as misc :refer [row row-with-cols col center-layout paper]]
    [district0x.utils :as u]
    [medley.core :as medley]
    [re-frame.core :refer [subscribe dispatch]]
    [reagent.core :as r]
    [district-voting.constants :as constants]))

(defn logo []
  [:a
   {:href "https://district0x.io"}
   [:img
    {:style styles/logo
     :src "./images/district0x-logo.svg"}]])

(defn app-bar-right-elements []
  (let [active-address-balance-dnt (subscribe [:district0x/active-address-balance :dnt])
        connection-error? (subscribe [:district0x/blockchain-connection-error?])
        my-addresses (subscribe [:district0x/my-addresses])
        contracts-not-found? (subscribe [:district0x/contracts-not-found?])]
    (fn []
      (when-not @connection-error?
        [row
         {:middle "xs"
          :end "xs"}
         (when (and (seq @my-addresses)
                    @active-address-balance-dnt)
           [:h2.bolder {:style (merge styles/app-bar-balance
                                      {:margin-right 10})}
            (u/format-dnt-with-symbol @active-address-balance-dnt)])
         [active-address-select-field
          {:select-field-props {:style styles/active-address-select-field
                                :label-style styles/active-address-select-field-label
                                :underline-style {:border-color styles/theme-blue}}
           :menu-item-props {:style styles/active-address-menu-item}
           :single-address-props {:style styles/active-address-single}}]]))))

(defn candidate-description []
  (let [expanded? (r/atom false)]
    (fn [content]
      [:div
       [:div
        {:style (merge
                  {:position :relative}
                  (when-not @expanded?
                    {:height 100
                     :overflow :hidden}))}
        (when-not @expanded?
          [:div {:style {:background "linear-gradient(to bottom,rgba(255,255,255,0),#fff)"
                         :position :absolute
                         :bottom 0
                         :height 60
                         :left 0
                         :width "100%"
                         :z-index 99}}])
        content]
       [:div
        {:style (merge styles/text-center
                       {:font-size "0.9em"
                        :cursor :pointer})
         :on-click #(swap! expanded? not)}
        (if @expanded?
          "Read less"
          "Read more")]])))

(defn voting-panel []
  (let [votes (subscribe [:candidates-voters-dnt-total])
        votes-total (subscribe [:voters-dnt-total])
        loading? (subscribe [:votes-loading?])
        can-submit? (subscribe [:district0x/can-submit-into-blockchain?])
        vote-form (subscribe [:form.district-voting/vote])]
    (fn []
      [paper
       {:style {:min-height 600}
        :loading? (or @loading? (:loading? @vote-form))
        :use-loader? true}
       [:h1 {:style styles/text-center}
        "Vote for next district"]
       [row
        (doall
          (for [[i {:keys [:title :description]}] (medley/indexed constants/candidates)]
            [:div
             {:key i
              :style {:margin-top styles/desktop-gutter}}
             [:h2
              {:style styles/margin-bottom-gutter-mini}
              title]
             [candidate-description
              description]
             (when-not @loading?
               [:div
                [ui/linear-progress
                 {:mode "determinate"
                  :style styles/votes-progress
                  :color styles/theme-orange
                  :max (if (zero? @votes-total) 1 @votes-total)
                  :value (@votes i)}]
                [:div
                 {:style styles/text-center}
                 [:div (u/to-locale-string (@votes i) 0) " / " (u/to-locale-string @votes-total 0) " DNT"]]
                (let [active-address-voted? (subscribe [:active-address-voted? i])]
                  [row
                   {:end "xs"}
                   [ui/flat-button
                    {:label (if @active-address-voted?
                              "Voted"
                              "Vote")
                     :disabled (or (not @can-submit?) @active-address-voted?)
                     :primary true
                     :on-touch-tap #(dispatch [:district-voting/vote {:candidate/index i}])}]])])]))]
       [row
        {:center "xs"}
        [:img {:src "./images/district0x-logo-title.svg"
               :style {:width 120 :height 50}}]]])))

(defn main-panel []
  (let [connection-error? (subscribe [:district0x/blockchain-connection-error?])
        xs-width? (subscribe [:district0x/window-xs-width?])]
    (fn []
      [misc/main-panel
       {:mui-theme styles/mui-theme}
       [:div
        {:style {:padding-bottom 20
                 :overflow :hidden
                 :position :relative
                 :min-height "100%"}}
        [:img {:src "./images/green-blob2.svg"
               :style styles/blob4}]
        [:img {:src "./images/cyan-blob.svg"
               :style styles/blob1}]
        [:img {:src "./images/green-blob1.svg"
               :style styles/blob2}]
        [:img {:src "./images/green-blobs.svg"
               :style styles/blob3}]
        [:img {:src "./images/blob-big-bottom.png"
               :style styles/blob5}]
        [ui/app-bar
         {:show-menu-icon-button false
          :style styles/app-bar
          :title (r/as-element [logo])
          :icon-element-right (r/as-element [app-bar-right-elements])}]
        [:div {:style (merge styles/content-wrap
                             (when @xs-width?
                               (styles/padding-all styles/desktop-gutter-mini)))}
         [center-layout
          [voting-panel]]]]])))
