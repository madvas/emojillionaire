(ns emojillionaire.web3-fx
  (:require
    [cljs-web3.eth :as web3-eth]
    [cljs.spec :as s]
    [re-frame.core :refer [reg-fx dispatch console reg-event-db reg-event-fx]]))

(defn- blockchain-filter-opts? [x]
  (or (map? x) (string? x) (nil? x)))

(s/def ::instance (complement nil?))
(s/def ::db map?)
(s/def ::db-path (s/coll-of keyword?))
(s/def ::dispatch (s/or :kw keyword?
                        :sq sequential?))
(s/def ::dispatches (s/cat :on-success ::dispatch
                           :on-error ::dispatch))
(s/def ::contract-fn-arg (complement map?))
(s/def ::addresses (s/coll-of string?))
(s/def ::watch? boolean?)
(s/def ::blockchain-filter-opts blockchain-filter-opts?)
(s/def ::web3 (complement nil?) #_(partial inst? js/Web3))
(s/def ::event-ids (s/coll-of any?))
(s/def :web3-fx.blockchain/fns (s/coll-of (s/cat :f fn?
                                                 :args (s/* any?)
                                                 :on-success ::dispatch
                                                 :on-error ::dispatch)))

(s/def :web3-fx.contract/fns (s/coll-of (s/cat :f keyword?
                                               :args (s/* ::contract-fn-arg)
                                               :on-success ::dispatch
                                               :on-error ::dispatch)))

(s/def :web3-fx.contract/fn (s/cat :f keyword?
                                   :args (s/* ::contract-fn-arg)
                                   :transaction-opts map?
                                   :on-success ::dispatch
                                   :on-error ::dispatch
                                   :on-transaction-receipt ::dispatch))

(s/def ::events (s/coll-of (s/cat :event-id (s/? any?)
                                  :event-name keyword?
                                  :event-filter-opts (or map? nil?)
                                  :blockchain-filter-opts blockchain-filter-opts?
                                  :on-success ::dispatch
                                  :on-error ::dispatch)))


(s/def ::contract-events (s/keys :req-un [::instance ::events ::db-path ::db]))
(s/def ::contract-events-stop-watching (s/keys :req-un [::event-ids ::db-path ::db]))

(s/def ::contract-constant-fns (s/keys :req-un [::instance :web3-fx.contract/fns]))
(s/def ::contract-state-fn (s/keys :req-un [::instance ::web3 :web3-fx.contract/fn ::db-path]))
(s/def ::blockchain-fns (s/keys :req-un [::web3 :web3-fx.blockchain/fns]))
(s/def ::balances (s/keys :req-un [::addresses ::dispatches ::web3]
                          :opt-un [::db ::watch? ::db-path ::blockchain-filter-opts]))

(defn- dispatch-call [dispatch-conformed & args]
  (let [{:keys [kw sq]} (apply hash-map dispatch-conformed)]
    (dispatch (vec (concat (or sq [kw]) args)))))

(defn- ensure-filter-params [event]
  (if (:event-id event)
    event
    (assoc event :event-id (:event-name event))))

(defn- dispach-fn [on-success on-error & args]
  (fn [err res]
    (if err
      (apply dispatch-call on-error (cons err args))
      (apply dispatch-call on-success (cons res args)))))

(defn- contract-event-dispach-fn [on-success on-error]
  (fn [err res]
    (if err
      (dispatch-call on-error err)
      (dispatch-call on-success (:args res) res))))

(reg-event-db
  :web3-fx.contract/assoc-event-filters
  (fn [db [_ filters-db-path filters]]
    (assoc-in db filters-db-path filters)))

(defn- event-stop-watching! [db db-path event-id]
  (when-let [event-filter (get-in db (conj db-path event-id))]
    (web3-eth/stop-watching! event-filter (fn []))))

(reg-fx
  :web3-fx.contract/events
  (fn [raw-config]
    (let [{:keys [instance events db-path db] :as config}
          (s/conform ::contract-events raw-config)]

      (when (= :cljs.spec/invalid config)
        (console :error (s/explain-str ::contract-events raw-config)))

      (let [new-filters
            (->> events
              (map ensure-filter-params)
              (reduce (fn [acc {:keys [event-id event-name on-success on-error
                                       event-filter-opts blockchain-filter-opts]}]
                        (event-stop-watching! db db-path event-id)
                        (assoc acc event-id (web3-eth/contract-call
                                              instance
                                              event-name
                                              event-filter-opts
                                              blockchain-filter-opts
                                              (contract-event-dispach-fn on-success on-error)))) {}))]
        (dispatch [:web3-fx.contract/assoc-event-filters db-path new-filters])))))

(reg-fx
  :web3-fx.contract/events-stop-watching
  (fn [raw-config]
    (let [{:keys [event-ids db-path db] :as config}
          (s/conform ::contract-events-stop-watching raw-config)]

      (when (= :cljs.spec/invalid config)
        (console :error (s/explain-str ::contract-events-stop-watching raw-config)))

      (doseq [event-id event-ids]
        (event-stop-watching! db db-path event-id)))))

(reg-fx
  :web3-fx.contract/constant-fns
  (fn [raw-params]
    (let [{:keys [fns instance] :as params} (s/conform ::contract-constant-fns raw-params)]
      (when (= :cljs.spec/invalid params)
        (console :error (s/explain-str ::contract-constant-fns raw-params)))
      (doseq [{:keys [f args on-success on-error]} fns]
        (apply web3-eth/contract-call (concat [instance f]
                                              args
                                              [(dispach-fn on-success on-error)]))))))


(defn- remove-blockchain-filter! [db filter-db-path]
  (when-let [blockchain-filter (get-in db filter-db-path)]
    (web3-eth/stop-watching! blockchain-filter (fn [])))
  (assoc-in db filter-db-path nil))

(reg-event-db
  :web3-fx.contract/transaction-receipt-loaded
  (fn [db [_ [tx-hashes-db-path filter-db-path] transaction-hash receipt on-transaction-receipt]]
    (let [rest-tx-hashes (dissoc (get-in db tx-hashes-db-path) transaction-hash)]

      (dispatch-call on-transaction-receipt receipt)

      (cond-> db
        (empty? rest-tx-hashes)
        (remove-blockchain-filter! filter-db-path)

        true
        (assoc-in tx-hashes-db-path rest-tx-hashes)))))

(reg-event-db
  :web3-fx.contract/add-transaction-hash-to-watch
  (fn [db [_ web3 db-path transaction-hash on-transaction-receipt]]
    (let [tx-hashes-db-path (conj db-path :transaction-hashes)
          filter-db-path (conj db-path :filter)
          all-tx-hashes (assoc (get-in db tx-hashes-db-path) transaction-hash on-transaction-receipt)]

      (remove-blockchain-filter! db filter-db-path)

      (-> db
        (assoc-in filter-db-path
                  (web3-eth/filter
                    web3
                    "latest"
                    (fn [err]
                      (when-not err
                        (doseq [[tx-hash on-tx-receipt] all-tx-hashes]
                          (web3-eth/get-transaction-receipt
                            web3
                            tx-hash
                            (fn [_ receipt]
                              (when receipt
                                (dispatch [:web3-fx.contract/transaction-receipt-loaded
                                           [tx-hashes-db-path filter-db-path]
                                           tx-hash
                                           receipt
                                           on-tx-receipt])))))))))

        (assoc-in tx-hashes-db-path all-tx-hashes)))))

(defn- create-state-fn-callback [web3 db-path on-success on-error on-transaction-receipt]
  (fn [err transaction-hash]
    (if err
      (dispatch-call on-error err)
      (do (dispatch-call on-success transaction-hash)
          (dispatch [:web3-fx.contract/add-transaction-hash-to-watch
                     web3 db-path transaction-hash on-transaction-receipt])))))

(reg-fx
  :web3-fx.contract/state-fn
  (fn [raw-params]
    (let [{:keys [web3 instance db-path] :as params} (s/conform ::contract-state-fn raw-params)]
      (when (= :cljs.spec/invalid params)
        (console :error (s/explain-str ::contract-state-fn raw-params)))
      (let [{:keys [f args transaction-opts on-success on-error on-transaction-receipt]} (:fn params)]
        (apply web3-eth/contract-call
               (concat [instance f]
                       args
                       [transaction-opts]
                       [(create-state-fn-callback web3 db-path on-success on-error on-transaction-receipt)]))))))

(reg-fx
  :web3-fx.blockchain/fns
  (fn [raw-params]
    (let [{:keys [fns] :as params} (s/conform ::blockchain-fns raw-params)]
      (when (= :cljs.spec/invalid params)
        (console :error (s/explain-str ::blockchain-fns raw-params)))
      (doseq [{:keys [f args on-success on-error]} fns]
        (apply f (concat [(:web3 params)] args [(dispach-fn on-success on-error)]))))))

(reg-event-db
  :web3-fx.blockchain/add-addresses-to-watch
  (fn [db [_ web3 db-path addresses blockchain-filter-opts on-success on-error]]
    (let [addresses-db-path (conj db-path :addresses)
          filter-db-path (conj db-path :filter)
          all-addresses (set (concat (get-in db addresses-db-path)
                                     addresses))]

      (when-let [blockchain-filter (get-in db filter-db-path)]
        (web3-eth/stop-watching! blockchain-filter (fn [])))

      (let [blockchain-filter
            (web3-eth/filter
              web3
              blockchain-filter-opts
              (fn [err _]
                (if err
                  (dispatch [on-error err])
                  (doseq [address all-addresses]
                    (web3-eth/get-balance web3 address (dispach-fn on-success on-error address))))))]

        (-> db
          (assoc-in addresses-db-path all-addresses)
          (assoc-in filter-db-path blockchain-filter))))))

(reg-fx
  :web3-fx.blockchain/balances
  (fn [{:keys [addresses web3 dispatches watch? db-path blockchain-filter-opts] :as config}]
    (s/assert ::balances config)
    (let [{:keys [on-success on-error]} (s/conform ::dispatches dispatches)]
      (doseq [address addresses]
        (web3-eth/get-balance web3 address (dispach-fn on-success on-error address)))
      (when (and watch? (seq addresses))
        (dispatch [:web3-fx.blockchain/add-addresses-to-watch
                   web3
                   db-path
                   addresses
                   blockchain-filter-opts
                   on-success
                   on-error])))))
