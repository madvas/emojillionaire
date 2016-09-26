(ns emojillionaire.components.winnings-table
  (:require
    [cljs-react-material-ui.reagent :as ui]
    [emojillionaire.components.emoji :refer [emoji]]
    [emojillionaire.styles :as st]
    [emojillionaire.utils :as u]
    ))

(defn winnings-table [winnings {:keys [no-items-text no-address-col?]}]
  [ui/table {:selectable false}
   [ui/table-header {:adjust-for-checkbox false :display-select-all false}
    [ui/table-row
     (when-not no-address-col?
       [ui/table-header-column "Address"])
     [ui/table-header-column "Emoji"]
     [ui/table-header-column "Amount"]
     [ui/table-header-column "Date"]]]
   [ui/table-body {:display-row-checkbox false}
    (if (seq winnings)
      (doall
        (for [{:keys [address roll amount date jackpot-key]} winnings]
          [ui/table-row {:key jackpot-key :selectable false}
           (when-not no-address-col?
             [ui/table-row-column
              [:a {:href (u/path-for :player-profile :address address)}
               (u/truncate address 25)]])
           [ui/table-row-column [emoji roll (:width st/table-emoji)]]
           [ui/table-row-column (u/eth amount)]
           [ui/table-row-column (u/format-date date)]]))
      [ui/table-row
       [ui/table-row-column
        {:col-span 4 :style st/text-center}
        no-items-text]])]])
