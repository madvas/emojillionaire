(ns web3-cljs.net
  (:require [web3-cljs.utils :as u]))

(def listening? (u/prop-or-clb-fn "net" "listening"))
(def peer-count (u/prop-or-clb-fn "net" "peerCount"))