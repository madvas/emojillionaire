(ns web3-cljs.core
  (:require [clojure.string :as str]
            [web3-cljs.utils :as u :refer [js-apply js-prototype-apply]]))

(def version-api (u/prop-or-clb-fn "version" "api"))
(def version-node (u/prop-or-clb-fn "version" "node"))
(def version-network (u/prop-or-clb-fn "version" "network"))
(def version-ethereum (u/prop-or-clb-fn "version" "ethereum"))
(def version-whisper (u/prop-or-clb-fn "version" "whisper"))

(defn connected? [web3]
  (.isConnected web3))

(defn sha3 [& args]
  (js-prototype-apply js/Web3 "sha3" args))

(defn to-ascii [& args]
  (js-prototype-apply js/Web3 "toAscii" args))

(defn from-ascii [& args]
  (js-prototype-apply js/Web3 "fromAscii" args))

(defn to-hex [& args]
  (js-prototype-apply js/Web3 "toHex" args))

(defn to-decimal [& args]
  (js-prototype-apply js/Web3 "toDecimal" args))

(defn from-decimal [& args]
  (js-prototype-apply js/Web3 "fromDecimal" args))

(defn from-wei [number unit]
  (js-prototype-apply js/Web3 "fromWei" [number (name unit)]))

(defn to-wei [number unit]
  (js-prototype-apply js/Web3 "toWei" [number (name unit)]))

(defn to-big-number [& args]
  (js-prototype-apply js/Web3 "toBigNumber" args))

(defn address? [& args]
  (js-prototype-apply js/Web3 "isAddress" args))

(defn reset [web3]
  (.reset web3))

(defn set-provider [web3 & args]
  (js-apply web3 "setProvider" args))

(defn current-provider [web3]
  (aget web3 "currentProvider"))

;; Providers

(defn http-provider [Web3 uri]
  (let [constructor (aget Web3 "providers" "HttpProvider")]
    (constructor. uri)))

(defn qt-provider [Web3 uri]
  (let [constructor (aget Web3 "providers" "QtSyncProvider")]
    (constructor. uri)))

(defn create-web3 [url]
  (new js/Web3 (http-provider js/Web3 url)))

