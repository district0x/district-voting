(ns district-voting.pages.vote-page
  (:require
    [district-voting.components.bottom-logo :refer [bottom-logo]]
    [district-voting.components.contract-info :refer [contract-info]]
    [district-voting.components.how-to-instructions :refer [how-to-instructions]]
    [district-voting.components.voting-bar :refer [voting-bar]]
    [district-voting.components.countdown-timer :refer [countdown]]
    [district-voting.styles :as styles]
    [district0x.components.misc :as misc :refer [row row-with-cols col center-layout paper page]]
    [markdown.core :refer [md->html]]
    [cljs-time.format :as time-format]
    [re-frame.core :refer [subscribe dispatch]]
    [reagent.core :as r]
    [district0x.utils :as u]
    [cljs-react-material-ui.reagent :as ui]
    [district-voting.components.expandable-text :refer [expandable-text]]
    )
  (:require-macros [reagent.ratom :refer [reaction]]))

(defn iso-8601->rfc822 [date]
  (when date
    (let [d (time-format/parse (:date-time-no-ms time-format/formatters) date)]
      (time-format/unparse-local (time-format/formatters :rfc822) d))))

(defn link [name url]
  [:a {:href url
       :target :_blank}
   name])

(def sort-options {:dnt-votes  {:title    "DNT Votes"
                                :reverse? true
                                :cmp-fn   :dnt-votes}
                   :upvotes    {:title    "Github upvotes"
                                :reverse? true
                                :cmp-fn   :reactions}
                   :comments   {:title    "Github comments"
                                :reverse? true
                                :cmp-fn   :comments}
                   :created_at {:title    "Latest"
                                :reverse? true
                                :cmp-fn   :created_at}})

(defn sort-pulldown [selected-value opts]
  [ui/select-field
   {:value @selected-value
    :auto-width true
    :on-change (fn [event idx value]
                 (reset! selected-value (keyword value)))}
   [ui/subheader {} "Sort by"]
   (doall (map (fn [[k v]]
                 ^{:key k}
                 [ui/menu-item
                  {:value k
                   :key k
                   :primary-text (:title v)}]) opts))])

(defmethod page :route.vote/home []
  (let [active-page (subscribe [:district0x/active-page])
        project (reaction (keyword (get-in @active-page [:route-params :project] "next-district")))
        votes (subscribe [:voting/candidates-voters-dnt-total] [project])
        votes-total (subscribe [:voting/voters-dnt-total] [project])
        loading? (subscribe [:voting-loading?] [project])
        can-submit? (subscribe [:district0x/can-submit-into-blockchain?])
        vote-form (subscribe [:voting-form] [project])
        all-proposals-p (subscribe [:proposals/list-open-with-votes-and-reactions] [project])
        limit (r/atom 10)
        sort-order (r/atom :dnt-votes)
        expanded (r/atom nil)
        sorted-proposals (subscribe [:sorted-list sort-options] [all-proposals-p sort-order])
        limited-proposals (subscribe [:limited-list] [sorted-proposals limit])
        time-remaining (subscribe [:voting-time-remaining] [project])]
    (fn []
      [paper
       {:style {:min-height 600}
        :loading? (or @loading? (:loading? @vote-form))
        :use-loader? true}
       [:h1 {:style (merge styles/text-center
                           styles/margin-bottom-gutter-less)}
        (str "What should we build next for " (name @project) "?")]
       [row
        [:div "The district0x project is open source and community-driven. As such, prioritization of the development of specific issues for the various districts happens according to the will of the community of token holders. To signal for the issue you would like to see worked on next, please complete the following steps:"
         [how-to-instructions]
         [:div [:strong "Note:"] " You may only vote for one issue per address at a time. No DNT are transferred when signaling, the voting mechanism simply registers your indication to your address. As such, the entire DNT balance stored at that address would be counted towards the vote. Once DNT is transferred to a new address, the district's vote total would be lowered by a corresponding amount. Your vote can be changed at any time by voting again from the same address."]
         (if @time-remaining
           [:div [countdown (assoc @time-remaining
                                   :caption "Time remaining: ")]]
           [:div [:strong "Note:"]" No date has been set for the closure of the current voting period. Stay tuned for updates!"])
         [contract-info {:contract-key @project
                         :style styles/margin-bottom-gutter-less}]]
        [:div
         {:style {:width "100%"
                  :margin-right 10}}
         [row
          {:end "xs"}
          [col
           [:div
            {:style
             {:margin-top "12px"
              :font-size "1.3em"
              :margin-right "20px"}}(str "Total votes: " (u/to-locale-string @votes-total 0)  " DNT")]]
          [col
           [sort-pulldown sort-order sort-options]]]]
        (doall
         (for [{:keys [:number
                       :title
                       :body
                       :html_url
                       :dnt-votes
                       :comments
                       :reactions
                       :created_at]} @limited-proposals]
            [:div
             {:key (str @project number)
              :style {:margin-top styles/desktop-gutter
                      :width "100%"}}
             [:h2
              {:style (:merge styles/margin-bottom-gutter-mini
                              {:font-size "1.6em"})}
              title]
             ;; WARN: This is as safe as https://github.com/leizongmin/js-xss lib.
             (when (= @expanded number )
               [:div {:style {:overflow-x :auto}}
                [:div {:dangerouslySetInnerHTML
                       {:__html ((aget js/window "filterXSS") (md->html body))}}]
                [:div {:style (merge styles/text-center
                                     {:font-size "0.9em"
                                      :cursor :pointer})}
                 [:a {:href "#"
                      :on-click (fn [e]
                                  (.preventDefault e)
                                  (reset! expanded nil))}
                  "Hide description"]]])
             [:div
              {:style styles/margin-top-gutter-less}
              [:div "ID: " number]
              [:div "Created: " (iso-8601->rfc822 created_at)]
              [:div "Github upvotes: " reactions]
              [:div "Github comments: " comments]
              [:div [:a {:href html_url
                         :target :_blank}
                     "Open in Github"]]
              [:div (if-not (= @expanded number )
                      [:a {:href "#"
                           :on-click (fn [e]
                                       (.preventDefault e)
                                       (reset! expanded number))}
                       "Show description"])]]
             [voting-bar
              {:votes-total @votes-total
               :votes @votes
               :index number
               :loading? @loading?
               :voting-disabled? (and
                                  @time-remaining
                                  (every? zero? (vals @time-remaining)))
               :voting-key @project}]]))]
       (when (< (count @limited-proposals) (count @sorted-proposals))
         [ui/flat-button
          {:label "View all"
           :primary true
           :on-touch-tap #(reset! limit 0)}])
       [bottom-logo]])))
