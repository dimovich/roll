(ns roll.core
  (:require [roll.system :refer [init]]))


(defn -main [& args]
  (init "conf/config.edn"))


