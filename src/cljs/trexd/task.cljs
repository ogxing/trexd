(ns trexd.task
  (:require [reagent.core :as r]
            [soda-ash.core :as sa]
            [ajax.core :refer [GET POST]]
            [cljs.reader :as reader]
            [trexd.util :as u]
            [trexd.shared :as s])) 

(defn fetch-task-data []
  (POST "/"
    {:params (str {:cookies "cookies"
                   :data    (u/func->string "fetch-tasks" ["admin"])})
     :handler (fn [arg1]
                (let [response    (-> arg1 reader/read-string :data)
                      ; Add id to each data.
                      with-id   (into (sorted-map) (for [data response :let [eid (u/gen-id)]] {eid (merge data {:eid eid})}))
                      _         (u/update-state with-id [:task-segment :data] true)]))
     :error-handler #(println %1)}))

(defn remove-task-data-entry [entry-id]
  (u/remove-state-entry entry-id [:task-segment :data]))

; Used for both edit and create new task.
(defn draw-task-modal [task-data]
  (let [{:keys [eid id state session created interval last data]} task-data
        add-task   (u/blank? task-data)
        a-interval (r/atom (if add-task "" interval))
        a-data     (r/atom (if add-task "" data))
        save       (r/atom "save")]
    (fn [task-data]
      [:div
       [sa/SegmentGroup
        [sa/Segment
         (if-not add-task
           [:div
            [:div [:b "ID       : "] id]
            [:div [:b "State    : "] state]
            [:div [:b "Session  : "] session]
            [:div [:b "Created  : "] created]
            [:div [:b "Last     : "] last]])
         [:div [:b "Interval : "] [sa/Input {:value    @a-interval
                                             :onChange #(do 
                                                          (reset! save "save")
                                                          (reset! a-interval (.-value %2)))}]]
         [:div [:b "Data     : "] [sa/Input {:value    @a-data
                                             :onChange #(do 
                                                          (reset! save "save")
                                                          (reset! a-data (.-value %2)))}]]
         [sa/Button {:toggle   true 
                     :active   (not (u/blank? @a-data))
                     :disabled (or (u/blank? @a-data) (not= @save "save"))
                     :loading  (= @save "saving")
                     :onClick  (fn []
                                 (do
                                   (reset! save "saving")
                                   (POST "/"
                                     {:params (str {:cookies "cookies"
                                                    :data    (if add-task 
                                                               (u/gen-add-task [@a-data interval])
                                                               (u/func->string "update-task" [(merge task-data {:data @a-data :interval @a-interval}) "admin"]))})
                                      :handler #(reset! save "done") 
                                      :error-handler #(println %1)})))}
          (cond
            (= @save "save")   "Save"
            (= @save "saving") "Saving"
            (= @save "done")   "Done")]]]])))

(defn draw-task-segment [task-data]
  (let [{:keys [eid id state session created interval last data]} task-data
        delete (r/atom "")]
    (fn [task-data]
      [:div
       [sa/SegmentGroup
        [sa/Segment
         [:div [:b "ID       : "] id]
         [:div [:b "State    : "] state]
         [:div [:b "Session  : "] session]
         [:div [:b "Created  : "] created]
         [:div [:b "Interval : "] interval]
         [:div [:b "Last     : "] last]
         [:div [:b "Data     : "] data]
         [sa/Button {:negative true
                     :content  @delete
                     :icon     "remove"
                     :onClick  (fn []
                                 (if (u/blank? @delete)
                                   (reset! delete "Remove?")
                                   (POST "/"
                                     {:params (str {:cookies "cookies"
                                                    :data    (u/func->string "remove-task" [id "admin"])})
                                      :handler #(remove-task-data-entry eid)
                                      :error-handler #(println %1)})))}]
         [sa/Modal {:size "fullscreen" :trigger (r/as-element [sa/Button {:content  "Edit"
                                                                          :icon     "edit"}])}
          [sa/ModalHeader "Edit Task"]
          [draw-task-modal task-data]]]]])))

(defn setup-tasks-page []
  (fetch-task-data)
  (fn []
    [:div
     [sa/Header {:as "h3"} "Tasks"]
     [sa/Modal {:size "fullscreen" :trigger (r/as-element [sa/Button {:positive true
                                                                      :content  "Create New"
                                                                      :icon     "add"}])}
      [sa/ModalHeader "New Task"]
      [draw-task-modal]]
     ; On data change, re-draw (generate) updated segments.
     ; Metadata to avoid "all seq should have a key" warning.
     (let [data-list  (-> @s/app-state :task-segment :data vals)]
       (for [data data-list]
         ^{:key (:id data)} [draw-task-segment data]))]))
