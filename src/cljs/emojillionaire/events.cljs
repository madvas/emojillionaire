(ns emojillionaire.events
  (:require
    [ajax.core :as ajax]
    [camel-snake-kebab.core :as cs :include-macros true]
    [cljs-web3.core :as web3]
    [cljs-web3.eth :as web3-eth]
    [cljs-web3.personal :as web3-personal]
    [cljs.spec :as s]
    [day8.re-frame.http-fx]
    [emojillionaire.components.emoji :refer [emoji]]
    [emojillionaire.components.layout :refer [row]]
    [emojillionaire.db :refer [default-db]]
    [emojillionaire.utils :as u]
    [goog.string :as gstring]
    [goog.string.format]
    [madvas.re-frame.google-analytics-fx]
    [madvas.re-frame.web3-fx]
    [re-frame.core :refer [reg-event-db reg-event-fx path trim-v after debug reg-fx console dispatch]]))

(defn check-and-throw
  "throw an exception if db doesn't match the spec."
  [a-spec db]
  (when-not (s/valid? a-spec db)
    (throw (ex-info (str "spec check failed: " (s/explain-str a-spec db)) {}))))

(def check-spec-interceptor (after (partial check-and-throw :todomvc.db/db)))

(def interceptors [;check-spec-interceptor
                   ;(path :todos)                        ;; 1st param to handler will be the value from this path
                   #_(when ^boolean js/goog.DEBUG debug)
                   trim-v])

(defn- my-address? [db address]
  (contains? (set (:my-addresses db)) address))

