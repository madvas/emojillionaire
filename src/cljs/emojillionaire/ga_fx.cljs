(ns emojillionaire.ga-fx
  (:require [cljs.spec :as s]
            [re-frame.core :refer [reg-fx]]))

(def ^:dynamic *enabled* true)

(defn set-enabled! [enabled?]
  (set! *enabled* enabled?))

(reg-fx
  :ga/page-view
  (fn [opts]
    (when *enabled*
      (js/ga "send" "pageview" (clj->js opts)))))

(reg-fx
  :ga/event
  (fn [[category action label value]]
    (when *enabled*
      (js/ga "send" (clj->js
                      (merge {:hitType "event"}
                             (when category) {:eventCategory category}
                             (when action) {:eventAction action}
                             (when label) {:eventLabel label}
                             (when value) {:eventValue value}))))))

(reg-fx
  :ga/exception
  (fn [[description fatal?]]
    (when *enabled*
      (js/ga "send" "exception" (clj->js {:exDescription description
                                          :exFatal (if fatal? true false)})))))
