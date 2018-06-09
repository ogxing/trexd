(ns trexd.server-api
  (:require [trexd.aes :as aes])
  (:require [trexd.db :as db])
  (:require [clojure.java.jdbc :as jdbc])
  (:gen-class))

;; *meta* is not accessible by nrepl as it is intentionally thread local by binding.
;; This makes sure remote user can't access pub/prikey, and have to provide
;; their password to decrypt the keys if they want to use exchange api on the fly.
(def ^:dynamic *meta*) ; Shared metadata containing decrypted pub/prikey on server thread only.
(def ^:private nmeta (atom nil))  ; Set by remote nrepl user, to access exchange api. Decrypted metadata.

(def paused (atom false))
(def halt (atom false))
(defn resume []
  (if @paused 
    (do 
      (swap! paused not)
      (reset! nmeta nil) ; Reset so future login cannot eavesdrop this.
      (println "Resumed!"))
    (println "Already resumed!")))
      
(def resume-time (atom 0))
; Auto call `resume` after certain amount of time.
(defn auto-resume []
  (future
    (while (< (System/currentTimeMillis) @resume-time)
      (Thread/sleep 15000))
    (resume)))

(defn pause []
  ; Resume after 1min if user doesn't respond by calling this function again.
  (reset! resume-time (+ (System/currentTimeMillis) 60000))
  ; Start auto-resume thread to resume after certain time after user disconnect.
  (if-not @paused
    (do
      (swap! paused not) ; Blocking wait until service loop frees the locked `paused` atom.
      (auto-resume)
      (println "Paused!"))
    (println "Already paused!")))

(defn halt-all [] (swap! halt not))

(defn paused? [] @paused)
(defn halt? [] @halt)  

(defn decrypt-meta [user-id password]
  (let [session (db/query-in ["SELECT * FROM session WHERE id = ?" user-id])]
     (try
       ; Try to decrypt then parse the metadata back to clojure map.
       (read-string (aes/decrypt (:meta session) password))
       ; Decryption failed, ask user to login again.
       (catch Exception e nil))))

; Login verification by decrypting user metadata,
; metadata will be gibberish if wrong password provided.
(defn login-verify [user-id password]
  (let [meta (decrypt-meta user-id password)]
    (not (nil? meta))))

; TODO: Make login page, currently allow direct login.
; Do so by changing server.clj setmeta to set meta to *meta* instead of shared metadata.
; After that replace `getmeta` and `setmeta` with the commented version below.
(comment
  (defn getmeta [])
  (if @paused ; Check if user is online, Main is paused means user is online.
    @nmeta
    *meta*)
  
  (defn setmeta
    "Set metadata of the api to this user, password required to decrypt the session.
     Call to enable direct exchange api usage."
    ; Called by nrepl logon user only (external use).
    [user-id password]
    (let [meta (decrypt-meta user-id password)]
      (if-not (nil? meta)
        (reset! nmeta meta)
        (println "Wrong ID or password!")))))

(def non-login-meta (atom nil))
(defn getmeta []
  @non-login-meta)

(defn setmeta
    "Set metadata of the api to this user, password required to decrypt the session.
     Call to enable direct exchange api usage."
    ; Called by nrepl logon user only (external use).
    [user-id password]
    (let [meta (decrypt-meta user-id password)]
      (if-not (nil? meta)
        (reset! non-login-meta meta)
        (println "Wrong ID or password!"))))

(defn- insert-result->id
  "Convert jdbc sqlite insert! result to id.
   Example result: ({:last_insert_rowid() 2})  =>  2"
  [insert-result]
  (first (vals (first insert-result))))

(defn update-active-task-id [user-id active-task-id-list]
  (jdbc/update! (db/get-con) :session {:tasklist active-task-id-list} ["id = ?" user-id]))

(defn add-active-task-id [user-id id]
  (let [task-ids     (db/query-in ["SELECT tasklist FROM session WHERE id = ?" user-id])
        updated-list (if (empty? task-ids) [id] (conj task-ids id))]
    (jdbc/update! (db/get-con) :session {:tasklist updated-list} ["id = ?" user-id])))

