(ns district-voting.components.expandable-text
  (:require [district-voting.styles :as styles]
            [district0x.components.utils :refer [parse-props-children]]
            [reagent.core :as r]))

(defn expandable-text []
  (let [expanded? (r/atom false)]
    (fn [props & children]
      (let [[props children] (parse-props-children props children)
            {:keys [:height] :or {height 60}} props]
        [:div
         (into [:div
                {:style (merge
                          {:position :relative}
                          (when-not @expanded?
                            {:height (+ height 40)
                             :overflow :hidden}))}
                (when-not @expanded?
                  [:div {:style {:background "linear-gradient(to bottom,rgba(255,255,255,0),#fff)"
                                 :position :absolute
                                 :bottom 0
                                 :height height
                                 :left 0
                                 :width "100%"
                                 :z-index 99}}])]
               children)
         [:div
          {:style (merge styles/text-center
                         {:font-size "0.9em"
                          :cursor :pointer})
           :on-click #(swap! expanded? not)}
          (if @expanded?
            "Read less"
            "Read more")]]))))
