(ns ^:figwheel-no-load emojillionaire.contract-test
  (:require [cljs.core.async :refer [<! >! chan]]
            [cljs.test :refer-macros [deftest is testing run-tests use-fixtures async]]
            [cljsjs.web3]
            [goog.string :as gstring]
            [goog.string.format]
            [emojillionaire.utils :as u]
            [web3-cljs.core :as wb]
            [web3-cljs.eth :as we]
            [web3-cljs.utils :as wu]
            [print.foo :include-macros true]
            )
  (:require-macros [cljs.core.async.macros :refer [go]]))

(comment
  (run-tests 'emojillionaire.contract-test))

(def w3 (or (aget js/window "web3")
            (wb/create-web3 "http://localhost:8545/")))

(def gas-limit 4712388)

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
  (= (first (we/accounts w3)) address))

(defn- developer-account [web3 & [clb]]
  (first (we/accounts web3 clb)))

(.clear js/console)

(use-fixtures
  :each
  {:before
   (fn []
     (let [account-ch (chan)]
       (we/accounts w3 (response>! account-ch))
       (async done
         (go
           (is w3)
           (let [account (first (<! account-ch))]
             (is (not-empty account))
             (u/fetch-contract!
               "/contracts/build/"
               contract-name
               (fn [abi bin]
                 (is (array? abi))
                 (is (string? bin))
                 (u/deploy-bin! w3 abi bin account gas-limit
                                (fn [res]
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
                                  (assert (not err)))))))))))
   :after
   (fn []
     (async done (js/setTimeout #(done) 0)))                ; Prevents from hanging between tests, don't ask me why
   })

(defn- contract-listen [event-name on-success]
  (wu/contract-call *contract* event-name nil {:from-block 0}
                    (fn [err log]
                      (assert (not err))
                      (on-success log))))

(defn- contract-call [k & args]
  (apply wu/contract-call *contract* k args))

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
        (.div (wb/to-big-number 10000)
              (contract-call :sponsorship-fee-ratio))))

(defn- amount-after-sponsor-fees [total-amount]
  (.minus total-amount (calculate-sponsor-fee total-amount)))

(defn- rand-address []
  (subs (wb/sha3 (str (random-uuid))) 0 42))

(defn- create-tx-receipt-filter [tx-receipt-ch tx-hash]
  (we/filter w3 "latest"
             (fn [err]
               (assert-no-error err)
               (when-let [tx-receipt (we/get-transaction-receipt w3 tx-hash)]
                 (go (>! tx-receipt-ch tx-receipt))))))

(deftest betting
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
         "https://www.random.org/integers/?num=15&min=0&col=1&base=10&format=plain&rnd=new&max=349"))

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
            (is (= "0.005" (wb/from-wei (.toNumber credit) :ether))))

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
  (let [winning-acc (second (we/accounts w3))
        winning-guesses [1 1 1 139 1 1 1 25]
        winning-bet-count (count winning-guesses)
        winning-bet-cost (calculate-bet-cost winning-bet-count)
        winner-balance-before (we/get-balance w3 winning-acc)
        winning-bet-ch (chan)
        withdraw-tx-receipt-ch (chan)
        withdraw-ch (chan)
        account2 (last (we/accounts w3))
        bet2 [1 2 3 4 5 6]
        bet2-ch (chan)
        bet2-count (count bet2)
        on-jackpot-won-ch (chan)
        developer-withdraw-ch (chan)
        developer-balance-before (we/get-balance w3 (developer-account w3))]

    (contract-listen :on-jackpot-won (args>! on-jackpot-won-ch))

    (contract-call :bet bet2
                   {:gas gas-limit
                    :value (calculate-bet-cost bet2-count)
                    :from account2}
                   (response>! bet2-ch))

    (contract-call :bet winning-guesses
                   {:gas gas-limit
                    :value winning-bet-cost
                    :from winning-acc}
                   (response>! winning-bet-ch))

    (async done
      (go
        (<! winning-bet-ch)
        (<! bet2-ch)
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
        (is (.gt (we/get-balance w3 winning-acc) winner-balance-before))

        (contract-call :developer-withdraw-profit
                       {:gas gas-limit
                        :from (developer-account w3)}
                       (response>! developer-withdraw-ch))

        (<! developer-withdraw-ch)
        (is (.gt (we/get-balance w3 (developer-account w3)) developer-balance-before))

        (done)))))

