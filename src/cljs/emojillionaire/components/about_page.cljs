(ns emojillionaire.components.about-page
  (:require
    [cljs-react-material-ui.reagent :as ui]
    [emojillionaire.components.emoji :refer [emoji]]
    [emojillionaire.components.layout :refer [outer-paper row col headline]]
    [emojillionaire.utils :as u]))


(defn about-page []
  [outer-paper
   [headline "About" :smiley-cat] [:br]
   [:h3 "Emojillionaire is " [u/new-window-link "https://www.ethereum.org/" "Ethereum"] " based lottery with
    100% provable fairness, transparency and security."]
   [:h3 [:a {:href (u/path-for :code)} "Smart contract"] " of emojillionaire is build in a way, that nobody, not
   even author himself, can never obtain jackpot money unless genuinely winning it."]
   [:h3 "To achieve this goal the contract has several notable features: "]
   [:ul
    [:li "Rules of the game are as simple as possible, to avoid potential bugs"]
    [:li "Contract code is never meant to be updated"]
    [:li "Although bet cost is adjustable by the author, it can be changed only to more expensive,
    so the author can't \"secretely\" set cost to 0 and quickly place many bets untill winning jackpot"]
    [:li "Number of emojis to choose from can also be adjusted only to bigger number for similar reasons as above"]
    [:li "To obtain truly random numbers the contract uses "
     [u/new-window-link "http://www.oraclize.it/" "Oraclize"] " to get numbers from "
     [u/new-window-link "https://www.random.org/" "random.org"]]
    [:li "In case of emergency, the contract has function to refund everyone, meaning that every player or sponsor
     will get back money they spent on current jackpot (minus fees)"]]])