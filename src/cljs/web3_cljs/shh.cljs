(ns web3-cljs.shh
  (:refer-clojure :exclude [filter])
  (:require [web3-cljs.utils :as u :refer [js-apply]]))

(defn get-shh [web3]
  (aget web3 "shh"))

(defn post! [web3 & args]
  (js-apply (get-shh web3) "post" args))

(defn new-identity [web3 & args]
  (js-apply (get-shh web3) "newIdentity" args))

(defn has-identity? [web3 & args]
  (js-apply (get-shh web3) "hasIdentity" args))

(defn new-group [web3 & args]
  (js-apply (get-shh web3) "newGroup" args))

(defn add-to-group [web3 & args]
  (js-apply (get-shh web3) "addToGroup" args))

(defn filter [web3 & args]
  (js-apply (get-shh web3) "filter" args))



