(ns trexd.bittrex-api
  (:require [trexd.util :as u])
  (:gen-class))

;; Final Format: {:success true, :msg "oh cow", :command "get-market", :result data}
(defn r-template 
  "Bittrex result map template. All exchanges share the same output format."
  [original-map command result]
  {:success (:success original-map)
   :msg     (if (nil? (:msg original-map))(:message original-map)(:msg original-map))
   :command command
   :result  result})

;; Bittrex Wrapper.
;; Converts to predefined clojure map template before returning. (json -> map -> templated map format)
;; Some of the result fields are not recorded or renamed. eg timestamp, null fields.
;; https://support.bittrex.com/hc/en-us/articles/115003723911-Developer-s-Guide-API
;; Public API
(defn getmarkets []
  (u/post->map "https://bittrex.com/api/v1.1/public/getmarkets"))  
(defn getcurrencies []
  (u/post->map "https://bittrex.com/api/v1.1/public/getcurrencies"))
(defn getticker [market]
  (let [r (u/post->map "https://bittrex.com/api/v1.1/public/getticker" {:query-params {"market" market}})
        rr (:result r)]
    (r-template r "get-ticker" {:bid  (rr :Bid)
                                :ask  (rr :Ask)
                                :last (rr :Last)})))
(defn getmarketsummaries []
  (let [r (u/post->map "https://bittrex.com/api/v1.1/public/getmarketsummaries")]
    (r-template r "get-market-summaries" (for [rr (:result r)]
                                           {:pair   (rr :MarketName)
                                            :high   (rr :High)
                                            :low    (rr :Low)
                                            :last   (rr :Last)
                                            :volume (rr :Volume)
                                            :base   (rr :BaseVolume)
                                            :bid    (rr :Bid)
                                            :ask    (rr :Ask)}))))
(defn getmarketsummary [market]
  (u/post->map "https://bittrex.com/api/v1.1/public/getmarketsummary" {:query-params {"market" market}}))
(defn getorderbook 
  ([market]
   (let [r (u/post->map "https://bittrex.com/api/v1.1/public/getorderbook" {:query-params {"market" market "type" "both"}})
         rr (:result r)]
     (r-template r "get-orderbook" {:buy  (for [rrb (:buy rr)]
                                            {:size (rrb :Quantity)
                                             :rate (rrb :Rate)})
                                    :sell (for [rrs (:sell rr)]
                                            {:size (rrs :Quantity)
                                             :rate (rrs :Rate)})})))
  ([market type]
   (u/post->map "https://bittrex.com/api/v1.1/public/getorderbook" {:query-params {"market" market "type" type}})))

(defn getmarkethistory [market]
  (let [r (u/post->map "https://bittrex.com/api/v1.1/public/getmarkethistory" {:query-params {"market" market}})]
    (r-template r "get-market-history") (for [rr (:result r)]
                                          {:id        (rr :Id)
                                           :size      (rr :Quantity)
                                           :rate      (rr :Price)
                                           :total     (rr :Total)
                                           :type      (rr :OrderType)
                                           :timestamp (u/date->epoch (rr :TimeStamp))})))

;; Market API
(defn buylimit [market quantity rate]
  (let [r (u/post->map-signed "https://bittrex.com/api/v1.1/market/buylimit" {:query-params {"market" market "quantity" quantity "rate" rate}})
        rr (:result r)]
    (r-template r "create-limit-buy" {:uuid (rr :uuid)})))

(defn selllimit [market quantity rate]
  (let [r (u/post->map-signed "https://bittrex.com/api/v1.1/market/selllimit" {:query-params {"market" market "quantity" quantity "rate" rate}})
        rr (:result r)]
    (r-template r "create-limit-sell" {:uuid (rr :uuid)})))

(defn cancel [uuid]
  (let [r (u/post->map-signed "https://bittrex.com/api/v1.1/market/cancel" {:query-params {"uuid" uuid}})]
    (r-template r "cancel-order" {})))

(defn getopenorders 
  ([]
   (let [r (u/post->map-signed "https://bittrex.com/api/v1.1/market/getopenorders")]
     (r-template r "get-open-orders" (for [rr (:result r)]
                                       {:uuid      (rr :OrderUuid)
                                        :pair      (rr :Exchange)
                                        :type      (rr :OrderType)
                                        :size      (rr :Quantity)
                                        :left      (rr :QuantityRemaining)
                                        :rate      (rr :Limit)
                                        :timestamp (u/date->epoch (rr :Opened))}))))
  ([market]
   (u/post->map-signed "https://bittrex.com/api/v1.1/market/getopenorders" {:query-params {"market" market}})))

