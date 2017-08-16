(ns district-voting.constants
  (:require [cemerick.url :as url]))

(defn link [name url]
  [:a {:href url
       :target :_blank}
   name])

(def load-votes-limit 30)

#_(def next-district-candidates
  {3 {:title "1Hive"
      :description [:div "The 1Hive Funding platform would offer a standardized and accountable crowdfunding dapp on top of a curation market. Similar to " [link "Patreon" "https://www.patreon.com/"] " or " [link "Kickstarter" "https://www.kickstarter.com/"] ", an individual or group could champion a project and solicit support from the community to provide funding to bring the idea to market. The platform would support both funding milestones and time-based funding release mechanisms. In addition, early contributors to a project would benefit from the projects future success, so community members have the incentive to find the best projects early and shift their support to those. The result is that quality projects should bubble up and receive the most funding and attention from the community."]}
   18 {:title "Influencer.io"
       :description [:div "As US digital ad spend reaches " [link "$82.86 billion" "https://www.emarketer.com/Article/US-Digital-Ad-Spending-Surpass-TV-this-Year/1014469"] " in 2017 and " [link "47% of online consumers" "https://digiday.com/uk/latest-ad-blocking-report-finds-half-disable-blockers-read-content/"] " use ad blockers, advertisers have been turning to influencer marketing in order to successfully reach their target audience. Influencer marketing can be defined as paying an influencer to advertise your product throughout their social media platforms. Through the Influencer District, advertisers will instantly discover their product’s ideal influencer in order to reach their campaign’s target audience. All of this with no platform fee."]}
   1 {:title "Bounty.io"
      :description "For the first time in blockchain technology's short history, blockchain entrepreneurs are now raising more money through initial coin offerings (ICOs) than traditional venture capital investments. So far in 2017, blockchain entrepreneurs have raised over $600m through ICO offerings, a figure that now exceeds the $295m raised through VC funding. This will only continue to grow in 2018 and beyond.  ICO's are accordingly relying even more on bounty programs in conjunction with upcoming ICO's. The Bounty.io district will be a one stop shop for startups needing ICO bounties completed. Bounty.io users will be able to search through available bounties, sort by project, category, and apply various filters to locate bounties and projects they are interested in participating in, thereafter users can accept bounty assignments, after selecting bounties they wish to complete. Upon completion users will be rewarded in ICO coins. Teams will be able to easily and efficiently disburse payments to contributors who have fulfilled the requirements of any bounties. A verification process will be built in to verify that each job was successfully completed"}
   10 {:title "etherAid"
       :description "Share the wealth. The etherAid network will connect local charities directly with donors across the world. The network will provide immediate funding to those most in need, all sourced from the thriving crypto economy. Charities are held to high ethical and fiscal standards using the etherAid Reputation System (eRS). Donor funds are managed by a smart contract system that gradually releases funds based on the charity’s reputation."}
   17 {:title "Localview"
       :description "Apps and websites like Yelp are good resources to allow residents to access reviews from others in their communities on places and activities in their local city. But the centralized nature leads to flaws, with advertisements competing for space and a focus on quantity of reviews over quality. For reviewers, there is limited pay off for putting together higher quality reviews. Some may even try to branch out to their own blogs/sites but then run into other issues getting value out of their reviews. For those looking to consume reviews, they end up with long lists of low quality reviews or struggle trying to find those community experts. This is where Localview comes into the picture."}
   29 {:title "Decentraland District"
       :description [:div [link "Decentraland" "https://decentraland.org/"] " is an open source initiative to build a traversable virtual reality world. Within Decentraland, habitants can claim land as their own and build anything they wish upon it, from 3D artwork to fully immersive applications such as a VR poker room, for other inhabitants of the world to discover and enjoy. The nature of adjacency of land will lead to particular parcels becoming more desirable than others as a byproduct of their surroundings. A parcel which hosts a heavily trafficked installation will naturally enhance the discoverability of content hosted by neighboring landowners, leading to an appreciation in the inherent value of bordering plots. The Decentraland District will establish a secondary market for land ownership and rentals within the Decentraland virtual world, establishing a focal point for the exchange of parcels and providing a means of price discovery and liquidity for LAND owners."]}})

(def bittrex-fee-candidates
  {1 {:title "Support"}
   2 {:title "Disapprove"}})

(def current-subdomain (let [subdomain (-> js/location.href
                                         url/url
                                         :host
                                         (clojure.string/split ".")
                                         first)]
                         (if (= subdomain "localhost") "vote" subdomain)))
(def routes
  ({"vote" ["/" [["proposals" :route.vote/proposals]
                 [true :route.vote/home]]]
    "feedback" ["/" [[true :route.feedback/home]]]}
    current-subdomain))
