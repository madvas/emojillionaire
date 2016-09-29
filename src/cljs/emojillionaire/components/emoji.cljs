(ns emojillionaire.components.emoji
  (:require
    [camel-snake-kebab.core :as cs :include-macros true]
    [cljs-react-material-ui.icons :as icons]
    [emojillionaire.emojis :refer [emojis]]
    [emojillionaire.styles :as st]
    [goog.string :as gstring]
    [goog.string.format]
    [reagent.core :as r]
    ))

(def emoji-url (str "http://cdn.jsdelivr.net/emojione/assets/%s/%s.%s"
                    (aget js/emojione "cacheBustParam")))

(defn emoji-kw->js-key [emoji-kw]
  (case emoji-kw
    :+1 ":+1:"
    :-1 ":-1:"
    (gstring/format ":%s:" (cs/->snake_case (name emoji-kw)))))

(def memo-emoji-kw->key (memoize emoji-kw->js-key))

(defn emoji-src [emoji-key type]
  (gstring/format
    emoji-url
    type
    (last (aget js/emojione
                "emojioneList"
                (cond
                  (string? emoji-key) emoji-key
                  (keyword? emoji-key) (memo-emoji-kw->key emoji-key)
                  (number? emoji-key) (nth emojis emoji-key)
                  :else (throw "Invalid emoji-key"))
                "unicode"))
    type))

(defn emoji
  ([emoji-key size] (emoji {} emoji-key size 0))
  ([emoji-key size horizontal-padding] (emoji {} emoji-key size horizontal-padding))
  ([opts emoji-key size horizontal-padding]
   (if (or (= emoji-key 0) (= emoji-key ""))
     (icons/content-remove-circle-outline)
     [:img (r/merge-props
             {:src (emoji-src emoji-key (or (:type opts) "svg"))
              :style {:width size
                      :padding-left horizontal-padding
                      :padding-right horizontal-padding}}
             opts)])))
