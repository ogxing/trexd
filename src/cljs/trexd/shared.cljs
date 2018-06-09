(ns trexd.shared
  (:require [reagent.core :as r])) 

; Shared global app-state, define initial values here.
(def app-state (r/atom {:active-page "alert"}))
