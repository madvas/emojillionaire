(ns emojillionaire.db
  (:require
    [cljs-web3.core :as web3]
    [cljs.spec :as s]
    [cljsjs.bignumber]
    [emojillionaire.emojis :refer [emojis]]))

(def default-db
  {:web3 (or (aget js/window "web3")
             (if goog.DEBUG
               (web3/create-web3 "http://localhost:8545/")
               (web3/create-web3 "https://morden.infura.io/metamask"))) ; Let's borrow this ;) Thanks MetaMask guys!
   :provides-web3? (or (aget js/window "web3") goog.DEBUG)
   :drawer-open? false
   :snackbar {:open? false
              :message ""
              :auto-hide-duration 10000}
   :network :testnet
   ;:network :privnet
   :conversion-rates {}
   :contract {:state :active
              :stats {}
              :config {:oraclize-fee (js/BigNumber. 7029368029739770)}
              :name "Emojillionaire"
              :code nil
              :code-loading? false
              :abi nil
              :bin nil
              :instance nil
              :address "0x01e29a1db0f4a7b522a93458e002392a7c49e8ce" ; testnet
              }
   :dev-accounts {:privnet [["0x6fce64667819c82a8bcbb78e294d7b444d2e1a29" "m"]
                            ["0xe206f52728e2c1e23de7d42d233f39ac2e748977" "m"]
                            ["0x522f9c6b122f4ca8067eb5459c10d03a35798ed9" "m"]
                            ["0xc5aa141d3822c3368df69bfd93ef2b13d1c59aec" "m"]]}
   :blockchain {}
   :gas-limits {:bet 650000
                :sponsor 500000
                :withdraw 50000}
   :new-bet {:guesses []
             :rolls []
             :address nil
             :rolling? false
             :transaction-hash nil
             :bet-processed? false}
   :emoji-select-form {:selected-emoji {:index 1 :emoji-key ":100:"}}
   :selectable-emojis emojis
   :accounts {}
   :my-addresses []
   :jackpot {}
   :bets {}
   :sponsors {}
   :sponsorships {}
   :top-sponsors-addresses []

   :new-sponsor {:amount 1
                 :name ""
                 :sending? false
                 :transaction-hash nil}
   :withdraw {:sending? false
              :transaction-hash nil}

   :winning-guess nil

   :winnings {}
   })

