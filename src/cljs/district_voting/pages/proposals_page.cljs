(ns district-voting.pages.proposals-page
  (:require
    [district-voting.constants :as constants]
    [district-voting.styles :as styles]
    [district-voting.components.bottom-logo :refer [bottom-logo]]
    [district0x.components.misc :as misc :refer [row row-with-cols col center-layout paper page]]
    [re-frame.core :refer [subscribe dispatch]]))

(defmethod page :route.vote/proposals []
  (fn []
    [paper
     {:style {:min-height 600}
      :use-loader? true}
     [:h1 {:style (merge styles/text-center
                         styles/margin-bottom-gutter-less)}
      "District Proposals"]
     [row
      ]]))