;; Account API
(defn getbalances []
  (let [r (u/post->map-signed "https://bittrex.com/api/v1.1/account/getbalances")]
    (r-template r "get-balances" (for [rr (:result r)]
                                   {:pair      (rr :Currency)
                                    :balance   (rr :Balance)
                                    :available (rr :Available)
                                    :pending   (rr :Pending)}))))

(defn getbalance [currency]
  (let [r (u/post->map-signed "https://bittrex.com/api/v1.1/account/getbalance" {:query-params {"currency" currency}})
        rr (:result r)]
    (r-template r "get-balances" {:pair      (rr :Currency)
                                  :balance   (rr :Balance)
                                  :available (rr :Available)
                                  :pending   (rr :Pending)})))

(defn getdepositaddress [currency]
  (let [r (u/post->map-signed "https://bittrex.com/api/v1.1/account/getdepositaddress" {:query-params {"currency" currency}})
        rr (:result r)]
    (r-template r "get-deposit-address" {:pair    (rr :Currency)
                                         :address (rr :Address)})))

(defn withdraw 
  ([currency quantity address]
   (let [r (u/post->map-signed "https://bittrex.com/api/v1.1/account/withdraw" {:query-params {"currency" currency "quantity" quantity "address" address}})
         rr (:result r)]
     (r-template r "withdraw" {:uuid (rr :uuid)})))
  ([currency quantity address paymentid]
   (let [r (u/post->map-signed "https://bittrex.com/api/v1.1/account/withdraw" {:query-params {"currency" currency "quantity" quantity "address" address "paymentid" paymentid}})
         rr (:result r)]
     (r-template r "withdraw" {:uuid (rr :uuid)}))))

(defn getorder [uuid]
  (let [r (u/post->map-signed "https://bittrex.com/api/v1.1/account/getorder" {:query-params {"uuid" uuid}})
        rr (:result r)]
    (r-template r "get-order" {:uuid      (rr :OrderUuid)
                               :pair      (rr :Exchange)
                               :type      (rr :Type)
                               :size      (rr :Quantity)
                               :left      (rr :QuantityRemaining)
                               :rate      (rr :Limit)
                               :timestamp (u/date->epoch (rr :Opened))
                               :isopen    (rr :IsOpen)})))

(defn getorderhistory 
  ([]
   (let [r (u/post->map-signed "https://bittrex.com/api/v1.1/account/getorderhistory")]
     (r-template r "get-order-history" (for [rr (:result r)]
                                         {:uuid      (rr :uuid)
                                          :pair      (rr :Exchange)
                                          :type      (rr :Type)
                                          :rate      (rr :Limit)
                                          :size      (rr :Quantity)
                                          :left      (rr :QuantityRemaining)
                                          :timestamp (u/date->epoch (rr :TimeStamp))}))))
  ([market]
   (u/post->map-signed "https://bittrex.com/api/v1.1/account/getorderhistory" {:query-params {"market" market}})))

(defn getwithdrawalhistory 
  ([]
   (let [r (u/post->map-signed "https://bittrex.com/api/v1.1/account/getwithdrawalhistory")]
     (r-template r "get-withdrawal-history" (for [rr (:result r)]
                                              {:uuid      (rr :uuid)
                                               :pair      (rr :Currency)
                                               :size      (rr :Amount)
                                               :address   (rr :Address)
                                               :txcost    (rr :TxCost)
                                               :timestamp (u/date->epoch (rr :TimeStamp))}))))
  ([currency]
   (u/post->map-signed "https://bittrex.com/api/v1.1/account/getwithdrawalhistory" {:query-params {"currency" currency}})))

(defn getdeposithistory 
  ([]
   (let [r (u/post->map-signed "https://bittrex.com/api/v1.1/account/getdeposithistory")]
     (r-template r "get-deposit-history" (for [rr (:result r)]
                                           {:uuid      (rr :uuid)
                                            :pair      (rr :Currency)
                                            :size      (rr :Amount)
                                            :address   (rr :Address)
                                            :txcost    (rr :TxCost)
                                            :timestamp (u/date->epoch (rr :TimeStamp))}))))
  ([currency]
   (u/post->map-signed "https://bittrex.com/api/v1.1/account/getdeposithistory" {:query-params {"currency" currency}})))
