(ns district-voting.components.bottom-logo
  (:require [district0x.components.misc :as misc :refer [row row-with-cols col center-layout paper]]))

(defn bottom-logo []
  [row
   {:center "xs"}
   [:img {:src "./images/district0x-logo-title.svg"
          :style {:width 120 :height 50}}]])