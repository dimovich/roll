(ns roll.core
  (:require [taoensso.timbre :as timbre :refer [info]]
            [integrant.core :as ig]
            [meta-merge.core :refer [meta-merge]]
            [roll.state :as state]
            [roll.util :as u]))



(derive :roll/httpkit :roll/server)
(derive :roll/nginx   :roll/server)
(derive :roll/aleph   :roll/server)



(defn- set-config! [config]
  (alter-var-root #'state/config (constantly config)))


(defn- halt-system [system & [ks]]
  (when system
    (if ks
      (do (ig/halt! system ks)
          (apply dissoc system ks))
      (ig/halt! system))))


(defn- build-system [build wrap-ex]
  (try
    (build)
    (catch clojure.lang.ExceptionInfo ex
      (when-let [system (:system (ex-data ex))]
        (try
          (ig/halt! system)
          (catch clojure.lang.ExceptionInfo halt-ex
            (throw (wrap-ex ex halt-ex)))))
      (throw ex))))



(defn- init-system [config & [ks]]
  (build-system
   (if ks
     #(ig/init config ks)
     #(ig/init config))
   #(ex-info
     "Config failed to init; also failed to halt failed system"
     {:init-exception %1}
     %2)))



(defn- resume-system [config system & [ks]]
  (build-system
   (if ks
     #(ig/resume config system ks)
     #(ig/resume config system))
   #(ex-info
     "Config failed to resume; also failed to halt failed system"
     {:resume-exception %1}
     %2)))



(defn- suspend-system [system & [ks]]
  (when system
    (if ks
      (ig/suspend! state/system ks)
      (ig/suspend! state/system))))



(defn halt [& [ks]]
  (alter-var-root
   #'state/system
   (fn [sys]
     (halt-system sys ks))))



(defn clear []
  (halt)
  (alter-var-root #'state/config (constantly nil)))



(defn restart [& [ks]]
  (alter-var-root
   #'state/system
   (fn [sys]
     (if sys
       (do ;; we have a running system
         (suspend-system sys ks)
         (if ks
           ;; resume will halt missing keys, so make sure to select
           ;; only specified keys
           (meta-merge
            (apply dissoc sys ks)
            (resume-system (select-keys state/config ks)
                           (select-keys sys ks)
                           ks))
           (resume-system state/config sys)))

       ;; no running system
       (init-system state/config ks)))))




#?(:clj
   (defonce init-shutdown-hook
     (delay (.addShutdownHook (Runtime/getRuntime) (Thread. #'halt)))))



(defn prep-config [ig-config]
  (cond-> ig-config
    #?@(:clj
        [ ;; ensure we have Sente for :roll/reload
         (:roll/reload ig-config)
         (-> (update :roll/sente (fnil identity {}))
             (update-in [:roll/handler :sente]
                        (fnil identity (ig/ref :roll/sente))))

         ;; ensure we have a handler for :roll/server
         (some #(isa? % :roll/server) (keys ig-config))
         (update :roll/handler (fnil identity {}))])))




(defn load-configs
  "Load Integrant configs. Either file path(s) or map(s)."
  [configs]
  (let [configs (cond-> configs
                  (not (sequential? configs)) vector)
        ;; merge all configs
        ig-config (reduce
                   (fn [all config]
                     (let [config (cond
                                    (map? config) config
                                    #?@(:clj [(string? config)
                                              (ig/read-string (slurp config))]))]
                       (u/deep-merge-into all config)))
                   {} configs)

        ;; prep global config
        ig-config (prep-config ig-config)]
     
    ;; make sure component namespaces are loaded
    #?(:clj (ig/load-namespaces ig-config))
    
    (ig/prep ig-config)))




(defn init
  "Init Integrant using either file path(s) or map(s) configs."
  [configs]

  ;; init Timbre
  (timbre/merge-config!
   {:level :info
    :output-fn
    (fn [{:keys [msg_ ?err]}]
      (str (force msg_)
           (when ?err
             (str "\n" (timbre/stacktrace ?err)))))})


  (set-config! (load-configs configs))

  (alter-var-root
   #'state/system
   (fn [sys]
     (halt-system sys)
     (meta-merge
      ;; start logging first
      (init-system state/config [:roll/timbre])
      (init-system (dissoc state/config :roll/timbre)))))
  
  #?(:clj (force init-shutdown-hook)))




(defn get-dependents
  "Recursively get component keys dependents."
  [deps cmpt-keys]
  (when (not-empty cmpt-keys)
    (let [deps-keys (->> (select-keys (:dependents deps) cmpt-keys)
                         (mapcat val))]
      (->> (get-dependents deps deps-keys)
           (concat deps-keys)
           (distinct)))))



(defn get-dependencies
  "Get component keys dependencies"
  [deps cmpt-keys]
  (->> cmpt-keys
       (select-keys (:dependencies deps))
       (mapcat val)
       (distinct)))



(defn reload
  "Check configs and restart changed keys and their dependencies."
  [paths & [watch-opts]]
  (when-let [new-config (load-configs paths)]
    (let [old-config state/config
          deps (ig/dependency-graph new-config)
          
          deleted-keys (clojure.set/difference
                        (set (keys old-config))
                        (set (keys new-config)))
        
          changed-keys (->> new-config
                            (reduce-kv
                             (fn [changed k v]
                               (cond-> changed
                                 (not= v (get old-config k))
                                 (conj k)))
                             []))

          ;; also add dependencies
          changed-keys (concat changed-keys
                               (get-dependencies deps changed-keys))

          ;; also add dependecies' dependents
          changed-keys (concat changed-keys
                               (get-dependents deps changed-keys))

          ;; also add dependents' dependencies
          changed-keys (concat changed-keys
                               (get-dependencies deps changed-keys))
        
          restart-keys (distinct changed-keys)]

      
      (set-config! new-config)
      
      ;; stop deleted keys
      (when (not-empty deleted-keys)
        (info "halting" (vec deleted-keys))
        (halt deleted-keys))

      ;; restart changed keys
      (when (not-empty restart-keys)
        (info "restarting" (vec restart-keys))
        (restart restart-keys)))))