(deftest sponsoring
  (let [sponsor1 (developer-account w3)
        sponsor1-ch (chan)
        sponsor1-amount (wb/to-big-number (wb/to-wei 1 :ether))
        sponsor2 (second (we/accounts w3))
        sponsor2-ch (chan)
        sponsor2-amount (wb/to-big-number (wb/to-wei 2 :ether))
        sponsor3 (last (we/accounts w3))
        sponsor3-ch (chan)
        sponsor3-amount (wb/to-big-number (wb/to-wei 3 :ether))
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
          (.stopWatching tx-filter))

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

        (done)))
    ))

(deftest refund-everone
  (let [bet1-ch (chan)
        bet2-ch (chan)
        refund-ch (chan)
        sponsor1-ch (chan)
        sponsor1 (last (butlast (we/accounts w3)))
        sponsor1-amount (wb/to-big-number (wb/to-wei 1 :ether))]
    (async done
      (contract-call :bet [1 2 3]
                     {:gas gas-limit
                      :value (calculate-bet-cost 3)
                      :from (last (we/accounts w3))}
                     (response>! bet1-ch))

      (contract-call :bet [1 2 3 4]
                     {:gas gas-limit
                      :value (calculate-bet-cost 4)
                      :from (second (we/accounts w3))}
                     (response>! bet2-ch))

      (contract-call :sponsor "sponsor1"
                     {:gas gas-limit
                      :value sponsor1-amount
                      :from sponsor1} (response>! sponsor1-ch))

      (go
        (let [tx-receipt-ch (chan)
              tx-filter (create-tx-receipt-filter tx-receipt-ch (<! bet1-ch))]
          (assert-not-out-of-gas (<! tx-receipt-ch))
          (.stopWatching tx-filter))

        (let [tx-receipt-ch (chan)
              tx-filter (create-tx-receipt-filter tx-receipt-ch (<! bet2-ch))]
          (assert-not-out-of-gas (<! tx-receipt-ch))
          (.stopWatching tx-filter))

        (let [tx-receipt-ch (chan)
              tx-filter (create-tx-receipt-filter tx-receipt-ch (<! sponsor1-ch))]
          (assert-not-out-of-gas (<! tx-receipt-ch))
          (.stopWatching tx-filter))

        (is (.eq (contract-call :current-jackpot-amount) (.plus (calculate-guesses-cost 7)
                                                                (amount-after-sponsor-fees sponsor1-amount))))
        (let [balance-before1 (we/get-balance w3 (last (we/accounts w3)))
              [credit-before1] (contract-call :players (last (we/accounts w3)))
              balance-before2 (we/get-balance w3 (second (we/accounts w3)))
              [credit-before2] (contract-call :players (second (we/accounts w3)))
              sponsor-balance-before (we/get-balance w3 sponsor1)]

          (contract-call :refund-everyone
                         {:gas gas-limit
                          :from (developer-account w3)}
                         (response>! refund-ch))

          (let [tx-receipt-ch (chan)
                tx-filter (create-tx-receipt-filter tx-receipt-ch (<! refund-ch))]
            (assert-not-out-of-gas (<! tx-receipt-ch))
            (.stopWatching tx-filter))

          (is (.eq (contract-call :current-jackpot-amount) 0))
          (is (.eq (we/get-balance w3 (last (we/accounts w3)))
                   (.plus (.plus balance-before1 credit-before1)
                          (calculate-guesses-cost 3))))

          (is (.eq (we/get-balance w3 (second (we/accounts w3)))
                   (.plus (.plus balance-before2 credit-before2)
                          (calculate-guesses-cost 4))))

          (is (.eq (we/get-balance w3 sponsor1)
                   (.plus sponsor-balance-before
                          (amount-after-sponsor-fees sponsor1-amount))))
          (done))))))


(deftest invalid-permissions-and-inputs
  (let [chans (repeatedly 10 #(chan))
        opts {:gas gas-limit :from (last (we/accounts w3))}]
    (go
      (contract-call :change-settings 1 1 1 1 1 1 1 opts (response>! (first chans)))
      (contract-call :set-inactive-state opts (response>! (second chans)))
      (contract-call :set-active-state opts (response>! (nth chans 2)))
      (contract-call :change-developer (last (we/accounts w3)) opts (response>! (nth chans 3)))
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
                         (assoc opts :value (wb/to-big-number (wb/to-wei 1 :ether)))
                         (response>! (nth chans 9))))))
    (async done
      (go
        (doseq [ch chans]
          (let [tx-receipt-ch (chan)
                tx-filter (create-tx-receipt-filter tx-receipt-ch (<! ch))]
            (assert-out-of-gas (<! tx-receipt-ch))
            (.stopWatching tx-filter)))
        (done)))))


