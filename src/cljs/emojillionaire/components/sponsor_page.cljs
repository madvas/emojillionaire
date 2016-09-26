(ns emojillionaire.components.sponsor-page
  (:require
    [cljs-react-material-ui.icons :as icons]
    [cljs-react-material-ui.reagent :as ui]
    [emojillionaire.components.address-select-field :refer [address-select-field]]
    [emojillionaire.components.emoji :refer [emoji]]
    [emojillionaire.components.layout :refer [grid row col outer-paper headline]]
    [emojillionaire.utils :as u]
    [re-frame.core :refer [subscribe dispatch]]
    [web3-cljs.core :as web3-cljs]
    ))

(defn sponsor-page []
  (let [new-sponsor (subscribe [:db/new-sponsor])
        contract-config (subscribe [:contract/config])
        my-addresses (subscribe [:db/my-addresses])
        selected-account (subscribe [:new-sponsor/account])
        etherscan-url (subscribe [:new-sponsor/etherscan-url])]
    (fn []
      (let [{:keys [name amount address sending?]} @new-sponsor
            {:keys [sponsor-name-max-length sponsorship-fee-ratio sponsorship-min-amount]} @contract-config
            sponsor-fee-percent (divide sponsorship-fee-ratio 100)
            min-amount (web3-cljs/from-wei sponsorship-min-amount :ether)]
        [outer-paper
         [headline "Become a Sponsor" :medal]
         [:br]
         [:h3 "Money you send as a sponsor will be added to a current jackpot. After receiving money,
          your name or your company's name will be automatically displayed at the home page."]
         [ui/text-field {:default-value name
                         :on-change #(dispatch [:new-sponsor/update :name (u/evt-val %)])
                         :name "Name"
                         :max-length sponsor-name-max-length
                         :floating-label-text "Your Name"
                         :floating-label-fixed true}]
         [:br]
         [ui/text-field {:default-value amount
                         :on-change #(dispatch [:new-sponsor/update :amount (js/parseFloat (u/evt-val %))])
                         :name "Amount"
                         :floating-label-text "Amount (in Ether)"
                         :floating-label-fixed true
                         :min 1}]
         [:br]
         [address-select-field @my-addresses address [:new-sponsor/update :address]]
         [:h3 "Wallet Balance: " [:b (u/eth (:balance @selected-account))]]
         [:br]
         [:h4 "Money sent multiple times from the same address are summed up."]
         [:h4 [:b (- 100 sponsor-fee-percent) "%"] " of given amount is added to a jackpot, "
          [:b sponsor-fee-percent "%"] " is sponsoring fee."]
         [ui/raised-button
          {:secondary true
           :disabled (or (js/isNaN amount)
                         (< amount min-amount)
                         (not address)
                         sending?)
           :label-position "before"
           :label "Pay"
           :style {:margin-top 15}
           :icon (icons/navigation-check)
           :on-touch-tap #(dispatch [:new-sponsor/sponsor
                                     (:address @selected-account)
                                     amount
                                     name])}]
         (when sending?
           [:h4 {:style {:margin-top 10}}
            "Your sponsoring is being processed. " [u/etherscan-link @etherscan-url]])]))))
