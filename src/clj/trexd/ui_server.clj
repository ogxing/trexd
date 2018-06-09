(ns trexd.ui-server
  (:use [org.httpkit.server])
  (:use [ring.middleware.resource])
  (:use [ring.util.response :only [redirect]])
  (:use [compojure.route :only [not-found]])
  (:use [compojure.core :only [defroutes GET POST context]])
  (:use [ring.middleware.file :only [wrap-file]])
  (:use [clojure.java.io :only [resource]])
  (:require [trexd.execute-string :as es])
  (:gen-class))

;; A webserver to serve https UI inteface.
(defonce server (atom nil))

(defn authenticated? [cookie]
  true)

(defn process-request [req]
  ;From: ["~#'","{:message {:ha 123, :bb 2313, :c 23}, :user [123 444 777], :cow \"moo\"}"]
  ;To  : {:message {:ha 123, :bb 2313, :c 23}, :user [123 444 777], :cow "moo"}
  (let [response-body (slurp (:body req))
        params        (read-string (second (read-string response-body)))]
    (println params)
    (if (authenticated? (:cookies params))
      (str {:data (es/execute-string (:data params))})
      {:status 403 :headers {"Content-Type" "text/plain"} :body "Forbidden"})))

(defroutes all-routes
  ; This command magically creates `public` directory in `target`,
  ; same result as at `cp ./resources/public/* ./target/public/*`,
  ; and all compiled js file also magically appears in it (instead of at `target/js_files`).
  (GET "/" [] (-> "public/index.html" resource slurp))
  (POST "/" req (process-request req))
  (not-found "<p>Page not found.</p>"))

(defn stop-server []
  (when-not (nil? @server)
    ;; graceful shutdown: wait 100ms for existing requests to be finished
    ;; :timeout is optional, when no timeout, stop immediately
    (@server :timeout 100)
    (reset! server nil)))

(defn start []
  ; https://stackoverflow.com/questions/44672281/wrap-file-through-a-specific-route?rq=1
  (if (nil? @server)
    (reset! server (run-server (wrap-file #'all-routes "target/public") {:port 8080}))))
