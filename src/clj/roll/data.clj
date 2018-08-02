(ns roll.data
  (:require [taoensso.timbre :refer [info]]
            [integrant.core  :as ig]
            [roll.filewatch :as fw]
            [roll.util :refer [resolve-map-vals]]))



(defmethod ig/init-key :data/file [_ {:keys [path init watch] :as opts}]
  (let [{:as opts :keys [init watch]} (resolve-map-vals opts)]
    (info "data file: " opts)
    
    (when init
      (init path))

    (when-let [watch (if (true? watch) init watch)]
      (fw/start-watch! path path watch)
      ;; return stop-fn
      #(fw/stop-watch! path))))



(defmethod ig/halt-key! :data/file [_ stop-fn]
  (when stop-fn
    (info "stopping data file watch...")
    (stop-fn)))

