(ns roll.run
  (:require [taoensso.timbre :refer [info]]
            [integrant.core :as ig]
            [roll.util :as u])
  (:import [java.lang Runtime]))




(defmethod ig/init-key :roll/run [_ paths]
  (reduce
   (fn [procs path]
     (info "starting" path)
     (conj procs (some->> path (.exec (Runtime/getRuntime)))))
   []
   paths))



(defmethod ig/halt-key! :roll/run [_ procs]
  (doseq [proc procs]
    (when proc
      (info "stopping" (.pid proc))
      (.destroy proc))))

