(ns ^:figwheel-no-load emojillionaire.repl
  (:require [web3-cljs.core :as wb]
            [web3-cljs.utils :as wu]
            [web3-cljs.eth :as we]
            [ajax.core :refer [GET]]
            [medley.core :as medley]))


(comment
  (print.foo/look (:instance (:contract @re-frame.db/app-db)))

  (web3-cljs.utils/contract-call (:instance (:contract @re-frame.db/app-db)) :state)
  (web3-cljs.utils/contract-call (:instance (:contract @re-frame.db/app-db)) :bets)
  (web3-cljs.utils/contract-call (:instance (:contract @re-frame.db/app-db)) :bets-count)
  (web3-cljs.utils/contract-call (:instance (:contract @re-frame.db/app-db)) :get-bet)
  (web3-cljs.utils/contract-call (:instance (:contract @re-frame.db/app-db)) :sponsors 0 #(println %2))
  (web3-cljs.utils/contract-call (:instance (:contract @re-frame.db/app-db)) :sponsor-keys)
  (web3-cljs.utils/contract-call (:instance (:contract @re-frame.db/app-db)) :sponsorships 2)
  (web3-cljs.utils/contract-call (:instance (:contract @re-frame.db/app-db)) :sponsors-count)
  (web3-cljs.utils/contract-call (:instance (:contract @re-frame.db/app-db)) :top-sponsors-addresses)
  (web3-cljs.core/from-wei (web3-cljs.utils/contract-call (:instance (:contract @re-frame.db/app-db)) :get-oraclize-fee)
                           :ether)

  (.toNumber (-> @re-frame.db/app-db :contract :instance :config :oraclize-fee))
  (-> @re-frame.db/app-db :selectable-emojis)

  (web3-cljs.utils/contract-call (:instance (:contract @re-frame.db/app-db))
                                 :on-log-int {} {:from-block 0 :to-block "latest"} (fn [& args]
                                                                                     (println "on-log-int")
                                                                                     (print.foo/look args)))

  (web3-cljs.utils/contract-call (:instance (:contract @re-frame.db/app-db))
                                 :on-log-int-arr {} {:from-block 0 :to-block "latest"} (fn [& args]
                                                                                         (println "on-log-int-arr")
                                                                                         (print.foo/look args)))

  (web3-cljs.utils/contract-call (:instance (:contract @re-frame.db/app-db))
                                 :on-rolled {} {:from-block 0 :to-block "latest"} (fn [& args]
                                                                                    (println "on-rolled")
                                                                                    (print.foo/look args)))

  (web3-cljs.utils/contract-call (:instance (:contract @re-frame.db/app-db))
                                 :on-sponsor-updated
                                 {}
                                 {:from-block 0 :to-block "latest"}
                                 (fn [& args]
                                   (println "on-sponsor-updated")
                                   (print.foo/look args)))

  (web3-cljs.utils/contract-call (:instance (:contract @re-frame.db/app-db))
                                 :on-bet {:from-block 0 :to-block "latest"} (fn [& args]
                                                                              (println "on-bet")
                                                                              (print.foo/look args)))

  (web3-cljs.utils/contract-call (:instance (:contract @re-frame.db/app-db))
                                 :bet [1] {:from (:address (:new-bet @re-frame.db/app-db))
                                           :value 200000000000000000
                                           :gas 4712388}
                                 #(print.foo/look %&))

  (web3-cljs.utils/contract-call (:instance (:contract @re-frame.db/app-db))
                                 :sponsor "aaaa" {:from (first (:my-addresses @re-frame.db/app-db))
                                                  :value 2000000000000000000
                                                  :gas 4712388}
                                 #(print.foo/look %&))

  (web3-cljs.utils/contract-call (:instance (:contract @re-frame.db/app-db))
                                 :refund-everyone {:from (first (:my-addresses @re-frame.db/app-db))
                                                   :gas 4712388}
                                 #(print.foo/look %&))


  (let [{:keys [my-addresses contract web3]} @re-frame.db/app-db]
    (emojillionaire.utils/deploy-bin! web3 (:abi contract) (:bin contract) (first my-addresses) 4012388
                                   (fn [res]
                                     (print.foo/look res))
                                   (fn [Contract]
                                     (println "Contract address: " (aget Contract "address")))
                                   (fn [err]
                                     (.error js/console err))))

  (:sponsors @re-frame.db/app-db)

  )

;(def web3 (wb/create-web3 "https://madvas.by.ether.camp:8555/sandbox/4c9d460cfe"))
#_(.log js/console js/web3)

#_(def w3 (wb/create-web3 "http://localhost:8545/"))
#_(.-eth js/web3)
(def w3 js/web3)

(def contracts-path "/contracts/build/")

(def my-address "0x610cAcfCc86Fe4B7A6c99F7FA0f49Dd6725c6632")
#_(def my-address "0x4C155E9C387e3ad41fAfB3f4ea5B959F65A88C5f")
(def my-address-priv "adbddd4526c93c445028278ee71cfb7ad5d15478f852229b70421fbe288ac345")
(def tx-address "0xa4ae381145987f314cb42edd4540ceb6c2acf6ba6179d5d1c3588966c5eb182e")
(def contract-address "0x594af327b2e76027e96db26f62eb2fa3ea2d960a")
(defonce *contract-abi* (atom nil))
(defonce *contract-bin* (atom nil))
(defonce *contract-address* (atom nil))
(defonce *contract-instance* (atom nil))
(def devnet-miner "0x97f755acf6e7c4daa064bef5c6740c12d56843ce")
(def devnet-metamask "0xf46a340b45ce87C557c622Bd99Ac340Ae0e0A2d3")
(def devnet-mist "0x7c7c3E38779E2407aD4daF1fe339635cccF34E87")

#_(we/set-default-account! w3 my-address)

(def print-clb (fn [err res]
                 (println err)
                 (println res)))

(defn big-number->ether [x]
  (if (and x (aget x "toNumber"))
    (let [num (.toNumber x)]
      (if (> num 1000000)
        (str (wb/from-wei (.toNumber x) :ether) " eth")
        num))
    x))

(defn big-numbers->ether [coll]
  (medley/map-vals big-number->ether coll))

(defn balance-clb [err res]
  (when err
    (println err))
  (println (big-number->ether res)))

(def console-clb (fn [err res]
                   (.log js/console err)
                   (.log js/console res)))

(defn accounts-balances [web3 clb]
  (map #(we/get-balance web3 % (fn [err res]
                                 (clb err (big-number->ether res))))
       (we/accounts web3)))

(defn deploy-bin! [web3 abi bin & [from-addr]]
  (println "here " (we/accounts web3))
  (let [Contract (we/contract web3 abi)]
    (.new Contract #js {:from (or from-addr (last (we/accounts web3)))
                        :data bin
                        :gas 2100000}
          (fn [err res]
            (.log js/console res)
            (when err
              (println "Deploy error:" err))
            (if-let [address (aget res "address")]
              (do
                (println "Deployed contract at " address)
                (reset! *contract-address* address)
                (reset! *contract-instance* (we/contract-at web3 abi address)))
              (println "Sent contract"))))))

(defn fetch-contract!
  ([contract-name] (fetch-contract! contract-name (fn [& args])))
  ([contract-name clb]
   (GET (str contracts-path contract-name ".json?_=" (.getTime (new js/Date)))
        {:response-format :json
         :keywords? true
         :handler (fn [res]
                    (let [{:keys [abi bin]} (-> res :contracts :Emojillionaire)]
                      (reset! *contract-abi* (JSON.parse abi))
                      (reset! *contract-bin* bin)
                      (clb @*contract-abi* bin)))
         :error-handler #(println "Error fetching " %)})))



(defn listen-contract-event [event-name]
  (wu/contract-call @*contract-instance* event-name
                    (fn [err log]
                      (if err
                        (println event-name err)
                        (println (:event log) (big-numbers->ether (:args log)))))))

(defn get-jackpot []
  (wb/from-wei (.toNumber (wu/contract-call @*contract-instance* "jackpot")) :ether))

(defn get-eth-prop [prop-name]
  (big-number->ether (wu/contract-call @*contract-instance* prop-name)))

(defn deploy-contract! []
  (fetch-contract! "emojillionaire" #(deploy-bin! w3 %1 %2)))

(comment
  (wb/version-api w3)
  (.-eth w3)
  (.log js/console (Object.keys (aget w3 "personal")))
  (wb/connected? w3)

  (.clear js/console)
  (wp/unlock-account w3 devnet-miner "matusles", print-clb)
  (fetch-contract! "emojillionaire")

  (deploy-bin! w3 @*contract-abi* @*contract-bin*)
  (we/accounts w3)
  (accounts-balances w3 print-clb)
  (we/get-balance w3 devnet-miner balance-clb)

  (contract-call @*contract-instance* "numBets")
  (contract-call @*contract-instance* "isValidBetInput" 5)
  (deploy-contract!)
  (contract-call @*contract-instance* "getRandomOrgQuery" 9)
  (big-number->ether (contract-call @*contract-instance* "playerCredit" (first (we/accounts w3))))
  (big-number->ether (contract-call @*contract-instance* "betValue"))

  (get-jackpot)
  (do
    (listen-contract-event "onLogStr")
    (listen-contract-event "onLogInt")
    (listen-contract-event "onLogAddress")
    (listen-contract-event "onPlayerCreditChange")
    (listen-contract-event "onJackpotChange")
    (listen-contract-event "onJackpotWon")
    (listen-contract-event "onRolled")
    (listen-contract-event "onRuntimeError"))

  (.log js/console (we/contract-at w3 @*contract-abi* @*contract-address*))
  (.log js/console @*contract-instance*)
  (contract-call @*contract-instance* "bet" 106 {:gas 4712388
                                                 ;:value (wb/to-wei w3 200 :finney)
                                                 :value (.plus (contract-call @*contract-instance* "betValue")
                                                               (wb/to-big-number w3 (wb/to-wei 0.005 :ether)))
                                                 :from (last (we/accounts w3))} print-clb)

  (contract-call @*contract-instance* "logTest" {:gas 300000
                                                 :from (last (we/accounts w3))} print-clb)

  (.getData (.-bet @*contract-instance*) 5)
  (.getData (.-withdraw @*contract-instance*))

  (contract-call @*contract-instance* "withdraw" {:gas 500000
                                                  :from (last (we/accounts w3))} print-clb)



  (get-eth-prop "houseFee")
  (get-eth-prop "betValue")
  (get-eth-prop "jackpot")
  (contract-call @*contract-instance* "betsKeys")
  (contract-call @*contract-instance* "bets" (contract-call @*contract-instance* "betsKeys" 0))

  (big-number->ether (we/get-balance w3 @*contract-address*))
  (big-number->ether (we/get-balance w3 my-address))

  (def my-filter (we/filter w3 "pending"))
  (.watch my-filter (fn [err log]
                      (println "my-filter watch")
                      (println err log)))

  (.get my-filter (fn [err log]
                    (println "my-filter get")
                    (println err log)))



  (.stopWatching my-filter)

  (reset! *contract-instance* (we/contract-at w3 @*contract-abi* @*contract-address*))

  (let [events (contract-call @*contract-instance* "allEvents" {} (fn [err log]
                                                                    (println "Contract Event!")
                                                                    (println err)
                                                                    (println log)))]
    (.get events (fn [err logs]
                   (println "Events Get: ")
                   (println err)
                   (println logs))))

  #_(contract-call w3 "multiply" src1 @*contract-address* 5 {:gas 300000
                                                             :value 100})

  #_(contract-call w3 "someMethod" contract-abi contract-address)
  #_(contract-call w3 "update" contract-abi contract-address)

  #_(contract-call w3 "onMultiply" src1 @*contract-address* (fn [err res]
                                                              (println "OMG IT WORKS")
                                                              (println err)
                                                              (println res)))
  #_(let [ContrInstance (we/contract-at w3 (code->abi w3 src1) @*contract-address*)]
      (.multiply ContrInstance 5 #js{:gas 300000
                                     :value 100}))

  (.log js/console w3)
  (wb/connected? w3)
  (we/block-number w3 print-clb)
  (we/accounts w3 print-clb)
  (we/get-balance w3 my-address print-clb)
  (accounts-balances w3 console-clb)
  (we/get-storage-at w3 @*contract-address*)
  (we/get-code w3 @*contract-address*)
  (we/get-code w3 my-address)

  (def Emojillionaire (we/contract-at w3 contract-abi contract-address))
  (.onSomeMethod Emojillionaire (fn [err res]
                              (println "onSomeMethod!!")))
  (.someMethod Emojillionaire #js {"value" 10 "gas" 2000})



  (.log js/console w3)
  (wb/version-api w3)
  (wb/version-network w3 print-clb)
  (wb/version-ethereum w3 print-clb)
  (wb/version-node w3 print-clb)
  (wb/version-whisper w3 print-clb)
  (wb/connected? w3)
  (.log js/console (wb/current-provider w3))
  (wb/sha3 (wb/sha3 "Some string to be hashed"))
  (wb/to-ascii "0x657468657265756d")
  (wb/from-ascii "ethereum" 32)
  (wb/to-decimal "0x15")
  (wb/from-wei 21000000000000 :finney)
  (wb/to-wei 0.5 :ether)
  (wb/to-big-number "200000000000000000000001")
  (wb/address? my-address)
  (wn/listening? w3)
  (wn/listening? w3 print-clb)
  (wn/peer-count w3)
  (wn/peer-count w3 print-clb)
  (we/default-account w3)
  (we/set-default-account! w3 my-address)
  (we/default-block w3)
  (we/syncing w3)
  (we/syncing w3 print-clb)
  (we/syncing? w3 print-clb)
  (we/coinbase w3 print-clb)
  (we/coinbase w3)
  (we/mining? w3 print-clb)
  (we/hashrate w3 print-clb)
  (big-number->ether (we/gas-price w3))
  (we/block-number w3 print-clb)
  (we/accounts w3 print-clb)
  (we/get-balance w3 my-address "latest" print-clb)
  (big-number->ether (we/get-balance w3 my-address))
  (wb/from-wei (.toNumber (we/get-balance w3 my-address)) :ether)
  (we/get-block w3 (we/block-number w3) true print-clb)
  (we/get-block w3 (we/block-number w3))
  (we/get-block-transaction-count w3 (we/block-number w3) print-clb)
  (we/get-uncle w3 (we/block-number w3) 0)
  (we/get-transaction-from-block w3 "pending" 0 print-clb)
  (we/get-transaction-count w3 my-address)
  (we/get-transaction w3 tx-address)
  (we/block-number w3 print-clb)
  (we/get-transaction-receipt w3 tx-address)
  (let [code "0x603d80600c6000396000f3007c01000000000000000000000000000000000000000000000000000000006000350463c6888fa18114602d57005b6007600435028060005260206000f3"]
    (we/send-transaction! w3 {:data code
                              :from my-address} print-clb))
  (we/sign w3 "0x135a7de83802408321b74c322f8558db1679ac20" "0x9dd2c369a187b4e6b9c402f030e50743e619301ea62aa4c0737d4ef7e10a3d49")
  (we/call! w3 {:data "0xc6888fa10000000000000000000000000000000000000000000000000000000000000003"
                :to my-address} print-clb)

  (we/estimate-gas w3
                   {:to my-address
                    :data "0xc6888fa10000000000000000000000000000000000000000000000000000000000000003"}
                   print-clb)

  (def fltr (we/filter w3 "latest"))
  (.get fltr print-clb)
  (.watch fltr (fn [& args]
                 (println "Watching " args)))
  (.stopWatching fltr)

  (def my-contract (we/contract w3 some-abi))
  (def my-contr-inst (.at my-contract "0xc4abd0339eb8d57087278718986382264244252f"))
  (.myConstantMethod my-contr-inst "myParam")
  (.myStateChangingMethod my-contr-inst "someParam1" 23 (clj->js {:value 200 :gas 2000}))
  (.myEvent my-contr-inst #js {"a" 5} print-clb)

  (we/get-compilers w3)
  (we/compile-solidity w3 src1)

  (we/namereg w3)

  (wd/put-string! w3 "myDB" "abc" "def")
  (wd/get-string w3 "myDB" "abc")

  )
