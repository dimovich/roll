(ns roll.run
  (:require [taoensso.timbre :refer [info]]
            [integrant.core :as ig]
            [roll.util :as u])
  (:import [java.lang Runtime]))




(defmethod ig/init-key :roll/run [_ paths]
  (reduce
   (fn [procs path]
     (info "starting" (pr-str path))
     (conj procs (some->> path (.exec (Runtime/getRuntime)))))
   []
   paths))



(defmethod ig/halt-key! :roll/run [_ procs]
  (info "stopping :roll/run")
  (doseq [proc procs]
    (when proc
      (info "stopping pid" (.pid proc))
      (.destroy proc))))



(comment

  #_(let [t (Thread.
             (fn [] ))]
      (info t)
      (doto t
        (.setDaemon true)
        (.start))
      t)

  #_(.interrupt proc)

  
  #_(doto (Thread.
           (fn [] (some->> path (.exec (Runtime/getRuntime)))))
      (.setDaemon true)
      (.start)))
