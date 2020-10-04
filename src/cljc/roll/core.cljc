(ns roll.core
  (:require [taoensso.timbre :as timbre :refer [info]]
            [integrant.core :as ig]
            [roll.state :as state]
            [roll.util :as u]
            #?(:clj [roll.env])))



(derive :roll/httpkit :roll/server)
(derive :roll/nginx   :roll/server)
(derive :roll/aleph   :roll/server)



(defn- set-config! [config]
  (alter-var-root #'state/config (constantly config)))



(defn- halt-system [system & [ks]]
  (when system
    (if ks
      (do (ig/halt! system ks)
          (-> (apply dissoc system ks)
              (vary-meta update ::ig/build #(apply dissoc % ks))
              (vary-meta update ::ig/origin #(apply dissoc % ks))))
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



(defn restart-system [config system & [ks]]
  (if system
    (do
      ;; we have a running system
      (suspend-system system ks)
      (if ks
        ;; resume will halt missing keys, so make sure to select
        ;; only specified keys from running system
        ;; https://github.com/weavejester/integrant/issues/84
        (u/meta-preserving-merge
         (apply dissoc system ks)
         (resume-system config (select-keys system ks) ks))

        ;; or resume all keys
        (resume-system config system)))

    ;; init if no running system
    (init-system config ks)))



(defn restart
  ([] (restart nil))
  ([ks] (restart state/config ks))
  ([config ks]
   (alter-var-root
    #'state/system
    (fn [system] (restart-system config system ks)))))




#?(:clj
   (defonce init-shutdown-hook
     (delay (.addShutdownHook (Runtime/getRuntime) (Thread. #'halt)))))



(defn prep-config [ig-config]
  (alter-var-root
   #'state/opts (constantly (:roll/opts ig-config)))
  
  (cond-> ig-config
    :default (dissoc :roll/opts)
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
  [& configs]
  (let [ ;; merge all configs
        ig-config
        (reduce
         (fn [all config]
           (let [config
                 (cond
                   #?@(:clj [(string? config)
                             (ig/read-string {:readers *data-readers*}
                                             (slurp config))])
                   :else config)]
             (u/deep-merge-into all config)))
         {} configs)

        ;; prep global config
        ig-config (prep-config ig-config)]
    
    ;; make sure component namespaces are loaded
    #?(:clj (ig/load-namespaces ig-config))
    
    (ig/prep ig-config)))




(defn init
  "Init Integrant with file path(s) or map(s) configs."
  [& configs]

  ;; init Timbre with simpler output
  (timbre/merge-config!
   {:min-level :info
    :output-fn
    (fn [{:keys [msg_ ?err]}]
      (str (force msg_)
           (when ?err
             (str "\n" (timbre/stacktrace ?err)))))})
  
  (let [config (apply load-configs configs)]
    (alter-var-root
     #'state/system
     (fn [sys]
       (halt-system sys)
       (u/meta-preserving-merge
        ;; start logging first
        (init-system config [:roll/timbre])
        (init-system (dissoc config :roll/timbre)))))
    
    (set-config! config))
  
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
  [configs & [watch-opts]]
  (let [configs (if (sequential? configs) configs [configs])]
    (when-let [new-config (apply load-configs configs)]
      (let [old-config (::ig/origin (meta state/system))
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


        ;; stop deleted keys
        (when (not-empty deleted-keys)
          (info "halting" (vec deleted-keys))
          (halt deleted-keys))

        ;; restart changed keys
        (when (not-empty restart-keys)
          (info "restarting" (vec restart-keys))
          (restart new-config restart-keys))

        (set-config! new-config)))))
