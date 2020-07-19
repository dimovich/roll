(ns roll.schedule
  (:require [taoensso.timbre :refer [info]]
            [clojure.core.async :as a :refer [<! go-loop]]
            [chime.core :as chime]
            [chime.core-async :as chime-async]
            [integrant.core :as ig]
            [roll.util :as ru])
  (:import [clojure.core.async.impl.channels ManyToManyChannel]
           [java.time Instant Period Duration]))



(def periods
  {:ms #(Duration/ofMillis %)
   :s  #(Duration/ofSeconds %)
   :m  #(Duration/ofMinutes %)
   :h  #(Duration/ofHours %)
   :d  #(Duration/ofDays %)
   :w  #(Period/ofWeeks %)
   :mt #(Period/ofMonths %)
   :y  #(Period/ofYears %)})



(defn periodic* [k n]
  (when-let [pf (get periods k)]
    (rest (chime/periodic-seq (Instant/now) (pf n)))))



(defn start-tasks [tasks]
  (when-not (empty? tasks)
    (info "starting...\n" (ru/spp tasks)))
  (reduce
   (fn [chans task]
     (let [[n k & run-fns :as task-config] (ru/resolve-syms task)]
       (if-let [times (periodic* k n)]
         (let [chimes (chime-async/chime-ch
                       times {:ch (a/chan (a/sliding-buffer 1))})
               cancel-ch (a/chan)]
           
           (go-loop []
             (when-let [time (<! chimes)]
               (info "[⏵]" task)
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
               (info "[✓]" task)
                    
               (recur)))
                
           (assoc chans task (with-meta {:chans [chimes cancel-ch]}
                               {::build task-config})))
         chans)))
   {} tasks))



(defn stop-tasks
  ([system] (stop-tasks system (keys system)))
  ([system tasks]
   (if (empty? tasks)
     system
     (do
       (info "stopping...\n" (ru/spp tasks))
       (run!
        (fn [task]
          (doseq [ch (-> system (get task) :chans)] (a/close! ch)))
        tasks)

       (apply dissoc system tasks)))))



(defmethod ig/init-key :roll/schedule [_ tasks]
  (when-let [tasks (when (not-empty tasks)
                     (if (sequential? (first tasks)) tasks [tasks]))]
    (info "starting roll/schedule...")
    (start-tasks tasks)))



(defmethod ig/halt-key! :roll/schedule [_ system]
  (info "halting roll/schedule...")
  (stop-tasks system))



(defmethod ig/suspend-key! :roll/schedule [_ tasks]
  (info "suspending roll/schedule...")
  ;; doing nothing, as halting/restarting will happen in resume-key
  )



(defmethod ig/resume-key :roll/schedule [_ new-tasks old-value old-impl]
  (info "resuming roll/schedule...")
  (let [ ;; deleted tasks
        missing-keys (remove (set new-tasks) (keys old-impl))
        
        ;; tasks with changed task-fn definition
        changed-keys
        (->> (filter old-impl new-tasks)
             (filter (fn [task]
                       (not= (ru/resolve-syms task)
                             (-> old-impl (get task) meta ::build)))))

        new-keys (remove old-impl new-tasks)]
    
    (merge
     (stop-tasks old-impl (concat missing-keys changed-keys))
     (start-tasks (concat changed-keys new-keys)))))
