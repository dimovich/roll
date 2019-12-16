(ns roll.schedule
  (:require [taoensso.timbre :refer [info]]
            [clj-time.core :as t]
            [clj-time.periodic :refer [periodic-seq]]
            [chime :refer [chime-at]]
            [integrant.core :as ig]
            [roll.util :as ru]))



(def periods
  {:ms t/millis
   :s  t/seconds
   :m  t/minutes
   :h  t/hours
   :d  t/days
   :w  t/weeks
   :mn t/months
   :y  t/years})



(defn periodic* [k n]
  (when-let [pf (get periods k)]
    (rest (periodic-seq (t/now) (pf n)))))




(defmethod ig/init-key :roll/schedule [_ tasks]
  (info "starting roll/schedule...")
  (info tasks)

  (let [tasks (if (sequential? (first tasks))
                tasks [tasks])]
    (->> (ru/resolve-coll-syms tasks)
         (reduce
          (fn [stop-fns [n k run-fn]]
            (if-let [times (periodic* k n)]
              (conj stop-fns (chime-at times run-fn))
              stop-fns))
          []))))




(defmethod ig/halt-key! :roll/schedule [_ stop-fns]
  (when (not-empty stop-fns)
    (info "stopping roll/schedule...")
    (doseq [f stop-fns] (f))))
