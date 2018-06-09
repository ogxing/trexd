(ns trexd.order
  (:require [reagent.core :as r]
            [soda-ash.core :as sa]
            [ajax.core :refer [GET POST]]
            [cljs.reader :as reader]
            [trexd.util :as u]
            [trexd.shared :as s])) 

(defn fetch-order-data []
  (POST "/"
    {:params (str {:cookies "cookies"
                   :data    (u/func->string "get-open-orders" [])})
     :handler (fn [arg1]
                (let [response    (-> arg1 reader/read-string :data :result)
                      ; Add id to each data.
                      with-id   (into (sorted-map) (for [data response :let [eid (u/gen-id)]] {eid (merge data {:eid eid})}))
                      _         (u/update-state with-id [:order-segment :data] true)]))
     :error-handler #(println %1)}))

; Fetch then update given atom after async call returns.
(defn fetch-balance->update [currency result-atom]
  (POST "/"
      {:params (str {:cookies "cookies"
                     :data    (u/func->string "get-balances" [currency])})
       :handler (fn [arg1]
                  (let [response    (-> arg1 reader/read-string :data :result :available u/float->string)]
                    (reset! result-atom (if (nil? response) "0" response))))
       :error-handler #(println %1)}))

(defn fetch-last-tick->update [pair result-atom]
  (POST "/"
      {:params (str {:cookies "cookies"
                     :data    (u/func->string "get-last-tick" [pair])})
       :handler (fn [arg1]
                  (let [response    (-> arg1 reader/read-string :data u/float->string)]
                    (reset! result-atom response)))
       :error-handler #(println %1)}))

; "BTC-DOGE" -> {:market "BTC" :coin "DOGE"}
(defn pair->market-pair[pair]
  (let [[market coin] (clojure.string/split pair #"-")]
    {:market market :coin coin}))

; {:market "BTC" :coin "DOGE"} -> "BTC-DOGE"
(defn market-pair->pair[pair-map]
  (str (:market pair-map) "-" (:coin pair-map)))

; Draw actual order page.
(defn draw-create-order-modal []
  (let [pairs       (u/get-all-pairs)
        pair        (r/atom "")
        m-available (r/atom "")
        c-available (r/atom "")
        rate        (r/atom "")
        quantity    (r/atom "")
        last        (r/atom "")
        buy         (r/atom true)
        market      (r/atom "")
        coin        (r/atom "")
        save        (r/atom "save")]
    (fn []
      [:div
       [sa/SegmentGroup
        [sa/Segment
         [:div [:b "Available " @market " :  "] [:a {:onClick #(reset! quantity (u/float->string (/ @m-available @rate)))} @m-available]]
         [:div [:b "Available " @coin   " :  "] [:a {:onClick #(reset! quantity @c-available)} @c-available]]
         [:div [sa/Button {:toggle   true 
                           :positive @buy
                           :negative (not @buy)
                           :disabled (= @save "saving")
                           :onClick  #(do
                                        (swap! buy not)
                                        (reset! save "save"))}
                (if @buy "BUY" "SELL")]]
         [:div [:b "Rate     : "] [sa/Input {:value    @rate
                                             :disabled (= @save "saving")
                                             :onChange #(do 
                                                          (reset! rate (.-value %2))
                                                          (reset! save "save"))}]]
         [:div [:b "Quantity : "] [sa/Input {:value    @quantity
                                             :disabled (= @save "saving")
                                             :onChange #(do 
                                                          (reset! quantity (.-value %2))
                                                          (reset! save "save"))}]]
         [:div [:b "Pair     : "] [sa/Dropdown {:selection    true
                                                :search       true
                                                :disabled     (= @save "saving")
                                                :defaultValue -1
                                                :options      (for [{:keys [pair id]} (vals @u/shared-summary)]
                                                                {:text pair :value id})
                                                ; Fetch and update `last` tick value.
                                                :onChange     #(let [pair-last-map (@u/shared-summary (.-value %2))]
                                                                 (reset! pair (:pair pair-last-map))
                                                                 (reset! market (-> @pair pair->market-pair :market))
                                                                 (reset! coin   (-> @pair pair->market-pair :coin))
                                                                 (reset! save "save")
                                                                 (fetch-last-tick->update @pair last)
                                                                 (fetch-balance->update @market m-available)
                                                                 (fetch-balance->update @coin c-available))}]]
         [:div [:b "Last     : "] [:a {:onClick #(reset! rate @last)} @last]]
         [sa/Segment
          [:div [:b "Summary: "]]
          [:div [:b (if @buy "BUY " "SELL ") @quantity " " @coin " for " (u/float->string (* @quantity @rate)) " " @market ". Rate: " (u/float->string @rate)]]]
         [:div [sa/Button {:toggle   true 
                           :active   (not (or (u/blank? @rate) (u/blank? @pair) (u/blank? @quantity)))
                           :disabled (or (u/blank? @rate) (u/blank? @pair) (u/blank? @quantity)  (= @save "saving") (= @save "done"))
                           :loading  (= @save "saving")
                           :onClick  (fn []
                                       (if (= @save "save")
                                         (reset! save "confirmation")
                                         (do
                                           (reset! save "saving")
                                           (POST "/"
                                             {:params (str {:cookies "cookies"
                                                            :data    (u/func->string (str "create-limit-" (if @buy "buy" "sell"))
                                                                       [@pair @quantity @rate])})
                                              :handler #(reset! save "done") ; Refetch then redraw open order list.
                                              :error-handler #(println %1)}))))}
                (cond
                  (= @save "save")         "Place Order"
                  (= @save "confirmation") "Confirm?"
                  (= @save "saving")       "Saving"
                  (= @save "done")         "Done")]]]]])))

(defn draw-open-orders-segment [order-data]
  (let [order-id  (:uuid order-data)
        pair      (:pair order-data)
        type      (:type order-data)
        quantity  (r/atom (:size order-data))
        rate      (r/atom (:rate order-data))
        remaining (:left order-data)
        timestamp (:timestamp order-data)
        delete    (r/atom "")]
    (fn [order-data]
      [:div
       [sa/SegmentGroup
        [sa/Segment
         [:div [:b "Uuid      : "] order-id]
         [:div [:b "Pair      : "] pair]
         [:div [:b "Type      : "] type]
         [:div [:b "Quantity  : "] @quantity]
         [:div [:b "Rate      : "] @rate]
         [:div [:b "Remaining : "] remaining]
         [:div [:b "Created   : "] timestamp]
         [sa/Button {:negative true
                     :disabled (not (or (u/blank? @delete) (= @delete "Remove?")))
                     :content  @delete
                     :icon     "remove"
                     :onClick  (fn []
                                 (if (u/blank? @delete)
                                   (reset! delete "Remove?")
                                   (do
                                     (reset! delete "Removing..")
                                     (POST "/"
                                       {:params (str {:cookies "cookies"
                                                      :data    (u/func->string "cancel-order" [order-id])})
                                        :handler #(do 
                                                    (reset! delete "Done")
                                                    (fetch-order-data)) ; Refetch then redraw open order list to guarantee order is really cancelled.
                                        :error-handler #(println %1)}))))}]]]])))

(defn setup-order-page []
  (fetch-order-data)
  (fn []
    [:div
     [sa/Header {:as "h3"} "Order"]
     [sa/Modal {:size "fullscreen" :trigger (r/as-element [sa/Button {:positive true
                                                                      :content  "Create New"
                                                                      :icon     "add"}])}
      [sa/ModalHeader "New Order"]
      [draw-create-order-modal]]
     [sa/Button {:icon "refresh" :onClick #(fetch-order-data)}]
     ; On data change, re-draw (generate) updated segments.
     ; Metadata to avoid "all seq should have a key" warning.
     (let [data-list  (-> @s/app-state :order-segment :data vals)]
       (for [data data-list]
         ^{:key (:uuid data)} [draw-open-orders-segment data]))]))
