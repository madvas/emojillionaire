(ns emojillionaire.components.home-page
  (:require
    [cljs-react-material-ui.icons :as icons]
    [cljs-react-material-ui.reagent :as ui]
    [emojillionaire.components.address-select-field :refer [address-select-field]]
    [emojillionaire.components.emoji :refer [emoji]]
    [emojillionaire.components.layout :refer [grid row col outer-paper headline]]
    [emojillionaire.components.winnings-table :refer [winnings-table]]
    [emojillionaire.styles :as st]
    [emojillionaire.utils :as u]
    [medley.core :as medley]
    [re-frame.core :refer [subscribe dispatch]]
    [reagent.core :as r]))

(defn- selected-emoji-preview []
  (let [selected-emoji (subscribe [:db/selected-emoji])
        new-bet (subscribe [:db/new-bet])
        contract-config (subscribe [:contract/config])]
    (fn []
      (let [{:keys [rolling? guesses]} @new-bet
            {:keys [index emoji-key]} @selected-emoji]
        [col {:xs 12 :sm 12 :md 4 :lg 4}
         [row {:top "xs" :center "xs"}
          (when emoji-key
            [emoji {:style st/selected-emoji-preview} emoji-key 150 0])]
         [row {:bottom "xs" :center "xs"}
          [ui/raised-button
           {:primary true
            :disabled (or (>= (count guesses) (:max-guesses-at-once @contract-config))
                          (not index)
                          rolling?)
            :label-position "before"
            :label "Add"
            :style st/add-selected-emoji-btn
            :icon (icons/content-add)
            :on-touch-tap #(dispatch [:new-bet/add-guess index])}]]]))))

(defn- emoji-select-form []
  (let [selectable-emojis (subscribe [:db/selectable-emojis])]
    (fn []
      [row {:bottom "xs"}
       [col {:xs 12 :sm 12 :md 8 :lg 8
             :style st/emoji-select-form-wrap}
        (for [[index emoji-key] (medley/indexed @selectable-emojis)]
          [emoji
           {:key emoji-key
            :class "selectable-emoji"
            :style st/selectable-emoji
            :on-click #(dispatch [:select-emoji index emoji-key])}
           emoji-key 33 5])]
       [selected-emoji-preview]])))

(defn- selectable-emoji [emoji-index emojis]
  (let [emoji-key (nth emojis emoji-index)]
    [emoji
     {:style st/clickable
      :on-click #(dispatch [:select-emoji emoji-index emoji-key])}
     emoji-key (:width st/table-emoji) 0]))

(defn- can-bet? [selected-account {:keys [rolling? guesses]}]
  (and selected-account
       (seq guesses)
       (not rolling?)))

(def rolling-emoji-placeholder
  (r/create-class
    {:get-initial-state
     (fn [this]
       (let [emojis-count (count (second (reagent.core/argv this)))]
         {:interval (js/setInterval #(r/set-state this {:emoji-index (rand-int emojis-count)}) 250)
          :emoji-index (rand-int emojis-count)}))
     :component-will-unmount
     (fn [this]
       (js/clearInterval (:interval (r/state this))))
     :render
     (fn [this]
       (let [emojis (second (reagent.core/argv this))]
         [selectable-emoji (:emoji-index (r/state this)) emojis]))}))

(defn- new-bet-table []
  (let [new-bet (subscribe [:db/new-bet])
        contract-config (subscribe [:contract/config])
        estimated-bet-cost (subscribe [:new-bet/estimated-bet-cost])
        selectable-emojis (subscribe [:db/selectable-emojis])]
    (fn []
      (let [{:keys [guesses rolls rolling?]} @new-bet]
        [row
         [ui/table {:selectable false :fixed-footer false :fixed-header false}
          [ui/table-header {:adjust-for-checkbox false :display-select-all false}
           [ui/table-row
            [ui/table-header-column {:style st/table-narrow-col} "No."]
            [ui/table-header-column "Cost"]
            [ui/table-header-column "Guess"]
            [ui/table-header-column "Roll"]
            [ui/table-header-column {:style st/table-narrow-col}
             [ui/icon-button
              {:tooltip "Clear All"
               :icon-style st/grey-text
               :disabled rolling?
               :on-touch-tap #(dispatch [:new-bet/remove-all-guesses])}
              (icons/action-delete)]]]]
          [ui/table-body {:display-row-checkbox false}
           (if (seq guesses)
             (doall
               (for [[index guess] (medley/indexed guesses)]
                 [ui/table-row {:key index :selectable false}
                  [ui/table-row-column {:style st/table-narrow-col}
                   (inc index)]
                  [ui/table-row-column (u/eth (:guess-cost @contract-config))]
                  [ui/table-row-column
                   [selectable-emoji guess @selectable-emojis]]
                  [ui/table-row-column
                   (let [roll (nth rolls index)]
                     (cond
                       roll [selectable-emoji roll @selectable-emojis]
                       rolling? [rolling-emoji-placeholder @selectable-emojis]
                       :else (icons/action-help-outline)))]
                  [ui/table-row-column
                   {:style st/table-narrow-col}
                   [ui/icon-button
                    {:disable-touch-ripple true
                     :disabled rolling?
                     :on-touch-tap #(dispatch [:new-bet/remove-guess-from-bet index])}
                    (icons/navigation-close)]]]))
             [ui/table-row
              [ui/table-row-column
               {:col-span 5 :style st/text-center}
               "You didn't choose any emoji. Choose one by clicking ADD above!"]])]
          [ui/table-footer
           [ui/table-row
            [ui/table-row-column
             {:col-span 4 :style st/new-bet-summary}
             "Estimated price: " [:b (u/eth @estimated-bet-cost)] [:br]
             "Price includes bet cost, bet fee and oracle fee. Change will be returned to you as credit"]]]]]))))

(defn- bet-submission []
  (let [my-addresses (subscribe [:db/my-addresses])
        selected-account (subscribe [:new-bet/account])
        new-bet (subscribe [:db/new-bet])
        wallet-charge (subscribe [:new-bet/wallet-charge])
        etherscan-url (subscribe [:new-bet/etherscan-url])]
    (fn []
      [row
       [col {:xs 12 :style {:margin-top 30}}
        [row {:middle "xs" :end "xs"}
         [col {:xs 12}
          [address-select-field @my-addresses
           (:address @selected-account)
           [:new-bet/select-address]
           {:disabled (or (empty? @my-addresses) (:rolling? @new-bet))}]]]
        [row {:end "xs"}
         [col {:xs 12 :style {:margin-top 20}}
          [:h3 "Credit: " [:b (u/eth (:credit @selected-account))]]
          [:h3 "Wallet Balance: " [:b (u/eth (:balance @selected-account))]]
          [:h3 "Wallet Will Be Charged: " [:b (u/eth @wallet-charge)]]]]
        [row {:end "xs"}
         [col {:xs 12 :sm 8 :md 5 :lg 5 :style st/bet-btns-wrap}
          (when (empty? @my-addresses)
            [:span
             [ui/raised-button
              {:secondary true
               :label-position :before
               :label "How do I connect ETH wallet?"
               :style (merge st/full-width {:margin-bottom 10})
               :href "#"
               :icon (icons/action-help-outline)}]
             [:br]])
          [ui/raised-button
           {:primary true
            :disabled (not (can-bet? @selected-account @new-bet))
            :label-position "before"
            :label "Pay & roll!"
            :style st/full-width
            :icon (icons/navigation-check)
            :on-touch-tap #(dispatch [:new-bet/bet
                                      (:guesses @new-bet)
                                      (:address @selected-account)
                                      @wallet-charge])}]]
         (when (:rolling? @new-bet)
           [col {:xs 12 :style {:margin-top 10}}
            [:h4 (if (:bet-processed? @new-bet)
                   "Your bet was successfully processed, now we wait to get winning emojis."
                   "Your bet is being processed now, this may take even several minutes.") [:br]
             "Meanwhile you can freely browse this website, we will notify you when it's ready"
             [row {:middle "xs" :end "xs"}
              "No need to refresh page, it's not stuck, don't worry. " [emoji :wink 30 5]]]
            [u/etherscan-link @etherscan-url]])]]])))


(defn- congrats-title []
  (let [emj #(vec [emoji % 45 10])]
    [row {:style st/congrats-title}
     [col {:xs 12}
      [:span (emj :money-mouth) "Congratulations" (emj :trophy) [:br]
       (emj :100) "You Won a Jackpot!" (emj :money-with-wings)]]]))

(defn- jackpot-won-dialog []
  (let [winning-guess (subscribe [:db/winning-guess])
        contract-config (subscribe [:contract/config])]
    (fn []
      (let [{:keys [amount roll address]} @winning-guess]
        [ui/dialog
         {:title (r/as-element [congrats-title])
          :open (boolean @winning-guess)
          :on-request-close #(dispatch [:winning-guess/remove])
          :actions [(r/as-element
                      [ui/flat-button
                       {:label "Go to my account page"
                        :label-position :before
                        :secondary true
                        :href (and address (u/path-for :player-profile :address address))
                        :on-touch-tap #(dispatch [:winning-guess/remove])
                        :icon (icons/action-account-balance-wallet)}])
                    (r/as-element
                      [ui/flat-button
                       {:label "Thank You!"
                        :primary true
                        :label-position :before
                        :icon (icons/action-thumb-up)
                        :on-touch-tap #(dispatch [:winning-guess/remove])
                        :keyboard-focused true}])]}
         [row {:middle "xs" :center "xs"}
          [col {:xs 12
                :style st/winning-amount}
           [:h1 (u/eth amount)]]
          [col {:xs 12 :style {:margin-bottom 20}}
           [emoji roll 120 0]]
          [col {:xs 12}
           [:div "Today is your lucky day! " (u/eth amount) " was added as your credit." [:br]
            "You can send those money to your wallet at your account's page." [:br]
            "Note, we reduced your winning by " (u/eth (:guess-cost @contract-config))
            ", so next jackpot is not completely empty."]]]]))))

(defn- previous-winnings []
  (let [winnings (subscribe [:db/winnings])]
    (fn []
      [outer-paper
       [headline "Previous Winners" :trophy]
       [winnings-table @winnings
        {:no-items-text "Nobody has won yet! Be first!"}]])))

(defn- top-sponsors-component []
  (let [sponsors (subscribe [:sponsors/top-sponsors])]
    (fn []
      [outer-paper
       [headline "Top Sponsors" :medal]
       [ui/table {:selectable false :height "300px"}
        [ui/table-header {:adjust-for-checkbox false :display-select-all false}
         [ui/table-row
          [ui/table-header-column {:style st/table-narrow-col} "No."]
          [ui/table-header-column "Address"]
          [ui/table-header-column "Name"]
          [ui/table-header-column "Amount"]]]
        [ui/table-body {:display-row-checkbox false}
         (if (seq @sponsors)
           (doall
             (for [[index {:keys [address name amount]}] (medley/indexed @sponsors)]
               [ui/table-row {:key address :selectable false}
                [ui/table-row-column {:style st/table-narrow-col}
                 (inc index) "."]
                [ui/table-row-column
                 [:a {:href (u/path-for :player-profile :address address)}
                  (u/truncate address 25)]]
                [ui/table-row-column name]
                [ui/table-row-column (u/eth amount)]]))
           [ui/table-row
            [ui/table-row-column
             {:col-span 4 :style st/text-center}
             "We have no sponsors yet. This is big opportunity for you!"]])]]])))

(defn- sponsorships-component []
  (let [sponsorships (subscribe [:sponsors/current-jackpot-sponsorships])
        sponsorships-total-amount (subscribe [:sponsors/current-jackpot-sponsorships-total-amount])]
    (fn []
      [outer-paper
       [headline "This Jackpot's Sponsors" :military-medal]
       [:div [:a {:href (u/path-for :sponsor)} "I want to become a sponsor"]]
       [ui/table {:selectable false :height "300px"}
        [ui/table-header {:adjust-for-checkbox false :display-select-all false}
         [ui/table-row
          [ui/table-header-column {:style st/table-narrow-col} "No."]
          [ui/table-header-column "Address"]
          [ui/table-header-column "Name"]
          [ui/table-header-column "Amount"]
          [ui/table-header-column "Date"]]]
        [ui/table-body {:display-row-checkbox false}
         (if (seq @sponsorships)
           (doall
             (for [[index {:keys [address name amount date sponsorship-key]}] (medley/indexed @sponsorships)]
               [ui/table-row {:key sponsorship-key :selectable false}
                [ui/table-row-column {:style st/table-narrow-col}
                 (inc index) "."]
                [ui/table-row-column
                 [:a {:href (u/path-for :player-profile :address address)}
                  (u/truncate address 25)]]
                [ui/table-row-column name]
                [ui/table-row-column (u/eth amount)]
                [ui/table-row-column (u/format-date date)]]))
           [ui/table-row
            [ui/table-row-column
             {:col-span 4 :style st/text-center}
             "This jackpot has no sponsors yet. Your name can be here!"]])]
        [ui/table-footer
         [ui/table-row
          [ui/table-row-column
           {:col-span 5 :style st/new-bet-summary}
           [:h2 "Total: " (u/eth @sponsorships-total-amount)]]]]]])))

(defn- stats-component []
  (let [stats (subscribe [:contract/stats])]
    (fn []
      (let [{:keys [total-jackpot-amounts-won jackpots-count bets-count total-guesses-count players-count
                    sponsors-count sponsorships-count total-sponsorships-amount]}
            @stats]
        [outer-paper
         [headline "Statistics" :1234] [:br]
         [:h3 "Total won in jackpots: " [:b (u/eth total-jackpot-amounts-won)]]
         [:h3 "Number of won jackpots: " [:b (dec jackpots-count)]]
         [:h3 "Total number of emoji guesses: " [:b total-guesses-count]]
         [:h3 "Unique players: " [:b players-count]]
         [:h3 "Unique sponsors: " [:b sponsors-count]]
         [:h3 "Total received from sponsors: " [:b (u/eth total-sponsorships-amount)]]
         [:br]]))))

(defn- bet-form []
  [outer-paper
   [:h1 {:style {:margin-bottom 20}} "Let's Play!"]
   [emoji-select-form]
   [ui/divider {:style (st/vertical-margin 20)}]
   [new-bet-table]
   [bet-submission]
   [jackpot-won-dialog]])

(defn home-page []
  (let [jackpot (subscribe [:db/jackpot])
        contract-config (subscribe [:contract/config])
        conversion-rates (subscribe [:db/conversion-rates])]
    (fn []
      (let [{:keys [guess-cost total-possibilities max-guesses-at-once
                    guess-fee-ratio]} @contract-config
            jackpot-amount (:amount @jackpot)]
        [:div
         [outer-paper
          [row {:middle "xs" :center "xs"}
           [emoji :moneybag 50 10]
           [:h1 {:style st/jackpot-text}
            "Jackpot: " (u/eth jackpot-amount)]
           [emoji :moneybag 50 10]]
          (when (and (:usd @conversion-rates) jackpot-amount)
            [row {:middle "xs" :center "xs"}
             [:h3 {:style {:font-weight 400}}
              "(" (u/usd jackpot-amount (:usd @conversion-rates)) ")"]])
          [:br]
          [:div {:style st/text-center}
           [:h1 "Welcome to emojillionaire!"] [:br]
           [:h3 "Bet fee is " [:b (str "only " (divide guess-fee-ratio 100) "%")]]
           [:h3 "Bet for " [:b "1"] " emoji is " [:b (u/eth guess-cost)]]
           [:h3 "You're choosing " [:b "1"] " of " [:b total-possibilities] " emojis"]
           [:h3 "Roll for " [:b "each"] " emoji is " [:b "independent"]]
           [:h3 "You can place up to " [:b max-guesses-at-once] " bets at once"]
           [:br]
           [:h2
            [row {:middle "xs" :center "xs"}
             [emoji :shamrock 30 10]
             [:b "Good Luck!"]
             [emoji :four-leaf-clover 30 10]]]]]
         [sponsorships-component]
         [top-sponsors-component]
         [stats-component]
         [bet-form]
         [previous-winnings]]))))