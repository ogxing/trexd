(ns trexd.server
  (:require [trexd.exchange-api :as exchange])
  (:require [trexd.aes :as aes])
  (:require [trexd.db :as db])
  (:require [trexd.server-api :as sa])
  (:require [trexd.execute-string :as es])
  (:require [trexd.task :as t])
  (:require [clojure.java.jdbc :as jdbc])
  (:gen-class))

(defn- is-time? [interval last]
  (> (- (System/currentTimeMillis) last) interval))

;; Main infinite service loop.
;; Password required to decrypt metadata, it is not accessible by nrepl.
(defn service-loop [password]  
  (println "Service loop started.")
  
  ; Begin task evaluation.
  (while (and (not (sa/halt?)) (not (sa/main-reset?)))
    ; Execute and filter out completed tasks.
    (if-not (sa/paused?)
      (locking sa/paused ; lock to prevent user intevention during evaluation
        (doall (for [session (db/query ["SELECT * FROM session"])]
                 (do
                   ; Set decrypted session specific metadata to api.
                   ;(set! sa/*meta* (sa/decrypt-meta (:id session) password))
                   (sa/setmeta (:id session) password)
                   (db/tx-open
                     ; Execute and update task states.
                     (->> (for [task (if-not (nil? (:tasklist session))
                                       (sa/ids->impls (read-string (:tasklist session))))]
                            (let [task-impl     (:data     task)
                                  task-last     (:last     task)
                                  task-interval (:interval task)]
                              ; Check is time to run the task, if not just add it to list to run afterwards.
                              (if (is-time? task-interval task-last)
                                ; Run the task once, if task wants to rerun by not returning `done`, add it to list.
                                (if-not (t/done? (es/execute-string task-impl))
                                  (do
                                    (sa/update-task (merge task {:last (System/currentTimeMillis)}) (:id task))
                                    (:id task))
                                  (do
                                    (sa/update-task (merge task {:last  (System/currentTimeMillis)
                                                                 :state "done"})
                                      (:id task))
                                    nil))
                                ; Task not yet time to run, add to list to run afterward.
                                (:id task))))
                          ; Update user's active task id list.
                          (remove nil?)
                          (into [])
                          (sa/update-active-task-id (:id session)))))))))
    (Thread/sleep 1000))
  
  ; When user update main loop password, reset the whole loop.
  (if (sa/main-reset?)
    (recur (sa/get-main-reset-new-password))))

(defn is-true? [s]
  (let [ls (clojure.string/lower-case s)]
    (or (= ls "true") (= ls "t") (= ls "y") (= ls "yes"))))

(defn start
  "Manage server startup, login, restore and resume previous tasks.
   Should be called as-is. (do not wrap in extra thread)
   If first time setup, will ask user to create new admin account."
  []
  (println "BittrexDaemon V0")
  (println "Loading settings...")
  ; Create db and tables if not exist.
  (jdbc/db-do-commands (db/get-con) db/db-create-tables)
  
  ; Create admin account if not exist (first time login)
  (if (empty? (db/query-in ["SELECT * FROM session WHERE id = 'admin'"]))
    (do
      (println "First time setup?  (No admin account found)")
      (println "NOTE: Admin Password will be used to encrypt all credentials!")
      (println "NOTE: All credentials can be updated in setting page if needed.")
      (let [adminpass   (do (print "New admin password : ") (flush) (read-line))
            pubkey      (do (print "Bittrex public key : ") (flush) (read-line))
            prikey      (do (print "Bittrex private key: ") (flush) (read-line))
            setup-email (is-true? (do (print "Setup email notification? (y/n): ") (flush) (read-line)))
            emailuser   (if setup-email (do (print "Sender Email Addr  : ") (flush) (read-line)))
            emailpass   (if setup-email (do (print "Sender Email Pass  : ") (flush) (read-line)))
            emailtarget (if setup-email (do (print "Receiver Email Addr: ") (flush) (read-line)))]
        (jdbc/insert! (db/get-con) :session {:id   "admin"
                                             :meta (str (aes/encrypt {:pubkey      pubkey
                                                                      :prikey      prikey
                                                                      :exchange    "bittrex"
                                                                      :emailuser   emailuser
                                                                      :emailpass   emailpass
                                                                      :emailtarget emailtarget}
                                                          adminpass))})
        ; Create default long term task then add it to active task list.
        (if (is-true? (do (print "Create long running data collection task? (y/n): ") (flush) (read-line)))
          (do
            (sa/register-task "(data-collect)" "admin" 60000)
            (println "Data collection task created.")))
        (if (is-true? (do (print "Create auto order refresh task? (y/n): ") (flush) (read-line)))
          (do
            (sa/register-task "(refresh-orders)" "admin" 864000000) ; Every 10 days.
            (println "Refresh order task created.")))
        (if (is-true? (do (print "Create notify order complete task? (y/n): ") (flush) (read-line)))
          (do
            (sa/register-task "(notify-order-complete)" "admin" 120000) ; Every 2 min
            (println "Notify when order complete task created."))))
      (println "Admin account created, please login.")))
  
  ; Admin login, success if metadata decrypt without error.
  (loop []
    (let [password (do (print "Admin pass: ") (flush) (read-line))
          success  (sa/login-verify "admin" password)]
      (if success
        (do
          (println "Login success.")
          ; Decrypt meta once, so `getmeta` wouldn't return nil after login.
          (sa/setmeta "admin" password)
          (service-loop password))
        (do
          (println "Wrong Password!")
          (recur)))))
  (println "Main Halted!"))
