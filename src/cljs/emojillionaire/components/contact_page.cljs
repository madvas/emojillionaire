(ns emojillionaire.components.contact-page
  (:require
    [cljs-react-material-ui.reagent :as ui]
    [emojillionaire.components.emoji :refer [emoji]]
    [emojillionaire.components.layout :refer [outer-paper row col headline]]
    [emojillionaire.utils :as u]))

(defn contact-page []
  [outer-paper
   [headline "Contact" :love-letter] [:br]
   [:h3 "If you found a bug, please try to open issue on "
    [u/new-window-link "https://github.com/madvas/emojillionaire" "Github"]
    ", or you can contact me on Twitter " [u/new-window-link "https://twitter.com/matuslestan" "@matuslestan"]]])
