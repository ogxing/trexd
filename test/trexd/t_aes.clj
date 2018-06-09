(ns trexd.t-aes
  (:use midje.sweet) 
  (:use [trexd.aes]))

(fact
  (decrypt (encrypt "12345" "pass") "pass") => "12345"
  (decrypt (encrypt 12345 "pass") "pass") => "12345")
