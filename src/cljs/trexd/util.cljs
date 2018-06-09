(ns trexd.util
  (:require [goog.object :as g]
            [goog.string :as gstring]
            [goog.string.format]
            [ajax.core :refer [GET POST]]
            [cljs.reader :as reader]
            [trexd.shared :as s])) 

; https://stackoverflow.com/questions/14488150/how-to-write-a-dissoc-in-command-for-clojure
(defn dissoc-in
  "Dissociates an entry from a nested associative structure returning a new
  nested structure. keys is a sequence of keys. Any empty maps that result
  will not be present in the new structure."
  [m [k & ks :as keys]]
  (if ks
    (if-let [nextmap (get m k)]
      (let [newmap (dissoc-in nextmap ks)]
        (if (seq newmap)
          (assoc m k newmap)
          (dissoc m k)))
      m)
    (dissoc m k)))

; http://javadevnotes.com/java-float-to-string-without-exponential-scientific-notation
(defn float->string [num] (if-not (nil? num) (gstring/format "%.8f" num)))

(defn remove-last-char [str]
  (.substring str 0 (- (count str) 1)))

; (alert> "123" "BTC") -> ["alert>" "123" "BTC"], turn into list of strings.
; If no parameter (println), just convert directly -> ["println"].
(defn string->func [func-str]
  (if-not (nil? func-str)
    (let [index-of-first-whitespace (clojure.string/index-of func-str " ")
          first-half                (.substring func-str 1 index-of-first-whitespace)
          remaining                 (.substring func-str index-of-first-whitespace (- (count func-str) 1))
          stringified-func-str      (if (nil? index-of-first-whitespace) 
                                      (str "[\"" (.substring func-str 1 (- (count func-str) 1)) "\"]")
                                      (str "[\"" first-half "\"" remaining "]"))]
      (reader/read-string stringified-func-str))))

; (alert> "123" "BTC") -> "\"(alert> \\\"123\\\" \\\"BTC\\\")"\""
; For embed inside (register-task data), where data should be double escaped.
(defn inner-func->string
  [func param]
  (str "(" func (apply str (for [p param] (if (string? p) 
                                            (str " \\\"" p "\\\"")
                                            (str " " p))))
    ")"))

; (alert> "123" "BTC") -> "\"(alert> \"123\" \"BTC\")"\""
; Escape all string params.
(defn func->string 
  [func param]
  (str "(" func (apply str (for [p param] (if (string? p) 
                                            (str " \"" p "\"")
                                            (str " " p))))
    ")"))

; Generate task template for adding task to server.
(defn gen-inner-add-task
  ([[func-string interval]]
   (func->string "register-task" [func-string "admin" interval]))
  ([func param]
   (gen-inner-add-task func param nil))
  ([func param interval]
   (gen-inner-add-task [(inner-func->string func param) interval])))

; Convert (println 123 "123") -> ["println" 123 "123"] -> "(register-task \"(println 123 \"123\")\" interval\")"
(defn gen-add-task [[func-param interval]]
  (let [func-string-vector (string->func func-param)]
    (gen-inner-add-task (first func-string-vector) (rest func-string-vector) interval)))

; Update global app-state, provide assoc-in 3 layer deep with optional replace capability.
(defn update-state
  ([value [k1 k2 k3]]
   (update-state value [k1 k2 k3] false))
  ([value [k1 k2 k3] replace]
   (if (nil? k3)
     ; If only 2 layer deep, use normal assoc in.
     (if (nil? k2)
       (if replace
         (do
           (swap! s/app-state dissoc [k1])
           (update-state value [k1 k2 k3] false))
         (swap! s/app-state assoc-in [k1] value))
       (if replace
         (do
           (swap! s/app-state dissoc-in [k1 k2])
           (update-state value [k1 k2 k3] false))
         (swap! s/app-state assoc-in [k1 k2] value)))
     ; If 3 layer, get the last layer (if exist) then merge it with a new map {k3 value}
     ; merge map so it preserve its original value if available, then replace
     ; {:k1 {:k2 {:k? previous-val}}} -> {:k1 {:k2 {:k? previous-val :k3 value}}}
     ; without merge the data will be replaced.
     (if replace
       (swap! s/app-state assoc-in [k1 k2] {k3 value})
       (swap! s/app-state #(assoc-in % [k1 k2] (merge (-> % k1 k2) {k3 value})))))))

; func ([:a :b] 2) => {:a {:b [1 2 3]}} -> {:a {:b [1 3]}}
(defn remove-state-entry
  [value [k1 k2]]
  (let [original-data (-> @s/app-state k1 k2)
        updated-data  (dissoc original-data value)]
    (update-state updated-data [k1 k2])))

; Store and generate Unique ID for each generated components.
(def id-counter (atom 0))
(defn gen-id[]
  (swap! id-counter inc))

; Latest summary for each pairs. Loaded once on startup only.
(def shared-summary (atom nil))
(defn fetch-summary []
  (POST "/"
    {:params (str {:cookies "cookies"
                   :data    (func->string "get-latest-summaries" [])})
     :handler (fn [arg1]
                (let [response    (-> arg1 reader/read-string :data)
                      counter     (atom 0)
                      indexed-map (into {} (for [data response] 
                                             (do (swap! counter inc) 
                                               {@counter (conj data {:id @counter})})))]
                  (reset! shared-summary indexed-map)))
     :error-handler #(println %1)}))
(fetch-summary)

; Get summary from local cached data. [pair] -> [{:pair :last :volume ...} {...}]
(defn get-summary [pair]
  (first (for [summary (vals @shared-summary)
               :when (= (:pair summary) pair)]
           summary)))

(defn get-all-pairs []
  (for [summary (vals @shared-summary)]
    (:pair summary)))

(defn blank? [data]
  (clojure.string/blank? data))
