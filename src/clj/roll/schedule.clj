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
              (let [chimes (->>
                            {:ch (a/chan (a/sliding-buffer 1))}
                            (chime-ch times))
                    close-ch (a/chan)]
                
                (go-loop []
                  (when-let [time (<! chimes)]
                    (let [in-ch (run-fn time)]
                      ;; task function returns a channel; consume it
                      (when (instance? ManyToManyChannel in-ch)
                        (loop []
                          (a/alt!
                            close-ch (a/close! in-ch)
                            in-ch ([v] (when v
                                         (recur)))))))
                    
                    (recur)))
                
                (conj chans chimes close-ch))
              chans))
          []))))




(defmethod ig/halt-key! :roll/schedule [_ chans]
  (info "stopping roll/schedule...")
  (doseq [ch chans] (a/close! ch)))
