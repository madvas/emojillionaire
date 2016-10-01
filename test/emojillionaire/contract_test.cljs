(ns ^:figwheel-no-load emojillionaire.contract-test
  (:require [cljs.core.async :refer [<! >! chan]]
            [cljs.test :refer-macros [deftest is testing run-tests use-fixtures async]]
            [cljsjs.web3]
            [goog.string :as gstring]
            [goog.string.format]
            [emojillionaire.utils :as u]
            [cljs-web3.core :as web3]
            [cljs-web3.eth :as web3-eth]
            [print.foo :include-macros true]
            )
  (:require-macros [cljs.core.async.macros :refer [go]]))

(comment
  (run-tests 'emojillionaire.contract-test))

(def w3 (or (aget js/window "web3")
            (web3/create-web3 "http://localhost:8545/")))

(def gas-limit #_4700000 4712388)

(defn- assert-no-error [err]
  (when err
    (.error js/console err))
  (assert (not err)))

(defn- assert-not-out-of-gas [{:keys [gas-used]}]
  (let [gas-used-percent (* (/ gas-used gas-limit) 100)]
    (println (gstring/format "%.2f%" gas-used-percent) " gas used: " gas-used)
    (assert (not= gas-used gas-limit))))

(defn- assert-out-of-gas [{:keys [gas-used]}]
  (assert (= gas-used gas-limit)))

(defn- response>! [ch]
  (fn [err res]
    (assert-no-error err)
    (is res)
    (go (>! ch res))))

(defn- args>! [ch]
  (fn [{:keys [args]}]
    (go (>! ch args))))

(def contract-name "Emojillionaire")
#_(def contract-name "Test")
(def ^:dynamic *contract* nil)

(defn- developer-account? [address]
  (= (first (web3-eth/accounts w3)) address))

(defn- developer-account [web3 & [clb]]
  (first (web3-eth/accounts web3 clb)))

(.clear js/console)

(use-fixtures
  :each
  {:before
   (fn []
     (async done
       (is w3)
       (let [account (developer-account w3)]
         (is (not-empty account))
         (u/fetch-contract!
           "/contracts/build/emojillionaire"
           "Emojillionaire"
           (fn [abi bin]
             (is (array? abi))
             (is (string? bin))
             (u/deploy-bin! w3 abi bin account gas-limit
                            (fn [res]
                              (.log js/console "Contract transaction: " res)
                              (is res))
                            (fn [Contract]
                              (is Contract)
                              (is (string? (aget Contract "address")))
                              (println "Contract address: " (aget Contract "address"))
                              (set! *contract* Contract)
                              (assert *contract*)
                              (done))
                            (fn [err]
                              (.error js/console err)
                              (assert (not err)))))))))
   :after
   (fn []
     (async done (js/setTimeout #(done) 0)))                ; Prevents from hanging between tests, don't ask me why
   })

(defn- contract-listen [event-name on-success]
  (web3-eth/contract-call *contract* event-name nil {:from-block 0}
                          (fn [err log]
                            (assert (not err))
                            (on-success log))))

(defn- contract-call [k & args]
  (apply web3-eth/contract-call *contract* k args))

(defn- calculate-bet-cost [bets-count]
  (u/calculate-bet-cost *contract* bets-count))

(defn- guesses-ok? [actual-bets expected-bets]
  (= (map #(.toNumber %) actual-bets) expected-bets))

(defn- rolls-ok? [rolls]
  (every? #(< 0 (.toNumber %) (contract-call :total-possibilities)) rolls))

(defn- calculate-guesses-cost [times]
  (.times (contract-call :guess-cost) times))

(defn- calculate-sponsor-fee [total-amount]
  (.div total-amount
        (.div (web3/to-big-number 10000)
              (contract-call :sponsorship-fee-ratio))))

(defn- amount-after-sponsor-fees [total-amount]
  (.minus total-amount (calculate-sponsor-fee total-amount)))

(defn- rand-address []
  (subs (web3/sha3 (str (random-uuid))) 0 42))

(defn- create-tx-receipt-filter [tx-receipt-ch tx-hash]
  (web3-eth/filter w3 "latest"
                   (fn [err]
                     (assert-no-error err)
                     (when-let [tx-receipt (web3-eth/get-transaction-receipt w3 tx-hash)]
                       (go (>! tx-receipt-ch tx-receipt))))))

(deftest deploy-contract
  (let [settings-ch (chan)
        top-sponsors-max-length-ch (chan)
        oraclize-gas-limit-ch (chan)]
    (contract-call :change-settings
                   (contract-call :guess-cost)
                   (contract-call :total-possibilities)
                   (contract-call :guess-fee-ratio)
                   25                                       ; max-guesses-at-once
                   30                                       ; sponsor-name-max-length
                   500                                      ; sponsorship-fee-ratio
                   (cljs-web3.core/to-wei 1 :ether)         ; sponsorship-min-amount
                   {:from (developer-account w3)
                    :gas gas-limit} (response>! settings-ch))

    (contract-call :set-top-sponsors-max-length
                   10
                   {:from (developer-account w3)
                    :gas gas-limit} (response>! top-sponsors-max-length-ch))

    (contract-call :set-oraclize-gas-limit
                   700000
                   {:from (developer-account w3)
                    :gas gas-limit} (response>! oraclize-gas-limit-ch))

    (async done
      (go
        (let [tx-receipt-ch (chan)
              tx-filter (create-tx-receipt-filter tx-receipt-ch (<! settings-ch))]
          (assert-not-out-of-gas (<! tx-receipt-ch))
          (web3-eth/stop-watching! tx-filter))


        (let [tx-receipt-ch (chan)
              tx-filter (create-tx-receipt-filter tx-receipt-ch (<! top-sponsors-max-length-ch))]
          (assert-not-out-of-gas (<! tx-receipt-ch))
          (web3-eth/stop-watching! tx-filter))

        (let [tx-receipt-ch (chan)
              tx-filter (create-tx-receipt-filter tx-receipt-ch (<! oraclize-gas-limit-ch))]
          (assert-not-out-of-gas (<! tx-receipt-ch))
          (web3-eth/stop-watching! tx-filter))
        (done)))))


(deftest betting
  (println "Betting")
  (is (.eq (contract-call :bets-count) 0))
  (is (.eq (contract-call :players-count) 0))
  (is (.eq (contract-call :state) 0))
  (is (.gt (contract-call :total-possibilities) 0))
  (is (.gt (contract-call :guess-cost) 0))
  (is (.gt (contract-call :guess-fee-ratio) 0))
  (is (.gt (contract-call :guess-fee) 0))
  (is (.gt (contract-call :max-guesses-at-once) 0))
  (is (.eq (contract-call :current-jackpot-amount) 0))
  (is (.eq (contract-call :jackpots-count) 1))
  (is (developer-account? (contract-call :developer)))
  (is (.gt (contract-call :sponsor-name-max-length) 0))
  (is (.gt (contract-call :sponsorship-fee-ratio) 0))
  (is (.gt (contract-call :sponsorship-min-amount) 0))
  (is (.gt (contract-call :top-sponsors-max-length) 0))
  (is (.eq (contract-call :total-jackpot-amounts-won) 0))
  (is (.eq (contract-call :total-guesses-count) 0))
  (is (.eq (contract-call :total-sponsorships-amount) 0))
  (is (= (contract-call :get-random-org-query 15)
         (gstring/format "https://www.random.org/integers/?num=15&min=1&col=1&base=10&format=plain&rnd=new&max=%s"
                         (.toNumber (contract-call :total-possibilities)))))

  (let [bet-tx-receipt-ch (chan)
        guessees1 [2 5]
        guesses1-count (count guessees1)
        bet-ch (chan)
        on-player-credit-change-ch (chan)
        on-jackpot-amount-change-ch (chan)
        on-rolled-ch (chan)
        on-new-player-ch (chan)
        on-new-jackpot-started-ch (chan)]

    (contract-listen :on-player-credit-change (args>! on-player-credit-change-ch))
    (contract-listen :on-jackpot-amount-change (args>! on-jackpot-amount-change-ch))
    (contract-listen :on-rolled (args>! on-rolled-ch))
    (contract-listen :on-new-player (args>! on-new-player-ch))
    (contract-listen :on-new-jackpot-started (args>! on-new-jackpot-started-ch))

    (contract-call :bet guessees1
                   {:gas gas-limit
                    :value (calculate-bet-cost guesses1-count)
                    :from (developer-account w3)}
                   (response>! bet-ch))

    (async done
      (go
        (let [bet-tx-receipt-filter (create-tx-receipt-filter bet-tx-receipt-ch (<! bet-ch))]
          (assert-not-out-of-gas (<! bet-tx-receipt-ch))

          (let [{:keys [player-address credit]} (<! on-player-credit-change-ch)]
            (is (developer-account? player-address))
            (is (= "0.005" (web3/from-wei (.toNumber credit) :ether))))

          (let [{:keys [amount jackpot-key]} (<! on-jackpot-amount-change-ch)]
            (is (.eq amount 0))
            (is (.eq jackpot-key 0)))

          (let [{:keys [amount jackpot-key]} (<! on-jackpot-amount-change-ch)]
            (is (.eq (calculate-guesses-cost guesses1-count) amount))
            (is (.eq jackpot-key 0)))

          (let [{:keys [guesses rolls player-address date bet-key]} (<! on-rolled-ch)]
            (is (guesses-ok? guesses guessees1))
            (is (rolls-ok? rolls))
            (is (developer-account? player-address))
            (is date)
            (is (.eq bet-key 0)))

          (let [{:keys [player-address date players-count]} (<! on-new-player-ch)]
            (is (developer-account? player-address))
            (is date)
            (is (.eq players-count 1)))

          (let [{:keys [date jackpot-key]} (<! on-new-jackpot-started-ch)]
            (is date)
            (is (.eq jackpot-key 0)))


          (is (.eq (contract-call :bets-count) 1))
          (is (.eq (contract-call :players-count) 1))
          (is (.eq (calculate-guesses-cost guesses1-count)
                   (contract-call :current-jackpot-amount)))

          (let [[address guesses rolls guesses-cost bet-fee oraclize-fee jackpot-key query-id date]
                (contract-call :get-bet 0)]
            (is (developer-account? address))
            (is (guesses-ok? guesses guessees1))
            (is (rolls-ok? rolls))
            (is (not (.isNegative oraclize-fee)))
            (is (.eq (calculate-guesses-cost guesses1-count) guesses-cost))
            (is (.eq bet-fee (.div (calculate-guesses-cost guesses1-count) 100)))
            (is (.eq jackpot-key 0))
            (is (string? query-id))
            (is date))

          (.stopWatching bet-tx-receipt-filter)
          (done))))))


(deftest winning-jackpot
  (println "Winning Jackpot")
  (let [winning-acc (second (web3-eth/accounts w3))
        winning-guesses [1 1 1 139 1 1 1 25]
        winning-bet-count (count winning-guesses)
        winning-bet-cost (calculate-bet-cost winning-bet-count)
        winner-balance-before (web3-eth/get-balance w3 winning-acc)
        winning-bet-ch (chan)
        withdraw-tx-receipt-ch (chan)
        withdraw-ch (chan)
        account2 (last (web3-eth/accounts w3))
        bet2 [1 2 3 4 5 6]
        bet2-ch (chan)
        bet2-count (count bet2)
        on-jackpot-won-ch (chan)
        developer-withdraw-ch (chan)
        developer-balance-before (web3-eth/get-balance w3 (developer-account w3))]

    (contract-listen :on-jackpot-won (args>! on-jackpot-won-ch))

    (contract-call :bet bet2
                   {:gas gas-limit
                    :value (calculate-bet-cost bet2-count)
                    :from account2}
                   (response>! bet2-ch))

    (async done
      (go
        (let [tx-receipt-ch (chan)
              tx-filter (create-tx-receipt-filter tx-receipt-ch (<! bet2-ch))]
          (assert-not-out-of-gas (<! tx-receipt-ch))
          (web3-eth/stop-watching! tx-filter))

        (contract-call :bet winning-guesses
                       {:gas gas-limit
                        :value winning-bet-cost
                        :from winning-acc}
                       (response>! winning-bet-ch))

        (let [tx-receipt-ch (chan)
              tx-filter (create-tx-receipt-filter tx-receipt-ch (<! winning-bet-ch))]
          (assert-not-out-of-gas (<! tx-receipt-ch))
          (web3-eth/stop-watching! tx-filter))

        (let [{:keys [player-address roll date amount jackpot-key]} (<! on-jackpot-won-ch)]
          (is (= winning-acc player-address))
          (is (.eq roll 139))
          (is date)
          (is (.eq (calculate-guesses-cost (dec (+ winning-bet-count bet2-count))) amount))
          (is (.eq jackpot-key 0)))

        (let [[player-credit] (contract-call :players winning-acc)]
          (is (.gt player-credit (calculate-guesses-cost (dec (+ winning-bet-count bet2-count)))))
          (is (.lt player-credit (calculate-guesses-cost (+ winning-bet-count bet2-count)))))
        (is (.eq (calculate-guesses-cost 1) (contract-call :current-jackpot-amount)))

        (contract-call :withdraw
                       {:gas gas-limit
                        :from winning-acc}
                       (response>! withdraw-ch))

        (create-tx-receipt-filter withdraw-tx-receipt-ch (<! withdraw-ch))
        (assert-not-out-of-gas (<! withdraw-tx-receipt-ch))
        (is (.gt (web3-eth/get-balance w3 winning-acc) winner-balance-before))

        (contract-call :developer-withdraw-profit
                       {:gas gas-limit
                        :from (developer-account w3)}
                       (response>! developer-withdraw-ch))

        (<! developer-withdraw-ch)
        (is (.gt (web3-eth/get-balance w3 (developer-account w3)) developer-balance-before))

        (done)))))

(deftest sponsoring
  (println "Sponsoring")
  (let [sponsor1 (developer-account w3)
        sponsor1-ch (chan)
        sponsor1-amount (web3/to-big-number (web3/to-wei 1 :ether))
        sponsor2 (second (web3-eth/accounts w3))
        sponsor2-ch (chan)
        sponsor2-amount (web3/to-big-number (web3/to-wei 2 :ether))
        sponsor3 (last (web3-eth/accounts w3))
        sponsor3-ch (chan)
        sponsor3-amount (web3/to-big-number (web3/to-wei 3 :ether))
        on-jackpot-amount-change-ch (chan)
        on-sponsorship-added (chan)
        on-sponsor-updated (chan)
        on-top-sponsor-added (chan)
        on-top-sponsor-removed (chan)
        set-top-sponsors-max-length-ch (chan)]


    (contract-call :set-top-sponsors-max-length 2
                   {:gas gas-limit
                    :from (developer-account w3)} (response>! set-top-sponsors-max-length-ch))

    (contract-listen :on-jackpot-amount-change (args>! on-jackpot-amount-change-ch))
    (contract-listen :on-sponsorship-added (args>! on-sponsorship-added))
    (contract-listen :on-sponsor-updated (args>! on-sponsor-updated))
    (contract-listen :on-top-sponsor-added (args>! on-top-sponsor-added))
    (contract-listen :on-top-sponsor-removed (args>! on-top-sponsor-removed))

    (contract-call :sponsor "sponsor1" {:gas gas-limit
                                        :value sponsor1-amount
                                        :from sponsor1} (response>! sponsor1-ch))

    (async done
      (go
        (<! set-top-sponsors-max-length-ch)
        (let [tx-receipt-ch (chan)
              tx-filter (create-tx-receipt-filter tx-receipt-ch (<! sponsor1-ch))]
          (assert-not-out-of-gas (<! tx-receipt-ch))
          (web3-eth/stop-watching! tx-filter))

        (<! on-jackpot-amount-change-ch)
        (let [{:keys [amount jackpot-key]} (<! on-jackpot-amount-change-ch)]
          (is (.eq (amount-after-sponsor-fees sponsor1-amount) amount))
          (is (.eq jackpot-key 0)))

        (let [{:keys [jackpot-key sponsor-address name amount fee date sponsors-count sponsorships-count
                      total-sponsorships-amount]}
              (<! on-sponsorship-added)]
          (is (.eq jackpot-key 0))
          (is (.eq (amount-after-sponsor-fees sponsor1-amount) amount))
          (is (.eq (calculate-sponsor-fee sponsor1-amount) fee))
          (is (.eq sponsors-count 1))
          (is (.eq sponsorships-count 1))
          (is (.eq total-sponsorships-amount amount))
          (is (= sponsor-address sponsor1))
          (is (= name "sponsor1"))
          (is date))

        (let [{:keys [sponsor-address name amount date]} (<! on-sponsor-updated)]
          (is (.eq (amount-after-sponsor-fees sponsor1-amount) amount))
          (is (= sponsor-address sponsor1))
          (is (= name "sponsor1"))
          (is date))

        (let [{:keys [sponsor-address name amount date]} (<! on-top-sponsor-added)]
          (is (.eq (amount-after-sponsor-fees sponsor1-amount) amount))
          (is (= sponsor-address sponsor1))
          (is (= name "sponsor1"))
          (is date))

        (contract-call :sponsor "sponsor2" {:gas gas-limit
                                            :value sponsor2-amount
                                            :from sponsor2} (response>! sponsor2-ch))

        (let [{:keys [amount jackpot-key]} (<! on-jackpot-amount-change-ch)]
          (is (.eq (amount-after-sponsor-fees (.plus sponsor1-amount sponsor2-amount)) amount))
          (is (.eq jackpot-key 0)))

        (let [{:keys [jackpot-key sponsor-address name amount fee date sponsors-count sponsorships-count
                      total-sponsorships-amount]}
              (<! on-sponsorship-added)]
          (is (.eq (amount-after-sponsor-fees sponsor2-amount) amount))
          (is (.eq (calculate-sponsor-fee sponsor2-amount) fee))
          (is (.eq sponsors-count 2))
          (is (.eq sponsorships-count 2))
          (is (= sponsor-address sponsor2))
          (is (= name "sponsor2")))

        (let [{:keys [sponsor-address name amount]} (<! on-sponsor-updated)]
          (is (.eq (amount-after-sponsor-fees sponsor2-amount) amount))
          (is (= sponsor-address sponsor2))
          (is (= name "sponsor2")))

        (let [{:keys [sponsor-address name amount]} (<! on-top-sponsor-added)]
          (is (.eq (amount-after-sponsor-fees sponsor2-amount) amount))
          (is (= sponsor-address sponsor2))
          (is (= name "sponsor2"))
          (is (= (contract-call :top-sponsors-addresses 0) sponsor2))
          (is (= (contract-call :top-sponsors-addresses 1) sponsor1)))

        (contract-call :sponsor "sponsor3" {:gas gas-limit
                                            :value sponsor3-amount
                                            :from sponsor3} (response>! sponsor3-ch))

        (let [{:keys [amount jackpot-key]} (<! on-jackpot-amount-change-ch)]
          (is (.eq (amount-after-sponsor-fees (.plus (.plus sponsor1-amount sponsor2-amount)
                                                     sponsor3-amount)) amount))
          (is (.eq jackpot-key 0)))

        (let [{:keys [jackpot-key sponsor-address name amount fee date sponsors-count sponsorships-count
                      total-sponsorships-amount]}
              (<! on-sponsorship-added)]
          (is (.eq (amount-after-sponsor-fees sponsor3-amount) amount))
          (is (.eq (calculate-sponsor-fee sponsor3-amount) fee))
          (is (.eq sponsors-count 3))
          (is (.eq sponsorships-count 3))
          (is (= sponsor-address sponsor3))
          (is (= name "sponsor3")))

        (let [{:keys [sponsor-address name amount]} (<! on-sponsor-updated)]
          (is (.eq (amount-after-sponsor-fees sponsor3-amount) amount))
          (is (= sponsor-address sponsor3))
          (is (= name "sponsor3")))

        (let [{:keys [sponsor-address name amount]} (<! on-top-sponsor-added)]
          (is (.eq (amount-after-sponsor-fees sponsor3-amount) amount))
          (is (= sponsor-address sponsor3))
          (is (= name "sponsor3"))
          (is (= (contract-call :top-sponsors-addresses 0) sponsor3))
          (is (= (contract-call :top-sponsors-addresses 1) sponsor2)))

        (let [{:keys [sponsor-address]} (<! on-top-sponsor-removed)]
          (is (= sponsor-address sponsor1))
          (is (not= (contract-call :top-sponsors-addresses 2) sponsor1)))

        (contract-call :sponsor "sponsor1-new-name" {:gas gas-limit
                                                     :value sponsor1-amount
                                                     :from sponsor1} (response>! sponsor1-ch))

        (let [{:keys [sponsor-address name amount]} (<! on-sponsor-updated)]
          (is (.eq (amount-after-sponsor-fees (.times sponsor1-amount 2)) amount))
          (is (= sponsor-address sponsor1))
          (is (= name "sponsor1-new-name")))

        (let [[name amount exists] (contract-call :sponsors sponsor1)]
          (is (.eq (amount-after-sponsor-fees (.times sponsor1-amount 2)) amount))
          (is (= name "sponsor1-new-name"))
          (is exists))

        (is (.eq (first (contract-call :jackpots 0))
                 (amount-after-sponsor-fees
                   (.plus (.times sponsor1-amount 2)
                          (.plus sponsor2-amount sponsor3-amount)))))

        (done)))))

(deftest invalid-oraclize-response
  (println "Invalid Oraclize Response")
  (let [bet-ch (chan)
        player-address (second (web3-eth/accounts w3))]

    (contract-call :bet (range 1 21)
                   {:gas gas-limit
                    :value (calculate-bet-cost 20)
                    :from (second (web3-eth/accounts w3))}
                   (response>! bet-ch))

    (async done
      (go
        (let [tx-receipt-ch (chan)
              tx-filter (create-tx-receipt-filter tx-receipt-ch (<! bet-ch))]
          (assert-not-out-of-gas (<! tx-receipt-ch))
          (web3-eth/stop-watching! tx-filter))

        (let [[_ _ rolls] (contract-call :get-bet 0)]
          (is (.eq (last rolls) 0))
          (.eq (first (contract-call :players player-address)) (calculate-bet-cost 20))
          (is (.eq (first (contract-call :jackpots 0)) 0)))

        (contract-call :bet (range 1 21)
                       {:gas gas-limit
                        :value (calculate-bet-cost 20)
                        :from (second (web3-eth/accounts w3))}
                       (response>! bet-ch))

        (let [tx-receipt-ch (chan)
              tx-filter (create-tx-receipt-filter tx-receipt-ch (<! bet-ch))]
          (assert-not-out-of-gas (<! tx-receipt-ch))
          (web3-eth/stop-watching! tx-filter))

        (let [[_ _ rolls] (contract-call :get-bet 1)]
          (is (.eq (last rolls) 0))
          (.eq (first (contract-call :players player-address)) (.times (calculate-bet-cost 20) 2))
          (is (.eq (first (contract-call :jackpots 0)) 0)))

        (contract-call :bet (range 1 21)
                       {:gas gas-limit
                        :value (calculate-bet-cost 20)
                        :from (second (web3-eth/accounts w3))}
                       (response>! bet-ch))

        (let [tx-receipt-ch (chan)
              tx-filter (create-tx-receipt-filter tx-receipt-ch (<! bet-ch))]
          (assert-not-out-of-gas (<! tx-receipt-ch))
          (web3-eth/stop-watching! tx-filter))

        (let [[_ _ rolls] (contract-call :get-bet 2)]
          (is (.eq (last rolls) 0))
          (.eq (first (contract-call :players player-address)) (.times (calculate-bet-cost 20) 3))
          (is (.eq (first (contract-call :jackpots 0)) 0)))


        (done)))))

(deftest refund-everone
  (println "Refund Everyone")
  (let [bet1-ch (chan)
        bet2-ch (chan)
        refund-ch (chan)
        sponsor1-ch (chan)
        sponsor1 (last (butlast (web3-eth/accounts w3)))
        sponsor1-amount (web3/to-big-number (web3/to-wei 1 :ether))]
    (async done
      (contract-call :bet [1 2 3]
                     {:gas gas-limit
                      :value (calculate-bet-cost 3)
                      :from (last (web3-eth/accounts w3))}
                     (response>! bet1-ch))

      (contract-call :bet [1 2 3 4]
                     {:gas gas-limit
                      :value (calculate-bet-cost 4)
                      :from (second (web3-eth/accounts w3))}
                     (response>! bet2-ch))

      (contract-call :sponsor "sponsor1"
                     {:gas gas-limit
                      :value sponsor1-amount
                      :from sponsor1} (response>! sponsor1-ch))

      (go
        (let [tx-receipt-ch (chan)
              tx-filter (create-tx-receipt-filter tx-receipt-ch (<! bet1-ch))]
          (assert-not-out-of-gas (<! tx-receipt-ch))
          (web3-eth/stop-watching! tx-filter))

        (let [tx-receipt-ch (chan)
              tx-filter (create-tx-receipt-filter tx-receipt-ch (<! bet2-ch))]
          (assert-not-out-of-gas (<! tx-receipt-ch))
          (web3-eth/stop-watching! tx-filter))

        (let [tx-receipt-ch (chan)
              tx-filter (create-tx-receipt-filter tx-receipt-ch (<! sponsor1-ch))]
          (assert-not-out-of-gas (<! tx-receipt-ch))
          (web3-eth/stop-watching! tx-filter))

        (is (.eq (contract-call :current-jackpot-amount) (.plus (calculate-guesses-cost 7)
                                                                (amount-after-sponsor-fees sponsor1-amount))))
        (let [balance-before1 (web3-eth/get-balance w3 (last (web3-eth/accounts w3)))
              [credit-before1] (contract-call :players (last (web3-eth/accounts w3)))
              balance-before2 (web3-eth/get-balance w3 (second (web3-eth/accounts w3)))
              [credit-before2] (contract-call :players (second (web3-eth/accounts w3)))
              sponsor-balance-before (web3-eth/get-balance w3 sponsor1)]

          (contract-call :refund-everyone
                         {:gas gas-limit
                          :from (developer-account w3)}
                         (response>! refund-ch))

          (let [tx-receipt-ch (chan)
                tx-filter (create-tx-receipt-filter tx-receipt-ch (<! refund-ch))]
            (assert-not-out-of-gas (<! tx-receipt-ch))
            (web3-eth/stop-watching! tx-filter))

          (is (.eq (contract-call :current-jackpot-amount) 0))
          (is (.eq (web3-eth/get-balance w3 (last (web3-eth/accounts w3)))
                   (.plus (.plus balance-before1 credit-before1)
                          (calculate-guesses-cost 3))))

          (is (.eq (web3-eth/get-balance w3 (second (web3-eth/accounts w3)))
                   (.plus (.plus balance-before2 credit-before2)
                          (calculate-guesses-cost 4))))

          (is (.eq (web3-eth/get-balance w3 sponsor1)
                   (.plus sponsor-balance-before
                          (amount-after-sponsor-fees sponsor1-amount))))
          (done))))))


(deftest invalid-permissions-and-inputs
  (println "Invalid Permissions")
  (let [chans (repeatedly 10 #(chan))
        opts {:gas gas-limit :from (last (web3-eth/accounts w3))}]
    (go
      (contract-call :change-settings 1 1 1 1 1 1 1 opts (response>! (first chans)))
      (contract-call :set-inactive-state opts (response>! (second chans)))
      (contract-call :set-active-state opts (response>! (nth chans 2)))
      (contract-call :change-developer (last (web3-eth/accounts w3)) opts (response>! (nth chans 3)))
      (contract-call :developer-withdraw-profit opts (response>! (nth chans 4)))

      (testing "Invaid bet input"
        (contract-call :bet [(.plus (contract-call :total-possibilities) 1)]
                       (assoc opts :value (calculate-bet-cost 1))
                       (response>! (nth chans 5)))

        (contract-call :bet [-1]
                       (assoc opts :value (calculate-bet-cost 1))
                       (response>! (nth chans 6))))

      (testing "Insufficient funds"
        (contract-call :bet [1 1 1 1]
                       (assoc opts :value (calculate-bet-cost 3))
                       (response>! (nth chans 7))))

      (testing "Too many bet inputs"
        (let [bets-count (.toNumber (.plus (contract-call :max-guesses-at-once) 1))]
          (contract-call :bet (range bets-count)
                         (assoc opts :value (calculate-bet-cost bets-count))
                         (response>! (nth chans 8)))))

      (testing "Invalid sponsor name"
        (let [max-length (.toNumber (contract-call :sponsor-name-max-length))]
          (contract-call :sponsor (reduce str (repeatedly (inc max-length) (constantly "A")))
                         (assoc opts :value (web3/to-big-number (web3/to-wei 1 :ether)))
                         (response>! (nth chans 9))))))
    (async done
      (go
        (doseq [ch chans]
          (let [tx-receipt-ch (chan)
                tx-filter (create-tx-receipt-filter tx-receipt-ch (<! ch))]
            (assert-out-of-gas (<! tx-receipt-ch))
            (web3-eth/stop-watching! tx-filter)))
        (done)))))


(deftest admin-functions
  (println "Admin Functions")
  (let [settings-ch (chan)
        invalid-settngs-chs (repeatedly 3 #(chan))
        set-inactive-state-ch (chan)
        disabled-bet-ch (chan)
        enable-betting-ch (chan)
        change-developer-ch (chan)
        on-settings-change-ch (chan)
        opts {:gas gas-limit
              :from (developer-account w3)}]


    (contract-listen :on-settings-change (args>! on-settings-change-ch))

    (testing "Valid settings"
      (contract-call :change-settings (web3/to-wei 0.2 :ether) 500 50 20 20 100 (web3/to-wei 2 :ether)
                     opts (response>! settings-ch)))

    (testing "Can't lower guess cost"
      (contract-call :change-settings (web3/to-wei 0.05 :ether) 500 50 20 20 100 (web3/to-wei 1 :ether)
                     opts (response>! (first invalid-settngs-chs))))

    (testing "Can't lower total posibilities"
      (contract-call :change-settings (web3/to-wei 0.1 :ether) 10 50 20 20 100 (web3/to-wei 1 :ether)
                     opts (response>! (second invalid-settngs-chs))))

    (testing "Can't increase bet fee"
      (contract-call :change-settings (web3/to-wei 0.1 :ether) 500 1000 20 20 100 (web3/to-wei 1 :ether)
                     opts (response>! (nth invalid-settngs-chs 2))))

    (contract-call :set-inactive-state opts (response>! set-inactive-state-ch))

    (async done
      (go
        (let [tx-receipt-ch (chan)
              tx-filter (create-tx-receipt-filter tx-receipt-ch (<! settings-ch))]
          (assert-not-out-of-gas (<! tx-receipt-ch))
          (web3-eth/stop-watching! tx-filter))

        (let [{:keys [guess-cost total-possibilities guess-fee-ratio max-guesses-at-once
                      sponsor-name-max-length sponsorship-fee-ratio sponsorship-min-amount]}
              (<! on-settings-change-ch)]
          (is (.eq (web3/from-wei guess-cost :ether) 0.2))
          (is (.eq total-possibilities 500))
          (is (.eq guess-fee-ratio 50))
          (is (.eq max-guesses-at-once 20))
          (is (.eq sponsor-name-max-length 20))
          (is (.eq sponsorship-fee-ratio 100))
          (is (.eq sponsorship-min-amount (web3/to-wei 2 :ether))))

        (is (.eq (web3/from-wei (contract-call :guess-cost) :ether) 0.2))
        (is (.eq (contract-call :total-possibilities) 500))
        (is (.eq (contract-call :guess-fee-ratio) 50))
        (is (.eq (contract-call :max-guesses-at-once) 20))
        (is (.eq (contract-call :sponsor-name-max-length) 20))
        (is (.eq (contract-call :sponsorship-fee-ratio) 100))
        (is (.eq (contract-call :sponsorship-min-amount) (web3/to-wei 2 :ether)))

        (doseq [ch invalid-settngs-chs]
          (let [tx-receipt-ch (chan)
                tx-filter (create-tx-receipt-filter tx-receipt-ch (<! ch))]
            (assert-out-of-gas (<! tx-receipt-ch))
            (web3-eth/stop-watching! tx-filter)))

        (let [tx-receipt-ch (chan)
              tx-filter (create-tx-receipt-filter tx-receipt-ch (<! set-inactive-state-ch))]
          (assert-not-out-of-gas (<! tx-receipt-ch))
          (web3-eth/stop-watching! tx-filter))

        (is (.eq (contract-call :state) 1))

        (contract-call :bet [1]
                       (merge {:value (calculate-bet-cost 1)} opts)
                       (response>! disabled-bet-ch))

        (let [tx-receipt-ch (chan)
              tx-filter (create-tx-receipt-filter tx-receipt-ch (<! disabled-bet-ch))]
          (assert-out-of-gas (<! tx-receipt-ch))
          (web3-eth/stop-watching! tx-filter))


        (contract-call :set-active-state opts (response>! enable-betting-ch))

        (let [tx-receipt-ch (chan)
              tx-filter (create-tx-receipt-filter tx-receipt-ch (<! enable-betting-ch))]
          (assert-not-out-of-gas (<! tx-receipt-ch))
          (web3-eth/stop-watching! tx-filter))

        (is (.eq (contract-call :state) 0))

        (contract-call :change-developer (last (web3-eth/accounts w3)) opts (response>! change-developer-ch))

        (let [tx-receipt-ch (chan)
              tx-filter (create-tx-receipt-filter tx-receipt-ch (<! change-developer-ch))]
          (assert-not-out-of-gas (<! tx-receipt-ch))
          (web3-eth/stop-watching! tx-filter))

        (is (= (last (web3-eth/accounts w3)) (contract-call :developer)))
        (done)))))
