(ns trexd.hmac
  (:import (javax.crypto Mac)
           (javax.crypto.spec SecretKeySpec))
  (:require [clojure.data.codec.base64 :as b64])
  (:require [crypto.random :as nonce]))

(def ^:private ^"[B" hex-chars
  (byte-array (.getBytes "0123456789ABCDEF" "ASCII")))

(defn get-nonce[]
  (String. (b64/encode
             (nonce/bytes 16))
    "UTF-8"))

; https://crossclj.info/fun/pandect.utils.convert/bytes-%3Ehex.html
(defn bytes->hex
  "Convert Byte Array to Hex String"
  ^String
  [^"[B" data]
  (let [len (alength data)
        ^"[B" buffer (byte-array (* 2 len))]
    (loop [i 0]
      (when (< i len)
        (let [b (aget data i)]
          (aset buffer (* 2 i) (aget hex-chars (bit-shift-right (bit-and b 0xF0) 4)))
          (aset buffer (inc (* 2 i)) (aget hex-chars (bit-and b 0x0F))))
        (recur (inc i))))
    (String. buffer "ASCII")))

;https://gist.github.com/jhickner/2382543
;With slight modifications.
(defn hmac 
  "Calculate HMAC signature for given data."
  [^String key ^String data]
  (let [hmac-sha512 "HmacSHA512"
        signing-key (SecretKeySpec. (.getBytes key) hmac-sha512)
        mac (doto (Mac/getInstance hmac-sha512) (.init signing-key))]
    (bytes->hex
      (.doFinal mac (.getBytes data)))))

