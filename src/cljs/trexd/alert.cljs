(ns trexd.alert
  (:require [reagent.core :as r]
            [soda-ash.core :as sa]
            [ajax.core :refer [GET POST]]
            [cljs.reader :as reader]
            [trexd.util :as u]
            [trexd.shared :as s])) 

(defn fetch-alert-data [page-index]
  (POST "/"
    {:params (str {:cookies "cookies"
                   :data    (cond
                              (= page-index 0) (u/func->string "active-alert-pair" ["admin"]) ; Return actual task.
                              (= page-index 1) (u/func->string "no-alert-pair"     ["admin"]) ; Return list of pairs [BTC-LTC BTC-DOGE ..]
                              (= page-index 2) (u/func->string "since-last-pair"   ["admin"]))}) ; Return actual task.
     :handler (fn [arg1]
                (let [response    (-> arg1 reader/read-string :data)
                      ; Add element-id to each data.
                      with-id   (if (= page-index 1)
                                  (into (sorted-map) (for [data response :let [eid (u/gen-id)]] {eid {:eid eid :pair data}}))
                                  (into (sorted-map) (for [data response :let [eid (u/gen-id)]] {eid (merge data {:eid eid})})))
                      _         (u/update-state with-id [:alert-segment :data] true)]))
     :error-handler #(println %1)}))


(defn remove-alert-data-entry [entry-id]
  (u/remove-state-entry entry-id [:alert-segment :data]))

(defn draw-add-alert-modal []
  (let [state (r/atom false)
        rate  (r/atom "")
        last  (r/atom "")
        pair  (r/atom "")
        save  (r/atom "save")]
    (fn []
      [:div
       [sa/SegmentGroup
        [sa/Segment
         [:div [:b "Exchange: "] "Bittrex"]
         [:div [:b "Pair    : "] [sa/Dropdown {:selection    true
                                               :search       true
                                               :disabled     (= @save "saving")
                                               :defaultValue -1
                                               :options      (for [{:keys [pair id]} (vals @u/shared-summary)]
                                                               {:text pair :value id})
                                               ; Fetch and update `last` tick value.
                                               :onChange     #(let [pair-last-map (@u/shared-summary (.-value %2))]
                                                                (reset! pair (:pair pair-last-map))
                                                                (reset! last (-> pair-last-map
                                                                                 :last
                                                                                 u/float->string))
                                                                (reset! save "save"))}]]
         [:div [:b "> | <   : "] [sa/Button {:toggle   true 
                                             :active   (not @state)
                                             :disabled (= @save "saving")
                                             :onClick  #(do
                                                          (swap! state not)
                                                          (reset! save "save"))}
                                  (if @state "<" ">")]]
         [:div [:b "Rate    : " [sa/Input {:value    @rate
                                           :disabled (= @save "saving")
                                           :onChange #(do 
                                                        (reset! rate (.-value %2))
                                                        (reset! save "save"))}]]]
         [:div [:b "Last    : "] [:a {:onClick #(reset! rate @last)} @last]]
         [:div [:b "Link    : "] [:a {:href    (str "https://bittrex.com/Market/Index?MarketName=" pair)} "Link"]]
         [:div [sa/Button {:toggle   true 
                           :active   (not (or (u/blank? @rate) (u/blank? @pair)))
                           :disabled (or (u/blank? @rate) (u/blank? @pair) (not= @save "save"))
                           :loading  (= @save "saving")
                           :onClick  (fn []
                                       (do
                                         (reset! save "saving")
                                         (POST "/"
                                           {:params (str {:cookies "cookies"
                                                          :data    (if @state
                                                                     (u/gen-inner-add-task "alert<" ["bittrex" @pair @rate])
                                                                     (u/gen-inner-add-task "alert>" ["bittrex" @pair @rate]))})
                                            :handler #(reset! save "done")
                                            :error-handler #(println %1)})))}
                (cond
                  (= @save "save")   "Save"
                  (= @save "saving") "Saving"
                  (= @save "done")   "Done")]]]]])))

(defn draw-alert-segment [data unassoc]
  ; Destructure task of type `alert`, if unassoc, data will be list of currency pairs,
  ; if so the f-name f-exchange... will be nil. No problem.
  (let [[f-name f-exchange f-pair f-rate] (u/string->func (:data data))
        e-id    (:eid data) ; For removing ui entries.
        task-id (:id data)  ; Actual existing task's id, used for update.
        state   (r/atom (= f-name "alert<")) ; load < or >.
        rate    (r/atom (if-not unassoc f-rate ""))
        save    (r/atom "save")
        delete  (r/atom "")
        pair    (if unassoc (:pair data) f-pair)
        summary (u/get-summary pair)
        last    (:last summary)]
    (fn [data unassoc] ; data can be a registered task OR currency pair (in case of unassociated alert page)
      [:div
       [sa/SegmentGroup
        [sa/Segment
         [:div [:b "Exchange: "] "Bittrex"]
         [:div [:b "Pair    : "] pair]
         [:div [:b "> | <   : "] [sa/Button {:toggle   true
                                             :active   (not @state)
                                             :disabled (= @save "saving")
                                             :onClick  #(do 
                                                          (swap! state not)
                                                          (reset! save "save")
                                                          (reset! delete ""))}
                                  (if @state "<" ">")]]
         [:div [:b "Rate    : " [sa/Input {:disabled (= @save "saving")
                                           :value    @rate
                                           :onChange #(do
                                                        (reset! rate (.-value %2))
                                                        (reset! save "save")
                                                        (reset! delete ""))}]]]
         [:div [:b "Last    : "] [:a {:onClick #(reset! rate (u/float->string last))} (u/float->string last)]]
         [:div [:b "Link    : "] [:a {:href (str "https://bittrex.com/Market/Index?MarketName=" pair)} "Link"]]
         [:div [sa/Button {:toggle   true 
                           :active   (not (u/blank? @rate))
                           :disabled (or (u/blank? @rate) (not= @save "save"))
                           :loading  (= @save "saving")
                           :onClick  (fn []
                                       (do
                                         (reset! save "saving")
                                         (POST "/"
                                           {:params (str {:cookies "cookies"
                                                          ; Unassoc then create new task, else update task
                                                          :data    (if unassoc
                                                                     (if @state
                                                                       (u/gen-inner-add-task "alert<" ["bittrex" pair @rate])
                                                                       (u/gen-inner-add-task "alert>" ["bittrex" pair @rate]))
                                                                     ; Update :data part of task only.
                                                                     (u/func->string "update-task" [(merge data {:data (u/func->string (if @state "alert<" "alert>") ["bittrex" pair @rate])})
                                                                                                    "admin"]))})
                                            :handler #(do
                                                        (reset! save "done")
                                                        (if unassoc (remove-alert-data-entry e-id))) ;remove the entry as it is not longer an unassociated pair.
                                            :error-handler #(println %1)})))}
                (cond
                  (= @save "save")   "Save"
                  (= @save "saving") "Saving"
                  (= @save "done")   "Done")]
          [sa/Button {:negative true
                      :disabled unassoc
                      :content  @delete
                      :icon     "remove"
                      :onClick  (fn []
                                  (if (u/blank? @delete)
                                    (reset! delete "Remove?")
                                    (POST "/"
                                      {:params (str {:cookies "cookies"
                                                     :data    (u/func->string "remove-task" [task-id "admin"])})
                                       :handler #(remove-alert-data-entry e-id)
                                       :error-handler #(println %1)})))}]]]]])))

(def alert-last-page (atom nil))
(defn setup-alert-page []
  (fn []
    [:div
     [sa/Header {:as "h3"} "Alerts"]
     [sa/Dropdown {:selection true :defaultValue @alert-last-page
                   :options [{:text "Active" :value 0}
                             {:text "Unassociated" :value 1}
                             {:text "Since Last" :value 2}]
                   :onChange #(let [page-index (.-value %2)]
                                ; Do not refetch data if page not changed.
                                (if (or (nil? @alert-last-page) (not= page-index @alert-last-page))
                                  (do
                                    (reset! alert-last-page page-index)
                                    (fetch-alert-data page-index))))}]
     [sa/Modal {:size "fullscreen" :trigger (r/as-element [sa/Button {:icon "add"}])}
      [sa/ModalHeader "Add Alert"]
      [draw-add-alert-modal]]
     ; On data change, re-draw (generate) updated segments.
     ; Metadata to avoid "all seq should have a key" warning.
     (let [data-list  (-> @s/app-state :alert-segment :data vals)]
       (for [data data-list]
         ^{:key (:eid data)} [draw-alert-segment data (= @alert-last-page 1)]))]))
