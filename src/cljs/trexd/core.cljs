(ns trexd.core
  (:require [reagent.core :as r]
            [soda-ash.core :as sa]
            [ajax.core :refer [GET POST]]
            [trexd.util :as u]
            [trexd.shared :as s]
            [trexd.alert :as alert]
            [trexd.task :as task]
            [trexd.order :as order]
            [trexd.setting :as setting]
            [trexd.repl :as repl]))            

(enable-console-print!)

(defn menu [active-page]
  [sa/Menu
   [sa/MenuItem {:name "tasks" :active (= active-page "tasks")
                 :onClick #(u/update-state "tasks" [:active-page])}]
   [sa/MenuItem {:name "alert" :active (= active-page "alert")
                 :onClick #(u/update-state "alert" [:active-page])}]
   [sa/MenuItem {:name "order" :active (= active-page "order")
                 :onClick #(u/update-state "order" [:active-page])}]
   [sa/MenuItem {:name "setting" :active (= active-page "setting")
                 :onClick #(u/update-state "setting" [:active-page])}]
   [sa/MenuItem {:name "repl" :active (= active-page "repl")
                 :onClick #(do (repl/repl-toggle))}]])

(defn content-page []
  (fn []
    (let [active-page (:active-page @s/app-state)]
      [:div
       [menu active-page]
       (cond
          (= active-page "tasks")   [task/setup-tasks-page]
          (= active-page "alert")   [alert/setup-alert-page]
          (= active-page "setting") [setting/setup-setting-page]
          (= active-page "order")   [order/setup-order-page])])))
  
(defn render []
  (r/render-component [content-page] (.querySelector js/document "#content")))

(render)

; Send periodic `pause` command to server as heartbeat. Server will `resume` automatically if no response after 2 min.
(defn heartbeat-pause []
  (POST "/"
    {:params (str {:cookies "cookies"
                   :data    (u/func->string "pause" [])})
     :handler #(println "Heartbeat " (.getTime (js/Date.)))
     :error-handler #(println %1)}))
(heartbeat-pause)
(js/setInterval heartbeat-pause 30000)

; http://www.spacjer.com/blog/2014/09/12/clojurescript-javascript-interop/
; https://github.com/reagent-project/reagent/blob/master/doc/ManagingState.md
;https://github.com/gadfly361/soda-ash
;https://react.semantic-ui.com/introduction

;https://reagent-project.github.io/
