(set-env!
  :resource-paths #{"resources"}
  :source-paths #{"src/clj" "src/cljs"}
  :dependencies '[[adzerk/boot-cljs "2.1.4" :scope "test"]
                  [adzerk/boot-reload "0.5.2" :scope "test"]
                  [javax.xml.bind/jaxb-api "2.3.0"] ; necessary for Java 9 compatibility
                  ; Clojure dependencies. (server)
                  [org.clojure/clojure "1.9.0"]
                  [clj-http "3.9.0"]
                  [clj-time "0.14.3"]
                  [com.draines/postal "2.0.2"]
                  [org.clojure/data.json "0.2.6"]
                  [org.clojure/java.jdbc "0.7.6"]
                  [org.xerial/sqlite-jdbc "3.20.1"]
                  [ring "1.6.3"]
                  [compojure "1.6.1"]
                  [http-kit "2.2.0"]
                  [crypto-random "1.2.0"]
                  [org.clojure/data.codec "0.1.1"]
                  [org.clojure/tools.nrepl "0.2.12"]
                  [samestep/boot-refresh "0.1.0" :scope "test"]
                  ; ClojureScript dependencies. (client)
                  [org.clojure/clojurescript "1.10.238" :scope "test"]
                  [cljs-ajax "0.7.3" :scope "test"]
                  [reagent "0.7.0" :scope "test"]
                  [cljsjs/react-dom "16.3.2-0" :scope "test"]
                  [cljsjs/react "16.3.2-0" :scope "test"]
                  [soda-ash "0.79.1" :scope "test"]])
(task-options!
  pom {:project 'trexd
       :version "1.0.0-SNAPSHOT"
       :description "BitTrex Daemon"}
  aot {:namespace #{'trexd.core}}
  jar {:main 'trexd.core})

(require
  '[adzerk.boot-cljs :refer [cljs]]
  '[adzerk.boot-reload :refer [reload]]
  '[samestep.boot-refresh :refer [refresh]]
  'trexd.core)

(deftask run []
  (comp
    (watch)
    (reload :asset-path "public")
    (cljs
      :source-map true
      :optimizations :none
      :compiler-options {:asset-path "main.out"})
    (target)
    (with-pass-thru _ 
      (trexd.core/-main))))

(deftask build-server []
  (comp
    (aot)
    (pom)
    (uber)
    (jar)
    (sift :include #{#"\.jar$"})
    (target)))

(deftask build-cljs []
  (comp
    (cljs 
      :optimizations :advanced
      :compiler-options {:asset-path "main.out"}
      :pretty-print true
      :pseudo-names true)
    (target)))
