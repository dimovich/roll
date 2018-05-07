(ns roll.figwheel
  (:require [figwheel-sidecar.repl-api :refer [cljs-repl start-figwheel!]]
            [roll.util :refer [when-read]]))

(def source-dirs ["src/cljs" "src/cljc"])
(def css-dirs   ["resources/public/css"])

(def compiler-config (clojure.edn/read-string (slurp "cljs.edn")))

(def dev-config (merge compiler-config
                       {:optimizations :none
                        :warnings      {:redef false}
                        :source-map    true}))


(defn figwheel []
  (let [opts (when-read [data "figwheel.edn"]
               (clojure.edn/read-string data))]
    
    (-> {:figwheel-options {:css-dirs         css-dirs
                            :nrepl-port       3312
                            :nrepl-middleware ["cider.nrepl/cider-middleware"
                                               "cemerick.piggieback/wrap-cljs-repl"]}
         :all-builds       [{:id           "dev"
                             :figwheel     true
                             :source-paths source-dirs
                             :compiler     dev-config}]}
        
        (update :figwheel-options merge
                (some-> opts (select-keys
                              [:css-dirs :server-port
                               :nrepl-middleware :nrepl-port])))
        
        (start-figwheel!))))



(defn -main [& args]
  (figwheel))

