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
  (when-let [tasks (when (not-empty tasks)
                     (if (sequential? (first tasks))
                       tasks [tasks]))]

    (info "starting roll/schedule...")
    (info (ru/spp tasks))
    
    (->> tasks
         (reduce
          (fn [chans [n k & run-fns :as task]]
            (if-let [times (periodic* k n)]
              (let [run-fns (ru/resolve-coll-syms run-fns)
                    chimes (->> {:ch (a/chan (a/sliding-buffer 1))}
                                (chime-ch times))
                    cancel-ch (a/chan)]
                
                (go-loop []
                  (when-let [time (<! chimes)]
                    (info "[START]" task)
                    (let [tasks-ch (a/to-chan run-fns)]
                      (loop []
                        (when-let [task-fn (a/<! tasks-ch)]
                          (let [[task-fn & args] (if (sequential? task-fn)
                                                   task-fn [task-fn])
                                args (or args [time])
                                run-ch (apply task-fn args)]
                            ;; task function returned an async channel;
                            ;; the channel can be closed or auto-close
                            (when (instance? ManyToManyChannel run-ch)
                              (let [[_ ch] (a/alts! [run-ch cancel-ch])]
                                (when (= ch cancel-ch)
                                  (a/close! run-ch)
                                  (a/close! tasks-ch)
                                  (a/close! chimes)
                                  ;; exhaust the time channel
                                  (while (a/poll! chimes))
                                  (while (a/poll! tasks-ch))))))
                          (recur))))
                    (info "[DONE]" task)
                    
                    (recur)))
                
                (assoc chans task [chimes cancel-ch]))
              chans))
          {}))))




(defmethod ig/halt-key! :roll/schedule [_ chans]
  (info "stopping roll/schedule...")
  (some->> (not-empty chans)
           (map (fn [[k v]] (doseq [ch v] (a/close! ch))))
           dorun))
