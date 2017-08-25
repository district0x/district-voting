(ns district-voting.components.main-panel
  (:require
    [cljs-react-material-ui.reagent :as ui]
    [clojure.set :as set]
    [district-voting.pages.feedback-page]
    [district-voting.pages.vote-page]
    [district-voting.pages.proposals-page]
    [district-voting.styles :as styles]
    [district0x.components.active-address-select-field :refer [active-address-select-field]]
    [district0x.components.misc :as misc :refer [row row-with-cols col center-layout paper page]]
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

(defn main-panel []
  (let [connection-error? (subscribe [:district0x/blockchain-connection-error?])
        xs-width? (subscribe [:district0x/window-xs-width?])
        active-page (subscribe [:district0x/active-page])]
    (fn []
      (let [{:keys [:handler]} @active-page]
        [misc/main-panel
         {:mui-theme styles/mui-theme}
         [:div
          {:style {:padding-bottom 20
                   :overflow :hidden
                   :position :relative
                   :background "transparent url('./images/blob-big-bottom.png') no-repeat"
                   :background-position "bottom -700px center"
                   :min-height "100%"}}
          [:img {:src "./images/green-blob2.svg"
                 :style styles/blob4}]
          [:img {:src "./images/cyan-blob.svg"
                 :style styles/blob1}]
          [:img {:src "./images/green-blob1.svg"
                 :style styles/blob2}]
          [:img {:src "./images/green-blobs.svg"
                 :style styles/blob3}]
          [ui/app-bar
           {:show-menu-icon-button false
            :style styles/app-bar
            :title (r/as-element [logo])
            :icon-element-right (r/as-element [app-bar-right-elements])}]
          [:div {:style (merge styles/content-wrap
                               (when @xs-width?
                                 (styles/padding-all styles/desktop-gutter-mini)))}
           [center-layout
            ^{:key handler} [page handler]]]]]))))
