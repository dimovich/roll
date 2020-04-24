(ns roll.schedule
  (:require [taoensso.timbre :refer [info]]
            [clojure.core.async :as a :refer [<! go-loop]]
            [clj-time.core :as t]
            [clj-time.periodic :refer [periodic-seq]]
            [chime :refer [chime-ch]]
            [integrant.core :as ig]
            [roll.util :as ru])
  (:import [clojure.core.async.impl.channels ManyToManyChannel]))



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
  
  (when-let [tasks (some-> (not-empty tasks)
                           (cond->
                               (not (sequential? (first tasks)))
                               [tasks]))]

    (info tasks)
    
    (->> (ru/resolve-coll-syms tasks)
         (reduce
          (fn [chans [n k run-fn]]
            (if-let [times (periodic* k n)]
              (let [chimes (->> {:ch (a/chan (a/sliding-buffer 1))}
                                (chime-ch times))
                    cancel-ch (a/chan)]
                
                (go-loop []
                  (when-let [time (<! chimes)]
                    (let [task-ch (run-fn time)]
                      ;; task function returned an async channel
                      (when (instance? ManyToManyChannel task-ch)
                        #_(let [[v ch] (a/alts! [task-ch cancel-ch])]
                            (when (= ch cancel-ch)
                              (a/close! task-ch)))
                        (a/alt!
                          cancel-ch (a/close! task-ch)
                          task-ch ())))
                    
                    (recur)))
                
                (conj chans chimes cancel-ch))
              chans))
          []))))




(defmethod ig/halt-key! :roll/schedule [_ chans]
  (info "stopping roll/schedule...")
  (doseq [ch chans] (a/close! ch)))
