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
    [goog.string.format]
    [emojillionaire.components.main-panel :refer [main-panel]]
    [emojillionaire.events]
    [emojillionaire.ga-fx :as ga-fx]
    [emojillionaire.routes :refer [routes]]
    [emojillionaire.subs]
    [print.foo :include-macros true]
    [pushy.core :as pushy]
    [re-frame.core :refer [dispatch dispatch-sync]]
    [reagent.core :as reagent]
    ))

(def history
  (pushy/pushy #(dispatch [:set-current-page %]) (partial bidi/match-route routes)))

(defn mount-root []
  (s/check-asserts goog.DEBUG)
  (ga-fx/set-enabled! (not goog.DEBUG))
  (.clear js/console)
  (reagent/render [main-panel]
                  (.getElementById js/document "app")))

(defn ^:export init []
  (dispatch-sync [:initialize])
  (pushy/start! history)
  (mount-root))

