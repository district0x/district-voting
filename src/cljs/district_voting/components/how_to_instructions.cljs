(ns district-voting.components.how-to-instructions)

(defn how-to-instructions []
  [:ul
   [:li "Enable the MetaMask or Parity extension in your browser"]
   [:li "Fund your MetaMask or Parity wallet with district0x Network Tokens"]
   [:li "Click 'Vote' under the district you would like to signal for"]
   [:li "Confirm the transaction via MetaMask or Parity extension"]
   [:li "If you want to vote from MyEtherWallet, see tutorial "
    [:a {:href (str "https://github.com/district0x/district-proposals")
         :target :_blank}
     "here"]]])
