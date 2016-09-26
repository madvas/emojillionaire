(ns emojillionaire.components.code-page
  (:require
    [cljs-react-material-ui.icons :as icons]
    [cljs-react-material-ui.reagent :as ui]
    [emojillionaire.components.emoji :refer [emoji]]
    [emojillionaire.components.layout :refer [headline row col outer-paper]]
    [emojillionaire.utils :as u]
    [re-frame.core :refer [subscribe dispatch]]
    [reagent.core :as r]))

(def code-page
  (with-meta
    (fn []
      (let [contract (subscribe [:db/contract])
            contract-etherscan-url (subscribe [:contract/etherscan-url])]
        (fn []
          [outer-paper
           [headline "Code" :frog] [:br]
           [:h2 "Front End"]
           [:p "Code for front end part of emojillionaire is written in lovely "
            [u/new-window-link "http://clojurescript.org/" "ClojureScript"]
            " and it's fully open-sourced "
            [u/new-window-link "https://github.com/madvas/emojillionaire" "on Github"] "."]
           [:h2 "Back End"]
           [:p "Back end is running on decentralized newtwork " [u/new-window-link "https://ethereum.org/" "Ethereum"]
            " using " [u/new-window-link "https://medium.com/@ConsenSys/a-101-noob-intro-to-programming-smart-contracts-on-ethereum-695d15c1dab4#.wif58hq93"
                       "Smart Contract"] ", which guarantees 100% fairness and transparency of this lottery."]
           [:p [u/new-window-link @contract-etherscan-url "Open smart contract in Etherscan"]]
           [:h2 "Smart Contract Code"]
           [:pre {:style {:overflow-x :scroll}}
            [:code (:code @contract)]]])))
    {:component-will-mount
     (fn []
       (let [contract (subscribe [:db/contract])]
         (when (and (not (:code @contract))
                    (not (:code-loading? @contract)))
           (dispatch [:contract/load-source-code]))))}))
