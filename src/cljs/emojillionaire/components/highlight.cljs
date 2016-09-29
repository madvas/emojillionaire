(ns emojillionaire.components.highlight
  (:require
    [cljsjs.react-highlight]
    [reagent.core :as r]))

(def highlight (r/adapt-react-class js/Highlight))