(def dispatch-snackbar-close #(dispatch [:snackbar/close]))

(defn- snackbar-text-with-emoji [text emoji-key]
  [row {:middle "xs" :center "xs"}
   text
   [emoji emoji-key 30 10]])

(defn- open-snackbar [db text & [emoji-key opts]]
  (update db :snackbar merge
          (merge {:open? true
                  :on-request-close dispatch-snackbar-close
                  :action nil
                  :on-action-touch-tap (fn [])
                  :message (if emoji-key
                             [snackbar-text-with-emoji text emoji-key]
                             text)}
                 opts)))

(defn- snackbar-etherescan-action [network transaction-hash]
  [:a {:href (u/etherscan-tx-url network transaction-hash) :target :_blank} "Open in Etherscan"])

(defn- privnet? [db]
  (= (:network db) :privnet))

(reg-event-fx
  :initialize
  (fn [_ _]
    {:db default-db
     :http-xhrio {:method :get
                  :uri (gstring/format "/contracts/build/%s.json"
                                       (cs/->camelCase (get-in default-db [:contract :name])))
                  :timeout 15000
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success [:contract/compiled-code-loaded]
                  :on-failure [:contract/compiled-code-load-error]}
     :dispatch [:load-conversion-rate :usd]}
    #_{:db default-db}))

;; ------------ Contract ------------

(reg-event-fx
  :contract/compiled-code-loaded
  interceptors
  (fn [{:keys [db]} [contracts]]
    (let [{:keys [bin abi]} (get-in contracts [:contracts (keyword (:name (:contract db)))])
          abi (js/JSON.parse abi)
          {:keys [network web3]} db
          contract-instance (web3-eth/contract-at web3 abi (:address (:contract db)))]
      (merge
        {:db
         (cond-> db
           true
           (update :contract merge {:abi abi :bin bin :instance contract-instance})

           (not (:provides-web3? db))
           (open-snackbar "Welcome! Looks like your browser can't handle Ethereum yet. Please see How to Play" :ghost))

         :web3-fx.contract/events
         {:db db
          :instance contract-instance
          :db-path [:contract :events]
          :events [[:on-settings-change {} "latest" :contract/on-settings-change :contract/on-error]
                   [:on-new-jackpot-started {} "latest" :contract/on-new-jackpot-started :contract/on-error]
                   [:on-jackpot-amount-change {} "latest" :contract/on-jackpot-amount-change :contract/on-error]
                   [:on-state-change {} "latest" :contract/on-state-change :contract/on-error]
                   [:on-player-credit-change {} "latest" :contract/on-player-credit-change :contract/on-error]
                   [:on-jackpot-won-latest :on-jackpot-won {} "latest" :contract/on-jackpot-won-latest :contract/on-error]
                   [:on-new-player {} "latest" :contract/on-new-player :contract/on-error]
                   [:on-bet {} "latest" :contract/on-bet :contract/on-error]
                   [:on-top-sponsor-added {} "latest" :contract/on-top-sponsor-added :contract/on-error]
                   [:on-top-sponsor-removed {} "latest" :contract/on-top-sponsor-removed :contract/on-error]
                   [:on-sponsor-updated {} "latest" :contract/on-sponsor-updated :contract/on-error]
                   [:on-oraclize-fee-change {} "latest" :contract/on-oraclize-fee-change :contract/on-error]]}

         :web3-fx.contract/constant-fns
         {:instance contract-instance
          :fns [[:get-settings :contract/get-settings :contract/on-error]
                [:get-initial-load-data :contract/get-initial-load-data :contract/on-error]]}}
        (when (:provides-web3? db)
          {:web3-fx.blockchain/fns
           {:web3 web3
            :fns (concat
                   [[web3-eth/accounts :blockchain/my-addresses-loaded :blockchain/on-error]
                    [web3-eth/block-number :blockchain/block-number-loaded :blockchain/on-error]]
                   (when-let [dev-accounts (get-in db [:dev-accounts network])]
                     (map (fn [[address password]]
                            [web3-personal/unlock-account address password 604800
                             [:blockchain/unlock-account-response address] [:blockchain/unlock-account-error address]])
                          dev-accounts)))}})))))

(reg-event-fx
  :contract/get-initial-load-data
  interceptors
  (fn [{:keys [db]} [result]]
    (let [result-map (zipmap [:state :jackpot-amount :jackpot-since :total-jackpot-amounts-won :jackpots-count
                              :bets-count :total-guesses-count :players-count :sponsors-count :sponsorships-count
                              :total-sponsorships-amount :top-sponsors-addresses]
                             result)
          {:keys [jackpot-amount jackpot-since jackpots-count state top-sponsors-addresses]} result-map
          jackpot-key (dec (.toNumber jackpots-count))]
      (merge
        {:db (-> db
               (assoc :jackpot {:amount jackpot-amount
                                :since (u/big-number->date-time jackpot-since)
                                :jackpot-key jackpot-key})
               (assoc-in [:contract :state] (u/big-number->state state))
               (assoc :top-sponsors-addresses top-sponsors-addresses)
               (update-in [:contract :stats] merge
                          (-> result-map
                            (u/keys-big-number->number [:jackpots-count :bets-count :total-guesses-count
                                                        :players-count :sponsors-count :sponsorships-count])
                            (dissoc :state :jackpot-amount :jackpot-since :top-sponsors-addresses))))
         :web3-fx.contract/events
         {:db db
          :instance (get-in db [:contract :instance])
          :db-path [:contract :events]
          :events [[:on-sponsorship-added
                    {:jackpot-key jackpot-key}
                    {:from-block 0}
                    :contract/on-sponsorship-added
                    :contract/on-error]]}}
        (when (seq top-sponsors-addresses)
          {:web3-fx.contract/constant-fns
           {:instance (get-in db [:contract :instance])
            :fns (map #(vec [:sponsors % [:contract/sponsor-loaded %] :contract/on-error]) top-sponsors-addresses)}})))))

(reg-event-fx
  :contract/get-settings
  interceptors
  (fn [{:keys [db]} [result]]
    (let [settings [:guess-cost :total-possibilities :guess-fee-ratio :guess-fee :max-guesses-at-once
                    :sponsor-name-max-length :sponsorship-fee-ratio :sponsorship-min-amount :oraclize-fee]
          settings-map (zipmap settings result)]

      (if (.eq (:total-possibilities settings-map) 0)
        {:db (open-snackbar db "Looks like I can't reach contract. Are you on Morden Testnet?" :astonished)}
        {:dispatch [:contract/on-settings-change (zipmap settings result)]}))))

(reg-event-db
  :contract/on-settings-change
  interceptors
  (fn [db [result]]
    (update-in db [:contract :config] merge
               (u/keys-big-number->number result [:total-possibilities :max-guesses-at-once :guess-fee-ratio
                                                  :sponsorship-fee-ratio :sponsor-name-max-length
                                                  :sponsorship-min-amount]))))

(reg-event-db
  :contract/on-oraclize-fee-change
  interceptors
  (fn [db [oraclize-fee]]
    (assoc-in db [:contract :config :oraclize-fee] oraclize-fee)))

(reg-event-fx
  :contract/on-new-jackpot-started
  interceptors
  (fn [{:keys [db]} [{:keys [jackpot-key date]}]]
    {:db (update db :jackpot merge {:since (u/big-number->date-time date)
                                    :jackpot-key (.toNumber jackpot-key)})
     :web3-fx.contract/events
     {:db db
      :instance (get-in db [:contract :instance])
      :db-path [:contract :events]
      :events [[:on-sponsorship-added
                {:jackpot-key jackpot-key}
                "latest"
                :contract/on-sponsorship-added
                :contract/on-error]]}}))

(reg-event-db
  :contract/on-jackpot-amount-change
  interceptors
  (fn [db [{:keys [amount]}]]
    (assoc-in db [:jackpot :amount] amount)))

(reg-event-fx
  :contract/on-sponsor-updated
  interceptors
  (fn [_ [{:keys [sponsor-address name amount]}]]
    {:dispatch [:contract/sponsor-loaded [sponsor-address [name amount]]]}))

(reg-event-db
  :contract/on-sponsorship-added
  interceptors
  (fn [db [{:keys [jackpot-key sponsor-address name amount fee date sponsors-count sponsorships-count
                   total-sponsorships-amount]} {:keys [transaction-hash]}]]
    (let [new-sponsor-tx-hash? (= transaction-hash (get-in db [:new-sponsor :transaction-hash]))
          sponsorship-key (dec sponsorships-count)]
      (cond-> db
        true
        (update-in [:contract :stats] merge {:sponsors-count (.toNumber sponsors-count)
                                             :sponsorships-count (.toNumber sponsorships-count)
                                             :total-sponsorships-amount total-sponsorships-amount})
        true
        (assoc-in [:sponsorships sponsorship-key] {:jackpot-key (.toNumber jackpot-key)
                                                   :address sponsor-address
                                                   :name name
                                                   :amount amount
                                                   :fee fee
                                                   :date (u/big-number->date-time date)
                                                   :sponsorship-key sponsorship-key})
        new-sponsor-tx-hash?
        (open-snackbar "Your sponsoring is now visible on website!" :stuck-out-tongue-closed-eyes)

        new-sponsor-tx-hash?
        (update :new-sponsor merge {:transaction-hash nil :sending? false})))))

(reg-event-fx
  :contract/on-top-sponsor-added
  interceptors
  (fn [{:keys [db]} [{:keys [sponsor-address name amount]}]]
    {:db (-> db
           (update :top-sponsors-addresses conj sponsor-address)
           (update :top-sponsors-addresses set))
     :dispatch [:contract/sponsor-loaded sponsor-address [name amount]]}))

(reg-event-db
  :contract/on-top-sponsor-removed
  interceptors
  (fn [db [{:keys [sponsor-address]}]]
    (update db :top-sponsors-addresses (partial remove #(= % sponsor-address)))))

(reg-event-fx
  :contract/load-source-code
  interceptors
  (fn [{:keys [db]}]
    {:db (assoc-in db [:contract :code-loading?] true)
     :http-xhrio {:method :get
                  :uri (gstring/format "/contracts/src/%s.sol"
                                       (cs/->camelCase (get-in default-db [:contract :name])))
                  :timeout 15000
                  :response-format (ajax/text-response-format)
                  :on-success [:contract/source-code-loaded]
                  :on-failure [:contract/source-code-load-error]}}))

(reg-event-db
  :contract/sponsor-loaded
  interceptors
  (fn [db [sponsor-address [name amount]]]
    (assoc-in db [:sponsors sponsor-address] {:address sponsor-address
                                              :name name
                                              :amount amount})))

(reg-event-fx
  :contract/on-jackpot-won-latest
  interceptors
  (fn [{:keys [db]} [{:keys [player-address roll date amount jackpot-key total-jackpot-amounts-won] :as result}]]
    {:db (cond-> db
           true
           (assoc-in [:contract :stats :total-jackpot-amounts-won] total-jackpot-amounts-won)

           (my-address? db player-address)
           (assoc :winning-guess {:address player-address
                                  :roll (.toNumber roll)
                                  :date (u/big-number->date-time date)
                                  :amount amount
                                  :jackpot-key (.toNumber jackpot-key)}))
     :dispatch [:contract/on-jackpot-won-history result]}))

(reg-event-db
  :contract/on-jackpot-won-history
  interceptors
  (fn [db [{:keys [player-address roll date amount jackpot-key]}]]
    (let [jackpot-key (.toNumber jackpot-key)]
      (assoc-in db [:winnings jackpot-key] {:address player-address
                                            :roll (.toNumber roll)
                                            :date (u/big-number->date-time date)
                                            :amount amount
                                            :jackpot-key jackpot-key}))))

(reg-event-db
  :contract/source-code-loaded
  interceptors
  (fn [db [code]]
    (-> db
      (assoc-in [:contract :code] code)
      (assoc-in [:contract :code-loading?] false))))

(reg-event-fx
  :contract/on-player-loaded
  interceptors
  (fn [_ [address [credit]]]
    {:dispatch [:contract/on-player-credit-change (zipmap [:player-address :credit] [address credit])]}))

(reg-event-db
  :contract/on-player-credit-change
  interceptors
  (fn [db [{:keys [player-address credit]}]]
    (assoc-in db [:accounts player-address :credit] credit)))

(reg-event-db
  :contract/on-state-change
  interceptors
  (fn [db [{:keys [state]}]]
    (assoc-in db [:contract :state] (u/big-number->state state))))

(reg-event-db
  :contract/on-new-player
  interceptors
  (fn [db [{:keys [player-address date players-count]}]]
    (assoc-in db [:contract :stats :players-count] (.toNumber players-count))))

(reg-event-db
  :contract/on-bet
  interceptors
  (fn [db [{:keys [bets-count total-guesses-count]}]]
    (update-in db [:contract :stats] merge {:bets-count (.toNumber bets-count)
                                            :total-guesses-count (.toNumber total-guesses-count)})))

(reg-event-db
  :contract/on-rolled
  interceptors
  (fn [db [{:keys [player-address guesses rolls date bet-key]} {:keys [block-number transaction-hash]}]]
    (let [bet-key (.toNumber bet-key)
          guesses (mapv #(.toNumber %) guesses)
          rolls (mapv #(.toNumber %) rolls)
          {:keys [new-bet]} db
          rolls-ok? (every? #(< 0 %) rolls)
          on-rolled-new-bet? (and (:rolling? new-bet)
                                  (= player-address (:address new-bet))
                                  (= guesses (:guesses new-bet)))]
      (cond-> db
        true
        (assoc-in [:bets bet-key] {:address player-address
                                   :guesses guesses
                                   :rolls rolls
                                   :date (u/big-number->date-time date)
                                   :bet-key bet-key
                                   :block-number block-number})

        on-rolled-new-bet?
        (update :new-bet merge {:rolls rolls
                                :rolling? false
                                :transaction-hash nil
                                :bet-processed? false})

        (and on-rolled-new-bet? rolls-ok?)
        (open-snackbar "Winning emojis are ready!" :smiley-cat)

        (and on-rolled-new-bet? (not rolls-ok?))
        (open-snackbar "Hmm, winning emojis we got looks invalid. We refunded you for this bet, please try again!"
                       :crying-cat-face)))))

(reg-event-fx
  :contract/bet-response
  interceptors
  (fn [{:keys [db]} [transaction-hash]]
    (console :log "Bet response " transaction-hash)
    {:db (-> db
           (update :new-bet merge {:rolling? true :transaction-hash transaction-hash})
           (open-snackbar "Your bet has been sent, now let's wait..." :relieved))
     :ga/event [:bet :contract/bet-response transaction-hash]}))

(reg-event-fx
  :contract/bet-transaction-receipt
  interceptors
  (fn [{:keys [db]} [receipt]]
    {:db (let [bet-processed? (get-in db [:new-bet :bet-processed?])]
           (cond-> db
             (not bet-processed?)
             (assoc-in [:new-bet :bet-processed?] true)

             (not bet-processed?)
             (open-snackbar "Bet transaction has been successfull, few more moments until we get winning emojis"
                              :kissing-smiling-eyes)))
     :ga/event [:bet :contract/bet-transaction-receipt (:transaction-hash receipt)]}))

;; ------------ Contract Errors ------------

(reg-event-fx
  :contract/compiled-code-load-error
  interceptors
  (fn [{:keys [db]} [result]]
    (console :error result)
    {:db (open-snackbar db "Sorry, I couldn't load smart contract code" :fearful)
     :ga/event [:compiled-code-load-error result]}))

(reg-event-fx
  :contract/source-code-load-error
  interceptors
  (fn [db [result]]
    (console :error result)
    {:db (open-snackbar db "Sorry, I couldn't load smart contract source code" :open-mouth)
     :ga/event [:source-code-load-error result]}))

(reg-event-db
  :contract/on-error
  interceptors
  (fn [db [error]]
    (console :error error)
    (cond-> db
      (not (get-in db [:snackbar :open?]))
      (open-snackbar "Sorry, but I have some trouble getting data from smart contract" :head-bandage))))

(reg-event-fx
  :contract/bet-error
  interceptors
  (fn [{:keys [db]} [error]]
    (console :error error)
    {:db (-> db
           (open-snackbar "Oh snap! Something went wrong with your bet" :cry)
           (update :new-bet merge {:rolling? false :transaction-hash nil :bet-processed? false}))
     :ga/event [:bet :contract/bet-error error]}))

;; ------------ Blockchain ------------

(defn- address->event-id [event-name address]
  (str event-name "-" address))

(defn- address->on-rolled-event [address]
  [(address->event-id :on-rolled address) :on-rolled {:player-address address}
   {:from-block 0 :to-block "latest"} :contract/on-rolled :contract/on-error])

(reg-event-fx
  :blockchain/my-addresses-loaded
  interceptors
  (fn [{:keys [db]} [addresses]]
    (let [addresses-map (reduce #(assoc %1 %2 {:address %2}) {} addresses)]
      (merge
        {:db (-> db
               (assoc :my-addresses addresses)
               (assoc-in [:new-bet :address] (first addresses))
               (assoc-in [:new-sponsor :address] (first addresses))
               (update :accounts merge addresses-map))
         :web3-fx.blockchain/balances
         {:web3 (:web3 db)
          :watch? true
          :blockchain-filter-opts "latest"
          :db-path [:blockchain :balances]
          :addresses addresses
          :dispatches [:blockchain/balance-loaded :blockchain/on-error]}}
        (when-not (empty? addresses)
          (let [instance (get-in db [:contract :instance])
                on-rolled-events (map address->on-rolled-event addresses)]
            {:web3-fx.contract/events
             {:db db
              :instance instance
              :db-path [:contract :events]
              :events on-rolled-events}
             :web3-fx.contract/constant-fns
             {:instance instance
              :fns (map #(vec [:players % [:contract/on-player-loaded %] :contract/on-error]) addresses)}}))))))

(reg-event-db
  :blockchain/balance-loaded
  interceptors
  (fn [db [balance address]]
    (assoc-in db [:accounts address :balance] balance)))

(reg-event-fx
  :blockchain/block-number-loaded
  interceptors
  (fn [{:keys [db]} [block-number]]
    {:db (assoc-in db [:blockchain :initial-block-number] block-number)
     :web3-fx.contract/events
     {:db db
      :instance (get-in db [:contract :instance])
      :db-path [:contract :events]
      :events [[:on-jackpot-won-history :on-jackpot-won {} {:from-block 0 :to-block block-number}
                :contract/on-jackpot-won-history :contract/on-error]]}}))

(reg-event-fx
  :blockchain/transaction-receipt-loaded
  interceptors
  (fn [_ [gas-limit on-success on-out-of-gas {:keys [gas-used] :as receipt}]]
    (let [gas-used-percent (* (/ gas-used gas-limit) 100)]
      (console :log (gstring/format "%.2f%" gas-used-percent) "gas used:" gas-used)
      (if (<= gas-limit gas-used)
        {:dispatch [on-out-of-gas receipt]}
        {:dispatch [on-success receipt]}))))

(reg-event-fx
  :blockchain/unlock-account-response
  interceptors
  (fn [_ [address]]
    {}))

;; ------------ Blockchain Errors ------------

(reg-event-fx
  :blockchain/unlock-account-error
  interceptors
  (fn [_ [address error]]
    (console :error "Could not unlock address " address " " error)
    {}))

(reg-event-fx
  :blockchain/on-error
  interceptors
  (fn [{:keys [db]} [error]]
    (console :error error)
    {:db (cond-> db
           (not (get-in db [:snackbar :open?]))
           (open-snackbar "Sorry, but I have some trouble getting data from a blockchain" :thermometer-face))
     :ga/event [:on-error :blockchain error]}))

;; ------------ New Bet ------------

(reg-event-db
  :new-bet/add-guess
  interceptors
  (fn [db [guess]]
    (-> db
      (update-in [:new-bet :guesses] conj guess)
      (update-in [:new-bet :rolls] conj nil))))

(reg-event-db
  :new-bet/remove-guess-from-bet
  interceptors
  (fn [db [index]]
    (-> db
      (update-in [:new-bet :guesses] u/remove-at index)
      (update-in [:new-bet :rolls] u/remove-at index))))

(reg-event-db
  :new-bet/remove-all-guesses
  interceptors
  (fn [db _]
    (update db :new-bet merge {:guesses []
                               :rolls []})))

(reg-event-db
  :new-bet/select-address
  interceptors
  (fn [db [address]]
    (assoc-in db [:new-bet :address] address)))

(reg-event-db
  :winning-guess/remove
  interceptors
  (fn [db]
    (assoc db :winning-guess nil)))

(reg-event-fx
  :new-bet/bet
  interceptors
  (fn [{:keys [db]} [guesses address wallet-charge]]
    (let [gas-limit (get-in db [:gas-limits :bet])]
      {:db (-> db
             (assoc :winning-guess nil)
             (update-in [:new-bet :rolls] (partial map (constantly nil))))
       :web3-fx.contract/state-fn
       {:instance (get-in db [:contract :instance])
        :web3 (:web3 db)
        :db-path [:contract :transaction-receipt-filter]
        :fn [:bet
             guesses
             {:gas gas-limit
              :from address
              :value wallet-charge}
             :contract/bet-response
             :contract/bet-error
             [:blockchain/transaction-receipt-loaded gas-limit :contract/bet-transaction-receipt :contract/bet-error]]}})))


;; ------------ New Sponsor ------------

(reg-event-db
  :new-sponsor/update
  interceptors
  (fn [db [key value]]
    (assoc-in db [:new-sponsor key] value)))

(reg-event-fx
  :new-sponsor/sponsor
  interceptors
  (fn [{:keys [db]} [address amount name]]
    (let [{:keys [web3 contract]} db
          gas-limit (get-in db [:gas-limits :sponsor])]
      {:web3-fx.contract/state-fn
       {:instance (:instance contract)
        :web3 web3
        :db-path [:contract :transaction-receipt-filter]
        :fn [:sponsor
             name
             {:gas gas-limit
              :from address
              :value (web3/to-wei amount :ether)}
             :contract/sponsor-response
             :contract/sponsor-error
             [:blockchain/transaction-receipt-loaded gas-limit
              :contract/sponsor-transaction-receipt :contract/sponsor-error]]}})))

(reg-event-fx
  :contract/sponsor-response
  interceptors
  (fn [{:keys [db]} [transaction-hash]]
    {:db (-> db
           (open-snackbar "Thank you very much! Your sponsoring will be processed in a while" :hugging)
           (update :new-sponsor merge {:sending? true :transaction-hash transaction-hash}))
     :ga/event [:sponsor :contract/sponsor-response transaction-hash]}))

(reg-event-fx
  :contract/sponsor-transaction-receipt
  interceptors
  (fn [{:keys [network] :as db} [{:keys [transaction-hash]}]]
    {:ga/event [:sponsor :contract/sponsor-transaction-receipt transaction-hash]}))

(reg-event-fx
  :contract/sponsor-error
  interceptors
  (fn [{:keys [db]} [error]]
    (console :error error)
    {:db (-> db
           (update :new-sponsor merge {:sending? false :transaction-hash nil})
           (open-snackbar "Oh no! Seems like your sponsoring transaction didn't go well" :crying-cat-face))
     :ga/event [:sponsor :contract/sponsor-error error]}))

;; ------------ Player Profile ------------

(reg-event-fx
  :player-profile/withdraw
  interceptors
  (fn [{:keys [db]}]
    (let [address (get-in db [:current-page :route-params :address])
          gas-limit (get-in db [:gas-limits :withdraw])]
      {:web3-fx.contract/state-fn
       {:instance (get-in db [:contract :instance])
        :web3 (:web3 db)
        :db-path [:contract :transaction-receipt-filter]
        :fn [:withdraw
             {:gas gas-limit
              :from address}
             :contract/withdraw-response
             :contract/withdraw-error
             [:blockchain/transaction-receipt-loaded gas-limit
              :contract/withdraw-transaction-receipt :contract/withdraw-error]]}})))

(reg-event-fx
  :contract/withdraw-response
  interceptors
  (fn [{:keys [db]} [transaction-hash]]
    {:db (-> db
           (update :withdraw merge {:sending? true :transaction-hash transaction-hash})
           (open-snackbar "Your cash out request will be processed shortly!" :smile-cat))
     :ga/event [:withdraw :contract/withdraw-response transaction-hash]}))

(reg-event-fx
  :contract/withdraw-transaction-receipt
  interceptors
  (fn [{:keys [db]} [receipt]]
    {:db (-> db
           (update :withdraw merge {:transaction-hash nil :sending? false})
           (open-snackbar "Cash out has been done! Enjoy your new money!" :money-mouth))
     :ga/event [:withdraw :contract/withdraw-transaction-receipt (:transaction-hash receipt)]}))

(reg-event-fx
  :contract/withdraw-error
  interceptors
  (fn [{:keys [db]} [error]]
    (console :error error)
    {:db (-> db
           (update :withdraw merge {:transaction-hash nil :sending? false})
           (open-snackbar "Ehm, sorry, but something went wrong while sending you money" :cold-sweat))
     :ga/event [:withdraw :contract/withdraw-error error]}))


(reg-event-fx
  :player-profile/initiate-load
  interceptors
  (fn [{:keys [db]}]
    (let [instance (get-in db [:contract :instance])
          address (get-in db [:current-page :route-params :address])]
      (when-not (my-address? db address)
        {:web3-fx.contract/events
         {:db db
          :instance instance
          :db-path [:contract :events]
          :events [(address->on-rolled-event address)
                   [(address->event-id :on-sponsorship-added address) :on-sponsorship-added
                    {:sponsor-address address} {:from-block 0} :contract/on-sponsorship-added :contract/on-error]]}
         :web3-fx.contract/constant-fns
         {:instance instance
          :fns [[:players address [:contract/on-player-loaded address] :contract/on-error]]}
         :web3-fx.blockchain/balances
         {:web3 (:web3 db)
          :addresses [address]
          :dispatches [:blockchain/balance-loaded :blockchain/on-error]}}))))

(reg-event-fx
  :player-profile/stop-watching
  interceptors
  (fn [{:keys [db]}]
    (let [address (get-in db [:current-page :route-params :address])]
      (when-not (my-address? db address)
        {:web3-fx.contract/events-stop-watching
         {:db db
          :db-path [:contract :events]
          :event-ids [(address->event-id :on-rolled address)
                      (address->event-id :on-sponsorship-added address)]}}))))

;; ------------ Other ------------

(reg-event-db
  :drawer/toggle
  interceptors
  (fn [db _]
    (update db :drawer-open? not)))

(reg-event-db
  :snackbar/close
  interceptors
  (fn [db _]
    (assoc-in db [:snackbar :open?] false)))

(reg-event-fx
  :set-current-page
  interceptors
  (fn [{:keys [db]} [match]]
    {:db (assoc db :current-page match
                   :drawer-open? false)
     :ga/page-view [(apply u/path-for (:handler match) (flatten (into [] (:route-params match))))]}))

(reg-event-db
  :select-emoji
  interceptors
  (fn [db [index emoji-key]]
    #_(print.foo/look (dec (count (:selectable-emojis db))))
    #_(update db :selectable-emojis u/remove-at index)
    (assoc-in db [:emoji-select-form :selected-emoji] {:index index
                                                       :emoji-key emoji-key})))

(reg-event-fx
  :load-conversion-rate
  interceptors
  (fn [_ [currency]]
    {:http-xhrio {:method :get
                  :uri (gstring/format "https://api.cryptonator.com/api/ticker/eth-%s" (name currency))
                  :timeout 10000
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success [:conversion-rate-loaded currency]
                  :on-failure [:conversion-rate-load-error currency]}}))

(reg-event-db
  :conversion-rate-loaded
  interceptors
  (fn [db [currency response]]
    (assoc-in db [:conversion-rates currency] (-> response :ticker :price js/parseFloat))))

(reg-event-fx
  :conversion-rate-load-error
  interceptors
  (fn [_ [currency error]]
    (console :error "Could not load rate for " currency error)
    {:ga/event [:conversion-rate-load-error currency error]}))