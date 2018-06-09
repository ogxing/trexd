(ns trexd.notification
  (:require [postal.core :as email])
  (:require [trexd.server-api :as sa])
  (:gen-class))

(defn email [subject msg]
  (let [email-user   (-> sa/getmeta :emailuser)
        email-pass   (-> sa/getmeta :emailpass)
        email-target (-> sa/getmeta :emailtarget)]
    (email/send-message 
      {:host "smtp.gmail.com"
       :user email-user
       :pass email-pass
       :ssl true}
      {:from email-user
       :to email-target
       :subject subject
       :body msg})))

(defn notify [subject msg]
  (email subject msg))
