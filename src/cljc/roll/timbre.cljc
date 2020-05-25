(ns roll.timbre
  (:require [taoensso.timbre :as timbre :refer [info]]
            [integrant.core :as ig]
            [roll.util :as u]))



(defn- println-appender [opts]
  (merge (timbre/println-appender (select-keys opts [:stream]))
         (dissoc opts :stream)))


#?(:clj
   (defn- spit-appender [opts]
     (merge (timbre/spit-appender (select-keys opts [:fname :append?]))
            (dissoc opts :fname :append?))))



(def default-appenders
  {:println println-appender
   #?@(:clj [:spit spit-appender])})


(defn init-appenders [opts default-appenders]
  (let [opts (or (not-empty opts)
                 {:println {}})]
    (reduce-kv
     (fn [opts k f]
       (if-let [app-opts (get opts k)]
         (if (:fn app-opts)
           opts
           (assoc opts k (f app-opts)))
         opts))
     opts
     default-appenders)))



(defmethod ig/init-key :roll/timbre [_ opts]
  (info "initializing roll/timbre:")
  (info (u/spp opts))
  
  (let [old-appenders (zipmap (keys (:appenders timbre/*config*))
                              (repeat nil))]
    (-> (u/resolve-map-syms opts)
        (update :appenders init-appenders default-appenders)
        (update :appenders (partial merge old-appenders))
        (timbre/merge-config!))))
