(ns roll.paths
  (:require [taoensso.timbre :refer [info]]
            [clojure.tools.reader :as redr]
            [clojure.java.io :as io]
            [integrant.core :as ig]
            [roll.watch :as w]
            [roll.util :as u])
  (:import [java.io PushbackReader]))



(defn format-parent [file]
  (let [fname (.getName file)
        parent (->> (.getParent file) (re-find #"\w*$"))]
    (str (some-> (not-empty parent) (str "/"))
         fname)))



(defn require-reload [file]
  (info "reloading" [(format-parent file)])
  (with-open [r (PushbackReader. (io/reader file))]
    (-> {:read-cond :allow :features #{:clj}}
        (redr/read r)
        second
        (require :reload))))



(defn reload-clj [paths]
  (->> (map io/file paths)
       (filter (comp #{"clj" "cljc"} w/file-suffix))
       ;; -or- (clojure.tools.namespace.repl/refresh)
       ;; -or- (doall (map load-file paths))
       ;; -or- (map require-reload)
       (map load-file paths)
       doall))





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
                :filter w/file-filter
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

  (w/reset-watch!)
  
  (->> (u/resolve-syms opts)
       (#(cond-> %
           (->> % (filter map?) not-empty)
           (vector)))
       (mapv proc-item)))



(defmethod ig/halt-key! :roll/paths [_ opts]
  (info "stopping roll/paths...")
  (w/reset-watch!)) ;;fixme: use w/remove-watch!










(comment

  '[clojure.tools.reader :as redr]
  '[clojure.tools.reader.edn :as redn]
  '[clojure.tools.reader.reader-types :as rtypes]

  '(-> (n/read-file-ns-decl (clojure.java.io/file path))
       second
       (require :reload))

  
  (proc-item
   ["src/clj/roll/reload.clj"
    {:init clojure.core/prn
     :watch true}])


  (w/stop!)

  {:roll/paths [["resources/public/css/main.css"
                 "resources/public/js/index.js"
                 {:init clojure.core/prn
                  :watch roll.util/read-edn
                  :throttle 1000
                  :filter ["clj" "cljs"]
                  :close clojure.core/prn}]]}
  )
