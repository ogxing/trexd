(ns trexd.core
  ; `use` instead of `require` to simplify user web interaction (don't have to type ns).
  (:use [trexd.exchange-api])
  (:use [trexd.server-api])
  (:use [trexd.task])
  (:use [trexd.notification])
  (:use [clojure.tools.nrepl.server :only [start-server stop-server]])
  (:require [trexd.server :as s])
  (:require [trexd.ui-server :as uis])
  (:gen-class))

; Binding must be done at least once, then can be `set!` in the future.
(defn -main []
  ; Setup UI Server outside of binding so it can't access *meta* binding.
  (uis/start)
  
  ; Setup initial binding so future call to update metadata binding wouldn't fail.
  (binding [*meta* ()]
    (try
      (start-server :port 7888)
      (catch Exception e))
    (s/start)))
