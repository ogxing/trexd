(ns trexd.task
  (:require [clojure.java.jdbc :as jdbc])
  (:require [trexd.db :as db])
  (:require [trexd.server-api :as s])
  (:require [trexd.exchange-api :as exchange])
  (:require [trexd.notification :as n])
  (:require [trexd.util :as u])
  (:gen-class))

(defn notify-done 
  "Notify user then return `done` to signify task is completed."
  [subject msg]
  (n/notify subject msg)
  "done")

(defn done? [return-value]
  (= return-value "done"))

;;; Utility tasks.           
(defn function->list 
  "Convert (println 123 456) -> [println 123 456]"
  [func-string]
  ; After read-string, first element will be type `symbol`, call name to convert it to string.
  (let [list (read-string (clojure.string/replace func-string #"\(|\)" {"(" "[" ")" "]"}))]
    (assoc list 0 (-> list first name))))

(defn func-name-match? 
  "Is the function contained in the string same as the given name?
   If parse failure, return false."
  [func-string expected-name]
  (try
    (= (first (function->list func-string)) expected-name)
    (catch Exception e false)))

(defn filter-tasklist
  "Filter string based function vector based on their name.
   ex: (filter-tasklist [`(func1 123)` `(func2 456)` `(func3 777)`] [`func1` `func3`])
   Returns: [`(func1 123)` `(func3 777)`]"
  [tasklist function-name-list]
  ; Filter tasklist, `nil` means not match.
  (remove nil? (for [task tasklist] ; sample task => {:id 1 :data "(println 123)"}
                 (let [func (:data task)]
                   ; Check if task matches any given name, if so returns true.
                   (if (u/in? (for [name function-name-list] (func-name-match? func name)) true)
                     task ; Returns actual task if matches, else return nil to simplify filter.
                     nil)))))

(defn fill-pair-last-details 
  "Given pair `BTC-DOGE`, return its latest summaries (last tick, exchange, volume ...)."
  [pairlist]
  (let [latest-summaries (exchange/get-latest-summaries)]
    (for [pair pairlist]
      (loop [summaries latest-summaries]
        (cond
          (empty? summaries) nil ; Will only be nil (not found) if exchange removed the pair.
          (= (:pair (first summaries)) pair) (first summaries)
          :else (recur (rest summaries)))))))

(defn active-alert-pair
  "Return current active alerts."
  [userid]
  ; Search 
  (let [task-ids (db/query-in ["SELECT tasklist FROM session WHERE id = ?" userid])
        ; Filter out unrelated tasks. We only want type `alert` here.
        tasklist (filter-tasklist (s/ids->impls task-ids) ["alert>" "alert<"])]
    tasklist))

(defn no-alert-pair
  "Return list of exchange pairs where no alert is set
   by searching all alert tasks from active task list of selected user."
  [userid]
  ; Search 
  (let [pairlist (exchange/get-supported-pairs)
        task-ids (db/query-in ["SELECT tasklist FROM session WHERE id = ?" userid])
        ; Filter out unrelated tasks. We only want type `alert` here.
        tasklist (filter-tasklist (s/ids->impls task-ids) ["alert>" "alert<"])
        ; Fetch `pair` argument passed to the `alert` function. Result will be a list of pair.
        ; eg (alert `bittrex` `BTC-DOGE` 1) -> `BTC-DOGE`, nth 2 returns third item (pair).
        taskpair (for [task tasklist] (nth (function->list (:data task)) 2))]
    ; If pair doesn't exist in current active alert task, return that pair.
    (for [pair pairlist
          :when (not (u/in? taskpair pair))]
      pair)))

(defn since-last-pair
  "Return all alerts since?"
  [timestamp]
  ; Search 
  (let [task-ids (db/query ["SELECT * FROM task WHERE created >= ?" timestamp])
        ; Filter out unrelated tasks. We only want type `alert` here.
        tasklist (filter-tasklist (s/ids->impls task-ids) ["alert>" "alert<"])]
    tasklist))

;;; All tasks interval are managed during creation.
;;; Return `done` to stop repeating task.
;;; Monitoring tasks, use local cached data instead of querying remote exchange directly.
(defn alert< [exchange pair rate]
  (if (< (exchange/get-last-tick pair) (u/string->float rate))
    (notify-done (str "Alert< " exchange ": " pair " " rate) "Price Alert :)")))
(defn alert> [exchange pair rate]
  (if (> (exchange/get-last-tick pair) (u/string->float rate))
    (notify-done (str "Alert> " exchange ": " pair " " rate) "Price Alert :)")))

(defn buy-then-sell-limit
  "Create limit buy, after filled, auto create limit sell.
   TODO complete this lol."
  [buyrate sellrate quantity])

;;; Long Running Tasks, never return `done`.
(defn refresh-orders
  "Cancel and reset orders all orders, as some exchanges will auto cancel order after 30days."
  []
  (doall (for [order (-> (exchange/get-open-orders) :result)]
           (do
             (exchange/cancel-order (:uuid order))
             (if (-> (:type order) clojure.string/lower-case (.contains "buy"))
               (exchange/create-limit-buy  (:pair order) (:size order) (:rate order))
               (exchange/create-limit-sell (:pair order) (:size order) (:rate order))))))
  (n/notify "Order refreshed!"
            "All orders are reset identically to avoid order auto expire."))

(def notify-order-last (atom (System/currentTimeMillis)))
(defn notify-order-complete
  "Notify user if any limit order is successfully executed.
   This function query remote exchange directly for real time order details."
  []
  ; Since last time, what order is completed, exclude cancelled orders.
  (let [hist-orders (:result (exchange/get-order-history))
        results     (doall (remove nil? (for [order hist-orders]
                                          (if (and (> (:timestamp order) @notify-order-last)
                                                   (zero? (:left order)))
                                            order))))]
    ; Send notification to user if any order completed after last recorded time.
    (if-not (empty? results)
      (do 
        (n/notify "Order completed!" (u/map->string results))
        (reset! notify-order-last (System/currentTimeMillis))))))
 
(defn data-collect 
  "Collect exchange data periodically, indefinitely."
  []
  (let [summaries (exchange/get-market-summaries)]
    (jdbc/insert! (db/get-con) :exchange {:timestamp (System/currentTimeMillis)
                                          :data      {:market-summaries summaries}})))