(defn remove-active-task-id [user-id id]
  (let [task-ids     (db/query-in ["SELECT tasklist FROM session WHERE id = ?" user-id])
        updated-list (if (empty? task-ids) [] (into [] (remove #{id} task-ids)))]
    (jdbc/update! (db/get-con) :session {:tasklist updated-list} ["id = ?" user-id])))

(defn register-task
  "Register new task and post it to DB, update user's active tasklist. Returns task' new id."
  ([task] ; Get session by binded global variable set by currently active user.
   (register-task task (:id (getmeta)))) 
  
  ([task user-id]
   ; If interval exist, use it. 
   (if-not (nil? (get task :interval nil))
     (register-task task user-id (:interval task))
     (register-task task user-id 60000)))
  ([task user-id interval]
   (let [id (insert-result->id (jdbc/insert! (db/get-con) :task      
                                 {:state "active"
                                  :session   user-id
                                  :created   (System/currentTimeMillis)
                                  :interval  interval
                                  :last      0
                                  :data      task}))]
     (add-active-task-id user-id id)
     (println "Task Registered: " task)
     id)))

(defn check-tasks-id
  "Check if all tasks possess proper ID and state.
  If they don't (new task), register them to DB."
  [task-list]
  (for [task task-list]
    (if (or (nil? (:id task)) 
            (not  (integer? (:id task))))
        (register-task task))))

; TODO: Encrypt task using user credentials.
(defn update-task
  "Update whole task, expecting a complete task structure (fetch, edit then call this update)."
  [task-data user-id]
  (let [{:keys [id state session interval last data]} task-data]
    ; ID and Created (timestamp) field should be static thus ignored in update.
    (jdbc/update! (db/get-con) :task {:state    state
                                      :session  session
                                      :interval interval
                                      :last     last
                                      :data     data}
      ["id = ?" id])))

(defn remove-task
  "Remove actual task and optionally remove any entries in user's session that point to that task."
  ([task-id]
   (jdbc/delete! (db/get-con) :task ["id = ?" task-id]))
  ([task-id user-id]
   (remove-task task-id)
   (remove-active-task-id user-id task-id)))

(defn ids->impls
  "Fetch task implementation by ID. Expect a list."
  [task-ids]
  (doall (for [task-id task-ids] (db/query-in ["SELECT * FROM task WHERE id = ?" task-id]))))

(defn fetch-tasks
  "nRepl uses, remotely fetch active tasks."
  [user-id]
  (ids->impls (db/query-in ["SELECT tasklist FROM session WHERE id = ?" user-id])))

(defn create-session
  "Register new session."
  [new-id password pubkey prikey exchange]
  (jdbc/insert! (db/get-con) :session {:id   new-id
                                       :meta (str (aes/encrypt {:pubkey   {:bittrex pubkey}
                                                                :prikey   {:bittrex prikey}
                                                                :exchange exchange} password))}))

; Reset main loop if user changes server password as all metadata will be re-encrypted.
(def main-reset-password (atom nil))
(defn main-reset? []
  (not (nil? @main-reset-password)))
(defn get-main-reset-new-password []
  (let [password @main-reset-password]
    (reset! main-reset-password nil)
    password))

; Update server-password first if requested, as it is used to encrypt all metadata.
; After that update all remaining credentials by reseting metadata directly.
; TODO: `setmeta` admin change to real-current user id in future.
(defn update-setting [user-id pubkey prikey email-user email-pass email-target server-pass current-pass]
  (if-not (login-verify "admin" current-pass)
    (throw (IllegalStateException. "Wrong password")))  
  (if-not (empty? server-pass)
    (let [session          (db/query ["SELECT * FROM session"])
          reencrypted-meta (str (aes/encrypt (getmeta) server-pass))]
      (jdbc/update! (db/get-con) :session 
        (merge session {:meta reencrypted-meta})
        ["id = ?" user-id])
      (reset! main-reset-password server-pass))
    (setmeta "admin" server-pass))
  
  (let [session      (first (db/query ["SELECT * FROM session"]))
        update-map   {:pubkey pubkey :prikey prikey
                      :emailuser email-user :emailpass email-pass :emailtarget email-target}
        ; Remove "" empty string and nil.
        filtered-map (into {} (remove #(let [val (second %1)]
                                         (or (= val "") (= val nil)))) update-map)
        _ (println "Filtered map  " filtered-map)
        _ (println "getmeta  " (getmeta))
        ; `getmeta` returns decrypted metadata, need to re-encrypt it before storing to db.
        updated-meta   (merge (getmeta) filtered-map)
        _ (println "updated-meta  " updated-meta)
        encrypted-meta (str (aes/encrypt updated-meta current-pass))
        _ (println "encrypted-meta  " encrypted-meta)]
    (jdbc/update! (db/get-con) :session (merge session {:meta encrypted-meta}) ["id = ?" user-id])
    (setmeta "admin" current-pass)))
