(ns emojillionaire.db
  (:require
    [cljs.spec :as s]
    [emojillionaire.emojis :refer [emojis]]
    [web3-cljs.core :as wb]))

(def default-db
  {:web3 (or (aget js/window "web3")
             (wb/create-web3 "http://localhost:8545/"))
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
              ;:address "0x5116522fb0d50c8b9f00dcf9b8dd1d3a84124d03"
              :address "0xe0ab37305a28f35c8e6bc4635c1327920af02e99"
              }
   :dev-accounts {:privnet [["0x97f755acF6e7C4daA064BEF5c6740C12d56843Ce" "matusles"]
                            ["0x7c7c3E38779E2407aD4daF1fe339635cccF34E87" "m"]
                            ["0x8F8C79b5dDdEb431682104423271AAa8fe06457e" "m"]
                            ["0x760807EA2E82cd97958c1a498774711e909b9CF3" "m"]]
                  :testnet [["0x4c155e9c387e3ad41fafb3f4ea5b959f65a88c5f" "matusles"]]
                  }
   :blockchain {}
   :gas-limits {:bet 700000
                :sponsor 100000
                :withdraw 50000}
   :new-bet {:guesses []
             :rolls []
             :address nil
             :rolling? false
             :transaction-hash nil
             :bet-processed? false}
   :emoji-select-form {:selected-emoji {:index 0 :emoji-key ":100:"}}
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

