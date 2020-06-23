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



(defn start-tasks [tasks]
  (reduce
   (fn [chans task]
     (let [[n k & run-fns :as task-config] (ru/resolve-syms task)]
       (if-let [times (periodic* k n)]
         (let [chimes (->> {:ch (a/chan (a/sliding-buffer 1))}
                           (chime-ch times))
               cancel-ch (a/chan)]
           
           (go-loop []
             (when-let [time (<! chimes)]
               (info "[START]" task)
               (let [tasks-ch (a/to-chan! run-fns)]
                 (loop []
                   (when-let [task-fn (a/<! tasks-ch)]
                     (let [[task-fn & args] (if (sequential? task-fn)
                                              task-fn [task-fn])
                           args (or args [time])
                           run-ch (apply task-fn args)]
                       ;; task function returned an async
                       ;; channel, the channel can be closed or
                       ;; will auto-close
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
                
           (assoc chans task {:config task-config
                              :chans [chimes cancel-ch]}))
         chans)))
   {} tasks))




(defn stop-tasks [tasks]
  (run!
   (fn [[k v]]
     (doseq [ch (:chans v)] (a/close! ch)))
   tasks))




(defmethod ig/init-key :roll/schedule [_ tasks]
  (when-let [tasks (when (not-empty tasks)
                     (if (sequential? (first tasks))
                       tasks [tasks]))]

    (info "starting roll/schedule...\n" (ru/spp tasks))
    (start-tasks tasks)))




(defmethod ig/halt-key! :roll/schedule [_ tasks]
  (info "stopping roll/schedule...")
  (stop-tasks tasks))



(defmethod ig/suspend-key! :roll/schedule [_ tasks]
  (info "suspending roll/schedule..."))


(defmethod ig/resume-key :roll/schedule [_ new-tasks old-value old-impl]
  (info "resuming roll/schedule...")
  (let [ ;; - missing tasks
        missing-keys (remove (set new-tasks) (keys old-impl))
        
        ;; - changed task fn definition
        changed-keys
        (->> (filter old-impl new-tasks)
             (filter
              (fn [task]
                (not= (ru/resolve-syms task)
                      (get-in old-impl [task :config])))))

        to-halt (select-keys old-impl (concat missing-keys changed-keys))
        
        new-keys (remove old-impl new-tasks)

        to-start (concat changed-keys new-keys)]
    
    (when (not-empty to-halt)
      (info "halting...")
      (info (ru/spp (keys to-halt)))
      (stop-tasks to-halt))

    
    (when (not-empty to-start)
      (info "starting...")
      (info (ru/spp to-start)))
    
    (merge
     ;; previous unchanged tasks
     (apply dissoc old-impl (keys to-halt))
     ;; new and changed tasks
     (start-tasks to-start))))