(deftest admin-functions
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
      (contract-call :change-settings (wb/to-wei 0.2 :ether) 500 50 20 20 100 (wb/to-wei 2 :ether)
                     opts (response>! settings-ch)))

    (testing "Can't lower guess cost"
      (contract-call :change-settings (wb/to-wei 0.05 :ether) 500 50 20 20 100 (wb/to-wei 1 :ether)
                     opts (response>! (first invalid-settngs-chs))))

    (testing "Can't lower total posibilities"
      (contract-call :change-settings (wb/to-wei 0.1 :ether) 10 50 20 20 100 (wb/to-wei 1 :ether)
                     opts (response>! (second invalid-settngs-chs))))

    (testing "Can't increase bet fee"
      (contract-call :change-settings (wb/to-wei 0.1 :ether) 500 1000 20 20 100 (wb/to-wei 1 :ether)
                     opts (response>! (nth invalid-settngs-chs 2))))

    (contract-call :set-inactive-state opts (response>! set-inactive-state-ch))

    (async done
      (go
        (let [tx-receipt-ch (chan)
              tx-filter (create-tx-receipt-filter tx-receipt-ch (<! settings-ch))]
          (assert-not-out-of-gas (<! tx-receipt-ch))
          (.stopWatching tx-filter))

        (let [{:keys [guess-cost total-possibilities guess-fee-ratio max-guesses-at-once
                      sponsor-name-max-length sponsorship-fee-ratio sponsorship-min-amount]}
              (<! on-settings-change-ch)]
          (is (.eq (wb/from-wei guess-cost :ether) 0.2))
          (is (.eq total-possibilities 500))
          (is (.eq guess-fee-ratio 50))
          (is (.eq max-guesses-at-once 20))
          (is (.eq sponsor-name-max-length 20))
          (is (.eq sponsorship-fee-ratio 100))
          (is (.eq sponsorship-min-amount (wb/to-wei 2 :ether))))

        (is (.eq (wb/from-wei (contract-call :guess-cost) :ether) 0.2))
        (is (.eq (contract-call :total-possibilities) 500))
        (is (.eq (contract-call :guess-fee-ratio) 50))
        (is (.eq (contract-call :max-guesses-at-once) 20))
        (is (.eq (contract-call :sponsor-name-max-length) 20))
        (is (.eq (contract-call :sponsorship-fee-ratio) 100))
        (is (.eq (contract-call :sponsorship-min-amount) (wb/to-wei 2 :ether)))

        (doseq [ch invalid-settngs-chs]
          (let [tx-receipt-ch (chan)
                tx-filter (create-tx-receipt-filter tx-receipt-ch (<! ch))]
            (assert-out-of-gas (<! tx-receipt-ch))
            (.stopWatching tx-filter)))

        (let [tx-receipt-ch (chan)
              tx-filter (create-tx-receipt-filter tx-receipt-ch (<! set-inactive-state-ch))]
          (assert-not-out-of-gas (<! tx-receipt-ch))
          (.stopWatching tx-filter))

        (is (.eq (contract-call :state) 1))

        (contract-call :bet [1]
                       (merge {:value (calculate-bet-cost 1)} opts)
                       (response>! disabled-bet-ch))

        (let [tx-receipt-ch (chan)
              tx-filter (create-tx-receipt-filter tx-receipt-ch (<! disabled-bet-ch))]
          (assert-out-of-gas (<! tx-receipt-ch))
          (.stopWatching tx-filter))


        (contract-call :set-active-state opts (response>! enable-betting-ch))

        (let [tx-receipt-ch (chan)
              tx-filter (create-tx-receipt-filter tx-receipt-ch (<! enable-betting-ch))]
          (assert-not-out-of-gas (<! tx-receipt-ch))
          (.stopWatching tx-filter))

        (is (.eq (contract-call :state) 0))

        (contract-call :change-developer (last (we/accounts w3)) opts (response>! change-developer-ch))

        (let [tx-receipt-ch (chan)
              tx-filter (create-tx-receipt-filter tx-receipt-ch (<! change-developer-ch))]
          (assert-not-out-of-gas (<! tx-receipt-ch))
          (.stopWatching tx-filter))

        (is (= (last (we/accounts w3)) (contract-call :developer)))
        (done)))))






