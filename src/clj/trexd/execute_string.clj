(ns trexd.execute-string
  (:gen-class))

(defn execute-string 
  "Execute command on main ns which had all namespaces pre-imported.
   So user don't have to specify fully qualified name for each command."
  [command]
  (binding [*ns* (find-ns 'trexd.core)]
    (load-string command)))
