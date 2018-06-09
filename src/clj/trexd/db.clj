(ns trexd.db
  (:require [clojure.java.jdbc :as jdbc])
  (:gen-class))

;; Database schemas, used to create connection.
(def ^:private db-spec {:classname   "org.sqlite.JDBC"
                        :subprotocol "sqlite"          ; Protocol to use
                        :subname     "db/db.sqlite3"}) ; Location of db 

(def db-create-tables
  [(jdbc/create-table-ddl
    :session                               ; User sessions, each with own unique tasklist.
    [[:id       :text :primary :key]       ; Username.
     [:meta     :blob]                     ; Clojure map, fully encrypted.
     [:data     :blob]                     ; Additional data field, unencrypted and not used.
     [:tasklist :blob]]                    ; Clojure map, loaded once only on startup,
    {:conditional? true})                  ; and backup on preset interval afterward.
   (jdbc/create-table-ddl
     :setting
     [[:id   :integer :primary :key]
      [:data :blob]]                       ; Clojure map
     {:conditional? true})
   (jdbc/create-table-ddl
     :exchange
     [[:timestamp :integer :primary :key]  ; unix timestamp
      [:data      :blob]]                  ; Clojure map, data can be mixed.
     {:conditional? true})                 ; eg. Order hist and Market ticks mixed (or miss 1)
   (jdbc/create-table-ddl
     :task                                 ; Record all task, for reference purpose.
     [[:id       :integer :primary :key]
      [:state    :text]                    ; Active, cancelled, done.
      [:session  :text]                    ; Session id, the creator. Eg admin
      [:created  :integer]                 ; timestamp
      [:interval :integer]                 ; Execution interval in millisec.
      [:last     :integer]                 ; Last time executed.
      [:data     :blob]]                   ; Raw clojure command string.
     {:conditional? true})])

;; Database transaction, call tx-open -> normal db operations -> tx-commit
; http://clojure-doc.org/articles/ecosystem/java_jdbc/reusing_connections.html
(def ^:dynamic tx-con nil)

(defn get-con 
  "Return db-connection based on whether we are currently in a tx or not.
   If not in tx, return `db-spec` to create new one time tx."
  []
  (if (nil? tx-con)
    db-spec
    tx-con))

; Note: If operation uses `db-spec` directly instead of calling `get-con`, that op will run in its own tx.
(defmacro tx-open 
  "Open new tx and setup shared tx-con, use it in place of db/db-spec for each db operation."
  [& exprs]
  `(jdbc/with-db-connection [db-con# (get-con)]   ; get-con will return db-spec as it is guarenteed nil now.
     (.setAutoCommit (:connection db-con#) false)
     (binding [tx-con db-con#]                    ; rebind get-con, all succeeding get-con returns db-con#.
       (do ~@exprs))))

; Strip out useless layer of mapping from original query result.
; query :                   ["SELECT tasklist FROM session WHERE id = ?" "admin"]
; default-result:           ({:tasklist "[2 1 3 4 5]"})
; `return-as-list = false`: [2 1 3 4 5]
; `return-as-list = true` : ([2 1 3 4 5])
; If multiple-res and `return-as-list = false`: [2 1 3 4 5]   <- Note: return only first entry.
; If multiple-res and `return-as-list = true` : ([2 1 3 4 5] [2 1 3 4 5] [2 1 3 4 5])
(defn- query-parse [sql return-as-list]
  (let [from   (-> sql first (clojure.string/split #" ") second) ; returns "tasklist".
        key    (keyword from)
        result (jdbc/query (get-con) sql)] ; Actual db query.
    (if (= from "*")  ; if * (select all), just bypass the wrapping () list and return inner result map.
      (if return-as-list
        result
        (first result)) ; Result will be a list even if only contain 1 item.
        
      ; Given: ({:data "[1 2 3]"}) -> [1 2 3], where in this case `key` = :data
      (if-not (empty? (-> result first key)) ; read-string as result are stored as string, convert to clojure datatype.
        (if return-as-list
          (if (-> result count (> 1))
            (for [res result]
              (-> res first key read-string))
            (into [] (-> result first key read-string)))
          (-> result first key read-string))))))

(defn query-in [sql]
  (query-parse sql false))

(defn query [sql]
  (query-parse sql true))
