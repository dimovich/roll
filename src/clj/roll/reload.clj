(ns roll.reload
  (:require [taoensso.timbre :refer [info]]
            [integrant.core :as ig]
            [roll.watch :as w]
            [roll.sente :as sente]
            [roll.util :as u]))



(defn prep-css-file-path [file]
  (-> file
      .getCanonicalPath
      (clojure.string/replace java.io.File/separator "/")))



(defn start-css [paths]
  (w/add-watch!
   [::css-watcher paths]
   {:paths paths
    :filter (w/suffix-filter #{"css"})
    :handler (w/throttle
              50
              (bound-fn [evts]
                (when-let [files (->> evts
                                      (mapv (comp prep-css-file-path :file))
                                      set vec not-empty)]
                  (sente/broadcast [:reload/css files]))))}))


(defn stop-css [paths]
  (w/remove-watch! [::css-watcher paths]))


(defn reload-page [paths]
  (sente/broadcast [:reload/page]))


(defn start-page [paths]
  (w/add-watch!
   [::page-watcher paths]
   {:paths paths
    :filter w/file-filter
    :handler (w/throttle
              200
              (bound-fn [evts]
                (when-let [files (->> evts
                                      (mapv (comp #(.getCanonicalPath %) :file))
                                      set vec not-empty)]
                  (sente/broadcast [:reload/page]))))}))


(defn stop-page [paths]
  (w/remove-watch! [::page-watcher paths]))



(defmethod ig/init-key :roll/reload [_ {:as opts :keys [css page]}]
  (info "starting roll/reload:")
  (info (u/spp opts))

  (cond-> []
    css  (conj (start-css css))
    page (conj (start-page page))))


(defmethod ig/halt-key! :roll/reload [_ _]
  (info "stopping roll/reload...")
  (w/stop!))
