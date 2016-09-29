(ns emojillionaire.subs
  (:require
    [emojillionaire.utils :as u]
    [medley.core :as medley]
    [re-frame.core :refer [reg-sub subscribe]]
    ))

(reg-sub
  :db/network
  (fn [db _]
    (:network db)))

(reg-sub
  :db/drawer-open?
  (fn [db _]
    (:drawer-open? db)))

(reg-sub
  :db/snackbar
  (fn [db _]
    (:snackbar db)))

(reg-sub
  :db/current-page
  (fn [db _]
    (:current-page db)))

(reg-sub
  :db/route-params
  (fn [db _]
    (get-in db [:current-page :route-params])))

(reg-sub
  :db/my-addresses
  (fn [db _]
    (:my-addresses db)))

(reg-sub
  :db/accounts
  (fn [db _]
    (:accounts db)))

(reg-sub
  :db/bets
  (fn [db]
    (:bets db)))

(reg-sub
  :db/jackpot
  (fn [db _]
    (:jackpot db)))

(reg-sub
  :db/new-sponsor
  (fn [db _]
    (:new-sponsor db)))

(reg-sub
  :db/withdraw
  (fn [db _]
    (:withdraw db)))

(reg-sub
  :db/sponsorships
  (fn [db _]
    (:sponsorships db)))

(reg-sub
  :contract/config
  (fn [db _]
    (get-in db [:contract :config])))

(reg-sub
  :db/selectable-emojis
  (fn [db _]
    (:selectable-emojis db)))

(reg-sub
  :db/has-web3?
  (fn [db _]
    (boolean (:web3 db))))

(reg-sub
  :db/selectable-emojis-indexed
  (fn [db _]
    (rest (medley/indexed (:selectable-emojis db)))))

(reg-sub
  :db/selected-emoji
  (fn [db _]
    (get-in db [:emoji-select-form :selected-emoji])))

(reg-sub
  :new-bet/rolling?
  (fn [db _]
    (get-in db [:new-bet :rolling?])))

(reg-sub
  :db/winning-guess
  (fn [db _]
    (:winning-guess db)))

(reg-sub
  :db/conversion-rates
  (fn [db _]
    (:conversion-rates db)))

(reg-sub
  :db/winnings
  (fn [db _]
    (->> (vals (:winnings db))
      (u/sort-by-desc :date))))

(reg-sub
  :sponsors/top-sponsors
  (fn [db _]
    (->> (:top-sponsors-addresses db)
      (map #(get-in db [:sponsors %]))
      (remove nil?)
      (u/sort-big-numbers-desc :amount))))

(reg-sub
  :sponsors/current-jackpot-sponsorships
  (fn [db _]
    (let [jackpot-key (get-in db [:jackpot :jackpot-key])]
      (->> (vals (:sponsorships db))
        (filter #(= (:jackpot-key %) jackpot-key))
        (u/sort-big-numbers-desc :amount)))))

(reg-sub
  :sponsors/current-jackpot-sponsorships-total-amount
  :<- [:sponsors/current-jackpot-sponsorships]
  (fn [sponsorships]
    (reduce #(.plus (:amount %2) %1) 0 sponsorships)))

;; Contract

(reg-sub
  :db/contract
  (fn [db]
    (:contract db)))

(reg-sub
  :contract/etherscan-url
  :<- [:db/contract]
  :<- [:db/network]
  (fn [[{:keys [address]} network]]
    (u/etherscan-address-url network address)))

(reg-sub
  :contract/stats
  (fn [db]
    (get-in db [:contract :stats])))

(reg-sub
  :contract/active-state?
  (fn [db]
    (= :active (get-in db [:contract :state]))))

;; ------------ New Bet ------------

(reg-sub
  :db/new-bet
  (fn [db _]
    (:new-bet db)))

(reg-sub
  :new-bet/account
  (fn [db _]
    (get-in db [:accounts (:address (:new-bet db))])))

(reg-sub
  :new-bet/estimated-bet-cost
  :<- [:contract/config]
  :<- [:db/new-bet]
  (fn [[contract-config new-bet] _]
    (u/estimated-bet-cost contract-config (count (:guesses new-bet)))))

(reg-sub
  :new-bet/wallet-charge
  :<- [:new-bet/estimated-bet-cost]
  :<- [:new-bet/account]
  (fn [[estimated-bet-cost selected-account] _]
    (let [wallet-charge (.minus estimated-bet-cost
                                (or (:credit selected-account) 0))]
      (if (.isNegative wallet-charge)
        (js/BigNumber. 0)
        wallet-charge))))

(reg-sub
  :new-bet/etherscan-url
  :<- [:db/network]
  :<- [:db/new-bet]
  (fn [[network {:keys [transaction-hash]}] _]
    (u/etherscan-tx-url network transaction-hash)))

;; ------------ Player Profile ------------

(reg-sub
  :player-profile/address
  :<- [:db/route-params]
  (fn [{:keys [address]}]
    address))

(reg-sub
  :player-profile/etherscan-url
  :<- [:player-profile/address]
  :<- [:db/network]
  (fn [[address network]]
    (u/etherscan-address-url network address)))

(reg-sub
  :player-profile/me?
  :<- [:db/my-addresses]
  :<- [:player-profile/address]
  (fn [[addresses address] _]
    (some #(= address %) addresses)))

(reg-sub
  :player-profile/winnings
  :<- [:db/winnings]
  :<- [:player-profile/address]
  (fn [[winnings address]]
    (filter #(= address (:address %)) winnings)))

(reg-sub
  :player-profile/bets
  :<- [:db/bets]
  :<- [:player-profile/address]
  (fn [[bets address]]
      (->> (vals bets)
        (filter #(= address (:address %)))
        (u/sort-by-desc :date))))

(reg-sub
  :player-profile/total-guesses
  :<- [:player-profile/bets]
  (fn [player-bets]
    (reduce #(+ %1 (count (:guesses %2))) 0 player-bets)))

(reg-sub
  :player-profile/sponsorships
  :<- [:db/sponsorships]
  :<- [:player-profile/address]
  (fn [[sponsorships address]]
    (->> (vals sponsorships)
      (filter #(= (:address %) address))
      (u/sort-by-desc :date))))

(reg-sub
  :player-profile/sponsorships-total-amount
  :<- [:player-profile/sponsorships]
  (fn [sponsorships]
    (reduce #(.plus (:amount %2) %1) 0 sponsorships)))

(reg-sub
  :player-profile/account
  :<- [:db/accounts]
  :<- [:player-profile/address]
  (fn [[accounts address]]
    (get accounts address)))


(reg-sub
  :new-sponsor/account
  :<- [:db/accounts]
  :<- [:db/new-sponsor]
  (fn [[accounts new-sponsor] _]
    (get accounts (:address new-sponsor))))

(reg-sub
  :new-sponsor/etherscan-url
  :<- [:db/network]
  :<- [:db/new-sponsor]
  (fn [[network {:keys [transaction-hash]}] _]
    (u/etherscan-tx-url network transaction-hash)))

(reg-sub
  :withdraw/etherscan-url
  :<- [:db/network]
  :<- [:db/withdraw]
  (fn [[network {:keys [transaction-hash]}]]
    (u/etherscan-tx-url network transaction-hash)))
