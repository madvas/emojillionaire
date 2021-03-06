(ns emojillionaire.core
  (:require
    [bidi.bidi :as bidi]
    [cljs-time.extend]
    [cljs.spec :as s]
    [cljsjs.bignumber]
    [cljsjs.emojione]
    [cljsjs.material-ui]
    [cljsjs.react-flexbox-grid]
    [cljsjs.web3]
    [emojillionaire.components.main-panel :refer [main-panel]]
    [emojillionaire.events]
    [emojillionaire.routes :refer [routes]]
    [emojillionaire.subs]
    [goog.string.format]
    [madvas.re-frame.google-analytics-fx :as google-analytics-fx]
    [print.foo :include-macros true]
    [pushy.core :as pushy]
    [re-frame.core :refer [dispatch dispatch-sync]]
    [reagent.core :as reagent]
    ))

(enable-console-print!)

(def history
  (pushy/pushy #(dispatch [:set-current-page %]) (partial bidi/match-route routes)))

(defn mount-root []
  (s/check-asserts goog.DEBUG)
  (google-analytics-fx/set-enabled! (not goog.DEBUG))
  #_(.clear js/console)
  (reagent/render [main-panel]
                  (.getElementById js/document "app")))

(defn ^:export init []
  (dispatch-sync [:initialize])
  (pushy/start! history)
  (mount-root))

;(init)