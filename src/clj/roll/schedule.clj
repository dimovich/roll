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
   :mt t/months
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
    
    (->> tasks
         (reduce
          (fn [chans [n k run-fn :as task]]
            (if-let [times (periodic* k n)]
              (let [run-fn (ru/sym->var run-fn)
                    chimes (->> {:ch (a/chan (a/sliding-buffer 1))}
                                (chime-ch times))
                    cancel-ch (a/chan)]
                
                (go-loop []
                  (when-let [time (<! chimes)]
                    (let [task-ch (run-fn time)]
                      ;; task function returned an async channel;
                      ;; the channel can be closed or auto-close
                      (when (instance? ManyToManyChannel task-ch)
                        (let [[_ ch] (a/alts! [task-ch cancel-ch])]
                          (when (= ch cancel-ch)
                            (a/close! task-ch)
                            (a/close! chimes)
                            ;; exhaust the time channel
                            (while (a/poll! chimes))))))
                    
                    (recur)))
                
                (assoc chans task [chimes cancel-ch]))
              chans))
          {}))))




(defmethod ig/halt-key! :roll/schedule [_ chans]
  (info "stopping roll/schedule...")
  (some->> (not-empty chans)
           (map (fn [[k v]] (doseq [ch v] (a/close! ch))))
           doall))
