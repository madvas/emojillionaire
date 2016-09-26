(ns web3-cljs.db
  (:require [web3-cljs.utils :as u :refer [js-apply]]))

(defn get-db [web3]
  (aget web3 "db"))

(defn put-string! [web3 & args]
  (js-apply (get-db web3) "putString" args))

(defn get-string [web3 & args]
  (js-apply (get-db web3) "getString" args))

(defn put-hex! [web3 & args]
  (js-apply (get-db web3) "putHex" args))

(defn get-hex [web3 & args]
  (js-apply (get-db web3) "getHex" args))