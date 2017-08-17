(ns district-voting.pages.vote-page
  (:require
    [district-voting.components.bottom-logo :refer [bottom-logo]]
    [district-voting.components.contract-info :refer [contract-info]]
    [district-voting.components.how-to-instructions :refer [how-to-instructions]]
    [district-voting.components.voting-bar :refer [voting-bar]]
    [district-voting.styles :as styles]
    [district0x.components.misc :as misc :refer [row row-with-cols col center-layout paper page]]
    [district-voting.proposals.subs :as proposal-subs]
    [markdown.core :refer [md->html]]
    [re-frame.core :refer [subscribe dispatch]]
    [reagent.core :as r]
    [district0x.utils :as u]
    [cljs-react-material-ui.reagent :as ui]
    [district-voting.components.expandable-text :refer [expandable-text]]
    )
  (:require-macros [reagent.ratom :refer [reaction]]))

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
  (let [votes (subscribe [:voting/candidates-voters-dnt-total :next-district])
        votes-total (subscribe [:voting/voters-dnt-total :next-district])
        loading? (subscribe [:voting-loading? :next-district])
        can-submit? (subscribe [:district0x/can-submit-into-blockchain?])
        vote-form (subscribe [:form.next-district/vote])
        all-proposals-p (subscribe [::proposal-subs/list-open-with-votes-and-reactions :next-district])
        limit (r/atom 10)
        sort-order (r/atom :dnt-votes)
        sorted-proposals (subscribe [:sorted-list sort-options] [all-proposals-p sort-order])
        limited-proposals (subscribe [:limited-list] [sorted-proposals limit])]
    (fn []
      [paper
       {:style {:min-height 600}
        :loading? (or @loading? (:loading? @vote-form))
        :use-loader? true}
       [:h1 {:style (merge styles/text-center
                           styles/margin-bottom-gutter-less)}
        "What should we build next?"]
       [row
        [:div "district0x makes use of a " [link "district proposal process" "https://github.com/district0x/district-proposals"]
         " to allow the community to determine what districts they would like to see built and deployed to the network next by the district0x team.  To signal for a district you would like to see launched, please complete the following steps:"
         [how-to-instructions]
         [:div "Note: You may only vote for one district per address at a time. No DNT are transferred when signaling, the voting mechanism simply registers your indication to your address. As such, the entire DNT balance stored at that address would be counted towards the vote. Once DNT is transferred to a new address, the district's vote total would be lowered by a corresponding amount. Your vote can be changed at any time by voting again from the same address."]
         [contract-info {:contract-key :next-district
                         :style styles/margin-bottom-gutter-less}]]
        [:div
         {:style {:width "100%"
                  :margin-right 10}}
         [row
          {:end "xs"}
          [sort-pulldown sort-order sort-options]]]
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
             {:key number
              :style {:margin-top styles/desktop-gutter
                      :width "100%"}}
             [:h2
              {:style styles/margin-bottom-gutter-mini}
              title]
             ;;TODO: This is not safe. Do we need formatting?
             ;; [:div {:dangerouslySetInnerHTML
             ;;        {:__html (md->html body)}}]
             [expandable-text
              body]
             [:div
              {:style styles/margin-top-gutter-less}
              [:div "ID: " number]
              [:div "Created: " created_at]
              [:div "Github upvotes: " reactions]
              [:div "Github comments: " comments]
              [:a {:href html_url
                   :target :_blank}
               "Open in Github"]]
             [voting-bar
              {:votes-total @votes-total
               :votes @votes
               :index number
               :loading? @loading?
               :voting-key :next-district
               :form-key :form.next-district/vote}]]))]
       (when (< (count @limited-proposals) (count @sorted-proposals))
         [ui/flat-button
          {:label "View all"
           ;;:disabled (or (not @can-submit?) @active-address-voted? voting-disabled?)
           :primary true
           :on-touch-tap #(reset! limit 0)}])
       [bottom-logo]])))
