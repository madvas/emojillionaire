(ns emojillionaire.utils
  (:require
    [ajax.core :refer [GET]]
    [bidi.bidi :as bidi]
    [cljs-time.coerce :refer [to-date-time to-long to-local-date-time]]
    [cljs-time.core :refer [date-time to-default-time-zone]]
    [cljs-time.format :as time-format]
    [cljs-web3.core :as web3]
    [cljs-web3.eth :as web3-eth]
    [emojillionaire.routes :refer [routes]]
    [goog.string :as gstring]
    [goog.string.format]
    [medley.core :as medley]))

(def path-for (partial bidi/path-for routes))

(defn evt-val [e]
  (aget e "target" "value"))

(defn remove-at [coll pos]
  (let [coll (vec coll)]
    (vec (concat (subvec coll 0 pos) (subvec coll (inc pos))))))

(defn keys-big-number->number [m keys]
  (medley/map-kv (fn [k v]
                   (if (contains? (set keys) k)
                     [k (.toNumber v)]
                     [k v])) m))

(defn sort-by-desc [key-fn coll]
  (sort-by key-fn #(compare %2 %1) coll))

(defn sort-big-numbers-desc [key-fn coll]
  (sort-by key-fn #(compare (.toNumber %2) (.toNumber %1)) coll))

(defn big-number->date-time [big-num]
  (to-date-time (* (.toNumber big-num) 1000)))

(defn big-number->state [big-num]
  (case (.toNumber big-num)
    0 :active
    :inactive))

(defn round2
  "Round a double to the given precision (number of significant digits)"
  ([d] (round2 d 3))
  ([d precision]
   (let [factor (Math/pow 10 precision)]
     (/ (Math/round (* d factor)) factor))))

(defn eth [big-num]
  (str (web3/from-wei big-num :ether) " ETH"))

(defn usd [big-num conversion-rate]
  (gstring/format "%s USD" (round2 (* (web3/from-wei big-num :ether) conversion-rate) 2)))

(defn extract-props [v]
  (let [p (nth v 0 nil)]
    (if (map? p) p)))

(defn extract-children [v]
  (let [p (nth v 0 nil)
        first-child (if (or (nil? p) (map? p)) 1 0)]
    (if (> (count v) first-child)
      (subvec v first-child))))

(defn new-window-link [href body]
  [:a {:href href :target :_blank} body])

(defn truncate
  "Truncate a string with suffix (ellipsis by default) if it is
   longer than specified length."
  ([string length]
   (truncate string length "..."))
  ([string length suffix]
   (let [string-len (count string)
         suffix-len (count suffix)]
     (if (<= string-len length)
       string
       (str (subs string 0 (- length suffix-len)) suffix)))))

(defn etherscan-url [type network hash]
  (gstring/format "https://%setherscan.io/%s/%s"
                  (if (= network :testnet) "testnet." "")
                  type
                  hash))

(defn etherscan-address-url [network address]
  (etherscan-url "address" network address))

(defn etherscan-tx-url [network tx-hash]
  (etherscan-url "tx" network tx-hash))

(defn etherscan-link [etherscan-url]
  [:a {:href etherscan-url
       :target :_blank} "Open in Etherscan"])

(defn fetch-contract! [contracts-path contract-name on-success & [on-error]]
  (GET (str contracts-path ".json?_=" (.getTime (new js/Date)))
       {:response-format :json
        :keywords? true
        :handler (fn [res]
                   (let [{:keys [abi bin]} (-> res :contracts (get (keyword contract-name)))]
                     (on-success (JSON.parse abi) bin)))
        :error-handler on-error}))

(defn deploy-bin! [web3 abi bin from-addr gas on-send on-deploy & [on-error]]
  (let [Contract (web3-eth/contract web3 abi)]
    (cljs-web3.utils/js-apply
      Contract
      "new"
      [{:from from-addr
        :data bin
        :gas gas}
       (fn [error res]
         (if (and error on-error)
           (on-error error)
           (if-let [address (and res (aget res "address"))]
             (on-deploy (web3-eth/contract-at web3 abi address))
             (on-send res))))])))

(defn estimated-bet-cost [{:keys [guess-cost guess-fee oraclize-fee]} guess-count]
  (if (<= guess-count 0)
    (js/BigNumber. 0)
    (.plus (.times (.plus guess-cost guess-fee)
                   guess-count)
           oraclize-fee)))

(defn calculate-bet-cost [contract betsCount]
  (let [bet-value (web3-eth/contract-call contract :guess-cost)
        house-fee (web3-eth/contract-call contract :guess-fee)]
    (.plus (.times (.plus bet-value house-fee) betsCount)
           (web3/to-big-number (web3/to-wei 0.005 :ether)))))

(defn format-date [date]
  (time-format/unparse-local (time-format/formatters :rfc822) (to-default-time-zone (to-date-time date))))