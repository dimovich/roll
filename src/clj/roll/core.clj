(ns roll.core
  (:require [taoensso.timbre  :as timbre :refer [info]]
            [com.rpl.specter  :as sr :refer [ALL MAP-VALS transform select filterer map-key]]
            [clojure.pprint    :refer [pprint]]
            [clojure.core.reducers :as r]
            [roll.system      :refer [init]]))


(defn -main [& args]
  (init {:path "conf/config.edn"})
  
  (timbre/set-config!
   {:level :info
    :output-fn (fn [{:keys [msg_]}] (force msg_))
    :appenders {:println (timbre/println-appender {:stream :auto})}}))


