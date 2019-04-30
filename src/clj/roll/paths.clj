(ns roll.paths
  (:require [taoensso.timbre :refer [info]]
            [clojure.java.io :as io]
            [integrant.core :as ig]
            [roll.watch :as w]
            [roll.util :as u]))


(defn load-clj-files [paths]
  (doall (map load-file paths)))


(defn proc-item [coll]
  (->> coll
       ;; extract paths and opts
       ((juxt #(->> % (remove map?) flatten distinct (filter string?))
              #(->> % (filter map?) (apply merge))))

       ;; init paths with opts
       ((fn [[paths {:as opts :keys [init watch]}]]
          (when-let [paths
                     (->> paths
                          (map #(-> (.replaceFirst % "^~" (System/getProperty "user.home"))
                                    io/file
                                    .getCanonicalPath))
                          (filter #(if (u/exists? %) %
                                       (info "Warning: Could not open" %)))
                          vec
                          (not-empty))]
            
            (when init (init paths))
            
            (when-let [watch (if (true? watch) init watch)]
              (w/add-watch!
               paths
               {:paths paths
                :filter (w/file-filter)
                :handler
                (w/throttle
                 (or (:throttle opts) 50)
                 (fn [evts]
                   (when-let [files
                              (->> evts
                                   (mapv (comp #(.getCanonicalPath %) :file))
                                   set vec not-empty)]
                     (watch files))))})))))))




(defmethod ig/init-key :roll/paths [_ opts]
  (info "starting roll/paths:")
  (info (u/spp opts))
  
  (->> opts
       u/resolve-syms
       (#(cond-> %
           (->> % (filter map?) not-empty)
           (vector)))
       (mapv proc-item)))



(defmethod ig/halt-key! :roll/paths [_ opts]
  (info "stopping roll/paths...")
  (w/reset-watch!))







(comment
  
  (proc-item
   ["src/clj/roll/reload.clj"
    {:init clojure.core/prn
     :watch true}])


  (w/stop!)

  {:roll/paths [["resources/public/css/main.css"
                 "resources/public/js/index.js"
                 {:init clojure.core/prn
                  :watch roll.util/read-edn
                  :throttle 1000}]]}
)
