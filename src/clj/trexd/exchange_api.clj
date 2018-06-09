(ns trexd.exchange-api
  (:require [trexd.bittrex-api :as trex])
  (:require [clojure.java.jdbc :as jdbc])
  (:require [trexd.db :as db])
  (:require [trexd.server-api :as sa])
  (:gen-class))

;; Fetch local DB recorded exchange data.
(defn get-latest-summaries
  "Last recorded summaries of all pairs and their details."
  []
  (let [data (db/query-in ["SELECT data FROM exchange ORDER BY timestamp DESC LIMIT 1"])]
    (-> data :market-summaries :result)))

(defn get-last-tick 
  "Get last tick from recorded DB history (no remote exchange call made)."
  [pair]
  (loop [summaries (get-latest-summaries)]
    (if-not (empty? summaries)
      (if (= (:pair (first summaries)) pair)
        (:last (first summaries))
        (recur (rest summaries))))))

(defn get-supported-pairs
  "Get supported pairs from recorded DB history (no remote exchange call made).
   Pair are from all exchanges."
  []
  (for [summary (get-latest-summaries)]
    (:pair summary)))

; TODO: Remove true, make sure after user remote log in, they setup their own metadata.
(defn bittrex? []
  (= (:exchange (sa/getmeta)) "bittrex")
  true)

;; All the plural functions can specify detailed target to fetch individual items.
;; All of them initiates a blocking POST request to remote exchange immediately.
;; Market data
(defn get-market-summaries []
  (cond
    (bittrex?) (trex/getmarketsummaries)))

(defn get-supported-currencies []
  (cond
    (bittrex?) (trex/getcurrencies)))

(defn get-ticker [market]
  (cond
    (bittrex?) (trex/getticker market)))

(defn get-orderbook 
  "Return current orderbook.
   Default returns both buy and sell side.
   Pass `buy` or `sell` to `type` to filter result."
  ([market]
   (cond
    (bittrex?) (trex/getorderbook market)))
  ([market type]
   (cond
    (bittrex?) (trex/getorderbook market type))))

(defn get-market-history [market]
  (cond
    (bittrex?) (trex/getmarkethistory market)))

;; User Actions
(defn create-limit-buy [market quantity rate]
  (cond
    (bittrex?) (trex/buylimit market quantity rate)))

(defn create-limit-sell [market quantity rate]
  (cond
    (bittrex?) (trex/selllimit market quantity rate)))

(defn create-margin-buy []
  (cond
    (bittrex?) (throw (.UnsupportedOperationException "Bittrex don't support margin order."))))

(defn create-margin-sell []
  (cond
    (bittrex?) (throw (.UnsupportedOperationException "Bittrex don't support margin order."))))

(defn cancel-order [uuid]
  (cond
    (bittrex?) (trex/cancel uuid)))

(defn get-order [uuid]
  "Fetch any order regardless of their state."
  (cond
    (bittrex?) (trex/getorder uuid)))

(defn get-open-orders 
  ([]
   (cond
    (bittrex?) (trex/getopenorders)))
  ([market]
   (cond
    (bittrex?) (trex/getopenorders market))))

;; Filter all order history, then filter out only the completed entries.
(defn get-closed-orders
  "Return completed orders only."
  ([]
   (cond
    (bittrex?) (trex/getorderhistory)))
  ([market]
   (cond
    (bittrex?) (trex/getorderhistory market))))

(defn get-balances
  ([]
   (cond
    (bittrex?) (trex/getbalances)))
  ([market]
   (cond
    (bittrex?) (trex/getbalance market))))

(defn get-order-history
  "Returns all order histories, both completed and cancelled orders."
  ([]
   (cond
    (bittrex?) (trex/getorderhistory)))
  ([market]
   (cond
    (bittrex?) (trex/getorderhistory market))))

(defn get-withdrawal-history
  ([]
   (cond
    (bittrex?) (trex/getwithdrawalhistory)))
  ([currency]
   (cond
    (bittrex?) (trex/getwithdrawalhistory currency))))

(defn get-deposit-history
  ([]
   (cond
    (bittrex?) (trex/getdeposithistory)))
  ([currency]
   (cond
    (bittrex?) (trex/getdeposithistory currency))))

(defn get-deposit-address [currency]
  (cond
    (bittrex?) (trex/getdepositaddress currency)))
