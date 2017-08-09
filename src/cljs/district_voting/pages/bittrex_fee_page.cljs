(ns district-voting.pages.bittrex-fee-page
  (:require
    [district-voting.components.bottom-logo :refer [bottom-logo]]
    [district-voting.components.contract-info :refer [contract-info]]
    [district-voting.components.how-to-instructions :refer [how-to-instructions]]
    [district-voting.components.voting-bar :refer [voting-bar]]
    [district-voting.constants :as constants]
    [district-voting.styles :as styles]
    [district0x.components.misc :as misc :refer [row row-with-cols col center-layout paper]]
    [district0x.components.misc :as misc]
    [re-frame.core :refer [subscribe dispatch]]))

(defn bittrex-fee-page []
  (let [votes (subscribe [:voting/candidates-voters-dnt-total :bittrex-fee])
        votes-total (subscribe [:voting/voters-dnt-total :bittrex-fee])
        loading? (subscribe [:voting-loading? :bittrex-fee])
        vote-form (subscribe [:form.bittrex-fee/vote])]
    (fn []
      [paper
       {:loading? (or @loading? (:loading? @vote-form))
        :use-loader? true}
       [:h1 {:style (merge styles/text-center
                           styles/margin-bottom-gutter-less)}
        "district0x Feedback dApp"]
       [row
        [:div "The district0x team is experimenting with a new form of collecting feedback,
        allowing the community to signal their support or disapproval on various topics. These polls will
         in no way have an outcome on the management of funds of the district0x Project,
         district0x Labs Ltd., or any contributions received to build out the district0x Network.
         district0x Network Token holders can signal to have their support or disapproval represented in
         proportion to the number of tokens held at the signaling address.
         To share your feedback, please complete the following steps:"]
        [how-to-instructions]
        [row
         [:h2 {:style (merge styles/text-center
                             styles/margin-bottom-gutter-less
                             styles/margin-top-gutter-less
                             styles/full-width)}
          "district0x Feeback Poll #1"]
         [:div {:style (merge styles/text-center
                              styles/full-width)}
          "Do you support or disapprove of the team’s stance presented "
          [:a {:href "https://district0x.slack.com/files/joe/F6JUAT8TT/Bittrex_Update"
               :target :_blank}
           "here"] "?"]
         [contract-info {:contract-key :bittrex-fee}]
         (doall
           (for [[i {:keys [:title]}] constants/bittrex-fee-candidates]
             [:div
              {:key i
               :style (merge {:margin-top styles/desktop-gutter}
                             styles/full-width)}
              [:h2
               {:style styles/margin-bottom-gutter-mini}
               title]
              [:div
               {:style styles/margin-top-gutter-less}
               [:div "ID: " i]]
              [voting-bar
               {:votes-total @votes-total
                :votes @votes
                :index i
                :loading? @loading?
                :voting-key :bittrex-fee
                :form-key :form.bittrex-fee/vote}]]))]]
       [:div {:style {:height 250}}]                        ; Only for styling purposes
       [bottom-logo]])))