(ns district-voting.components.countdown-timer
  (:require
   [district-voting.styles :as styles]
   [district0x.utils :as u]))

(defn countdown [time-remaining]
  (let [{:keys [:seconds :minutes :hours :days :caption]} time-remaining]
    [:h3
     {:style (merge styles/full-width
                    styles/text-center
                    styles/margin-top-gutter-less)}
     (if caption caption
         "remaining ")
     days " " (u/pluralize "day" days) " "
     hours " " (u/pluralize "hour" hours) " "
     minutes " " (u/pluralize "minute" minutes) " "
     seconds " " (u/pluralize "second" seconds) " "]))
