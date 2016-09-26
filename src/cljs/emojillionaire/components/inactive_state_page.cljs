(ns emojillionaire.components.inactive-state-page
  (:require
    [cljs-react-material-ui.reagent :as ui]
    [emojillionaire.components.emoji :refer [emoji]]
    [emojillionaire.components.layout :refer [outer-paper row col]]
    [emojillionaire.utils :as u]))


(defn inactive-state-page []
  [outer-paper
   [row {:middle "xs" :center "xs"}
    [col {:xs 12}
     [:h1 {:style {:font-size 40
                   :padding-top 30
                   :padding-bottom 30}} "Oh Snap!"]]
    [col {:xs 12}
     [emoji :thermometer-face 200] [:br] [:br]]
    [col {:xs 12}
     [:h1 "Emojillionaire smart contract is currently out of order." [:br] [:br]
      "Sure we will be back online soon!"] [:br]]]])
