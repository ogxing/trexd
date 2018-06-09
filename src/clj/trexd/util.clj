(ns trexd.util
  (:require [clj-http.client :as client])
  (:require [clojure.data.json :as json])
  (:require [clj-time.coerce :as t])
  (:require [trexd.hmac :as h])
  (:require [trexd.server-api :as sa])
  (:gen-class))

; http://javadevnotes.com/java-float-to-string-without-exponential-scientific-notation
(defn float->string [num] (format "%.8f" num))
(defn string->float [num] (read-string num))
; https://stackoverflow.com/questions/15077401/converting-date-to-epoch-time-in-clojure-or-jython/15078427#15078427
; https://stackoverflow.com/questions/6687433/convert-a-date-format-in-epoch
; https://github.com/clj-time/clj-time
; Time is in UTC.
(defn date->epoch
  "Convert bittrex GMT to unix epoch"
  [date]
  (t/to-long date))

(defn get-public-key []
  (:pubkey (sa/getmeta)))

(defn get-private-key []
  (:prikey (sa/getmeta)))

(defn sign-url [url]
  (h/hmac (get-private-key) url))

; Binding url means appending param to given url.
(defn bind-url-param [url params]
  (let [url-params (for [param (:query-params params)]
                     (str "&" (first param) "=" (second param)))]
    (str url "?" (subs (apply str url-params) 1))))

(defn post->map
  "Filter result from POST then convert them from json to clojure map."
  ([address]
   (json/read-str (:body (client/post address)) :key-fn keyword))
  ([address param]
   (json/read-str (:body (client/post address param)) :key-fn keyword)))

; Append public key to params.
(defn param-append-api-key[params]
  (let [add-api-key (assoc-in params      [:query-params "apikey"] (get-public-key))
        add-nonce   (assoc-in add-api-key [:query-params "nonce"] (h/get-nonce))]
    add-nonce))

; Sign url for market and order apis.
; Append params to url then sign it.
(defn post->map-signed 
  ; Used when no param, append apikey and nonce.
  ([url]
   (post->map-signed url {:query-params {}}))
  ([url param]
   (let [param-apikey-binded-url (bind-url-param url (param-append-api-key param))
         signed-url (sign-url param-apikey-binded-url)]
     (post->map param-apikey-binded-url {:headers {"apisign" signed-url}}))))

(defn longest-map-key-count 
  "Count the length of longest key from given map, : keyword colon included."
  [umap]
  (apply max (for [key (keys umap)] 
               (count (str key)))))

; https://stackoverflow.com/questions/6685916/how-to-iterate-over-map-keys-and-values?rq=1
(defn map->string
  "Return newline separated map."
  [umap]
  (let [longest-key    (longest-map-key-count umap)
        padding-format (str "%-" longest-key "s")]
    (loop [keys   (keys umap)
           vals   (vals umap)
           result ""]
      (if (empty? keys)
        result
        (recur (rest keys)
               (rest vals)
               (str (format padding-format (first keys)) (first vals) "\n"))))))

; https://stackoverflow.com/questions/3249334/test-whether-a-list-contains-a-specific-value-in-clojure
(defn in?
  "true if coll contains elm"
  [coll elm]  
  (some #(= elm %) coll))
