(ns roll.figwheel
  (:require [figwheel-sidecar.repl-api :refer [cljs-repl start-figwheel!]]))

(def source-dirs ["src"])
(def css-dirs   ["resources/public/css"])

(def compiler-config (clojure.edn/read-string (slurp "cljs.edn")))

(def dev-config (merge compiler-config
                       {:optimizations :none
                        :devcards      true
                        :warnings      {:redef false}
                        :source-map    true}))



(defn -main [& args]
  (start-figwheel!
   {:figwheel-options {:server-port      5000
                       :css-dirs         css-dirs
                       :nrepl-port       3312
                       :nrepl-middleware ["cider.nrepl/cider-middleware"
                                          "cemerick.piggieback/wrap-cljs-repl"]}
    :all-builds       [{:id           "dev"
                        :figwheel     true
                        :source-paths source-dirs
                        :compiler     dev-config}]}))
