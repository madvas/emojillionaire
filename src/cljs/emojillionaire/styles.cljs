(ns emojillionaire.styles)

(def main-grid {:margin-top 20})

(def paper-base {:padding 20
                 :margin-top 10
                 :margin-bottom 10})


(def jackpot-paper (merge paper-base
                          {:text-align :center}))

(def jackpot-text {:font-size "3em"
                   :line-height "1.4em"})

(def welcome-text {:margin-bottom 10
                   :margin-top 10})

(def emoji-select-form-wrap {:height 250
                             :overflow :scroll})

(def selectable-emoji {:padding-top 5
                       :padding-bottom 5
                       :border-radius 10
                       :cursor :pointer})

(def selected-emoji-preview {:margin-bottom 30
                             :margin-top 10})

(def add-selected-emoji-btn {:margin-bottom 10})

(defn vertical-margin [x]
  {:margin-top x
   :margin-bottom x})

(def clickable {:cursor :pointer})

(def table-narrow-col {:width 40})

(def text-center {:text-align :center})
(def text-right {:text-align :right})
(def text-left {:text-align :left})
(def grey-text {:color "#9e9e9e"})

(def new-bet-summary (merge text-right
                            {:line-height "1.4em"}))

(def bet-btns-wrap (merge text-right {:margin-top 30}))

(def full-width {:width "100%"})

(def congrats-title {:text-align :center
                     :font-weight 300
                     :font-size "3em"
                     :line-height "1.1em"
                     :padding-top 20
                     :padding-bottom 20})

(def winning-amount {:font-size "1.8em"
                     :font-weight 300
                     :line-height "2em"
                     :margin-bottom 30})

(def table-emoji {:width 25})

(def multi-emoji-row-col {:white-space :normal
                          :padding-top 5
                          :padding-bottom 5})

(def ellipsis {:text-overflow :ellipsis
               :overflow :auto})

(def main-logo {:margin-left 10
                :height 47
                })

(def eth-logo {:margin-top -1
               :margin-right 10
               :height 50})