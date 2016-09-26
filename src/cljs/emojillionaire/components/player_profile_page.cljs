(ns emojillionaire.components.player-profile-page
  (:require
    [cljs-react-material-ui.icons :as icons]
    [cljs-react-material-ui.reagent :as ui]
    [emojillionaire.components.emoji :refer [emoji]]
    [emojillionaire.components.layout :refer [grid row col outer-paper headline]]
    [emojillionaire.components.winnings-table :refer [winnings-table]]
    [emojillionaire.styles :as st]
    [emojillionaire.utils :as u]
    [medley.core :as medley]
    [re-frame.core :refer [subscribe dispatch]]
    ))

(defn- text-with-emoji [text emoji-key]
  [row {:middle "xs" :center "xs"}
   text
   [emoji emoji-key 30 10]])

(defn- previous-winnings []
  (let [winnings (subscribe [:player-profile/winnings])]
    (fn [my-profile-page?]
      [outer-paper
       [headline "Winnings" :trophy]
       [winnings-table @winnings
        {:no-items-text (if my-profile-page?
                          [text-with-emoji
                           "You haven't won jackpot yet. Keep trying!" :lifter]
                          [text-with-emoji
                           "This player has no winnings yet. Hopefully soon!" :wink])
         :no-address-col? true}]])))

(defn- player-profile-sponsorships []
  (let [sponsorships (subscribe [:player-profile/sponsorships])
        sponsorships-total-amount (subscribe [:player-profile/sponsorships-total-amount])]
    (fn [my-profile-page?]
      [outer-paper
       [headline "Sponsorings" :military-medal]
       [ui/table {:selectable false :height "500px"}
        [ui/table-header {:adjust-for-checkbox false :display-select-all false}
         [ui/table-row
          [ui/table-header-column "Name"]
          [ui/table-header-column "Amount"]
          [ui/table-header-column "Date"]]]
        [ui/table-body {:display-row-checkbox false}
         (if (seq @sponsorships)
           (doall
             (for [{:keys [name amount date sponsorship-key]} @sponsorships]
               [ui/table-row {:key sponsorship-key :selectable false}
                [ui/table-row-column name]
                [ui/table-row-column (u/eth amount)]
                [ui/table-row-column (u/format-date date)]]))
           [ui/table-row
            [ui/table-row-column
             {:col-span 4 :style st/text-center}
             (if my-profile-page?
               [text-with-emoji "You haven't sponsored yet. Nevermind, we still love you!" :revolving-hearts]
               [text-with-emoji "This player haven't sponsored any amount. One day maybe..." :sun-with-face])]])]
        (when (< 1 (count @sponsorships))
          [ui/table-footer
           [ui/table-row
            [ui/table-row-column
             {:col-span 3 :style st/new-bet-summary}
             [:h2 "Total: " (u/eth @sponsorships-total-amount)]]]])]])))

(defn- previous-bets []
  (let [bets (subscribe [:player-profile/bets])]
    (fn [my-profile-page?]
      [outer-paper
       [headline "Bets" :four-leaf-clover]
       [ui/table {:selectable false}
        [ui/table-header {:adjust-for-checkbox false :display-select-all false}
         [ui/table-row
          [ui/table-header-column "Guesses"]
          [ui/table-header-column "Rolls"]
          [ui/table-header-column "Date"]]]
        [ui/table-body {:display-row-checkbox false}
         (if (seq @bets)
           (doall
             (for [{:keys [guesses rolls date bet-key]} @bets]
               [ui/table-row {:key bet-key :selectable false}
                [ui/table-row-column {:style st/multi-emoji-row-col}
                 (for [[index guess] (medley/indexed guesses)]
                   ^{:key index} [emoji {:type "png"} guess (:width st/table-emoji) 2])]
                [ui/table-row-column {:style st/multi-emoji-row-col}
                 (for [[index roll] (medley/indexed rolls)]
                   ^{:key index} [emoji {:type "png"} roll (:width st/table-emoji) 2])]
                [ui/table-row-column (u/format-date date)]]))
           [ui/table-row
            [ui/table-row-column
             {:col-span 4 :style st/text-center}
             (if my-profile-page?
               [text-with-emoji
                "You haven't place any bet yet. Luck is for those who try!" :thinking]
               [text-with-emoji
                "This player haven't placed any bet yet." :confused])]])]]])))

(defn- account-info []
  (let [account (subscribe [:player-profile/account])
        etherscan-url (subscribe [:player-profile/etherscan-url])
        withdraw-etherscan-url (subscribe [:withdraw/etherscan-url])
        withdraw (subscribe [:db/withdraw])]
    (fn [my-profile-page?]
      (let [{:keys [address balance credit]} @account
            {:keys [sending?]} @withdraw]
        [outer-paper
         [:h1 {:title address
               :style st/ellipsis} address] [:br]
         [:div {:style {:margin-bottom 5}}
          [u/etherscan-link @etherscan-url]]
         [:h2 {:style {:margin-bottom 10}} "Wallet Balance: " (u/eth balance)]
         [:h2 "Credit: " (u/eth credit)]
         (when my-profile-page?
           [:div
            [ui/raised-button
             {:secondary true
              :disabled (or (and credit (.lessThanOrEqualTo credit 0))
                            sending?)
              :label-position "before"
              :label "Cash Out"
              :style {:margin-top 15}
              :icon (icons/editor-attach-money)
              :on-touch-tap #(dispatch [:player-profile/withdraw])}]
            (when sending?
              [:div {:style {:margin-top 10}}
               [:h4 "Money are on the way to you! " [u/etherscan-link @withdraw-etherscan-url]]])])]))))

(def player-profile-page
  (with-meta
    (fn []
      (let [my-profile-page? (subscribe [:player-profile/me?])]
        (fn []
          (let [my-profile-page? @my-profile-page?]
            [:div
             [account-info my-profile-page?]
             [previous-winnings my-profile-page?]
             [previous-bets my-profile-page?]
             [player-profile-sponsorships my-profile-page?]]))))
    {:component-will-mount
     (fn []
       (dispatch [:player-profile/initiate-load]))
     :component-will-unmount
     (fn []
       (dispatch [:player-profile/stop-watching]))}))
