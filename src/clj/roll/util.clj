(ns roll.util
  (:require [clojure.java.io :as io]
            [com.rpl.specter :as sr :refer [ALL MAP-VALS transform]]
            [integrant.core :as ig])
  (:import [java.io PushbackReader]))




(defn try-require
  "Tries to require the given namespace symbol, returning nil if not found."
  [sym]
  (try (do (require sym) sym)
       (catch java.io.FileNotFoundException _)
       #_(catch RuntimeException _)))



(defn try-resolve
  "Tries to resolve the given namespace-qualified symbol, returning nil if not found."
  [sym]
  (when (try-require (symbol (namespace sym)))
    (resolve sym)))




(defmacro resolve-cljs [ns-sym var-sym]
  `(some-> ((ns-publics ~ns-sym) ~var-sym)
           deref))



(defmacro try-require-cljs [ns-sym]
  `(try
     (do (require ~ns-sym) ~ns-sym)
     (catch :default ex#)))




(defn sym->var [s]
  (if (symbol? s)
    ;; for production better without fn wrapping
    ;; (some-> (try-resolve s) deref)
    
    ;; for development, wrap config fns with a deref on every call so
    ;; autoreloading works
    (or (when-let [v (try-resolve s)]
          (if (fn? (deref v))
            #(-> (deref v) (apply %&))
            (deref v)))
        (throw (Exception. (str "Could not resolve" s))))
    s))


(declare resolve-map-syms)
(declare resolve-coll-syms)


(defn resolve-syms [v]
  (cond
    (coll? v) (resolve-coll-syms v)
    (map? v) (resolve-map-syms v)
    :else (sym->var v)))

;; try clojure.walk

(defn resolve-map-syms [m]
  (transform [MAP-VALS] resolve-syms m))


(defn resolve-coll-syms [coll]
  (transform [ALL] resolve-syms coll))




(defn exists? [path]
  (.exists (io/file path)))



(defn url? [s]
  (try (boolean (io/as-url s))
       (catch Exception ex false)))



(defn get-path [path]
  (or (io/resource path) path))



(defn read-edn [path]
  (with-open [r (PushbackReader. (io/reader (get-path path)))]
    (clojure.edn/read r)))



(defn- read-one [r]
  (try
    (clojure.edn/read r)
    (catch java.lang.RuntimeException e
      (if (= "EOF while reading" (.getMessage e))
        ::EOF
        (throw e)))))




(defn read-edn-seq [path & [f]]
  (with-open [r (PushbackReader. (io/reader (get-path path)))]
    (doall (cond->> (take-while #(not= ::EOF %) (repeatedly #(read-one r)))
             f (map f)))))




(defn write-edn [path data & opts]
  (with-open [w (apply io/writer path opts)]
    (binding [*print-length* nil
              *out* w]
      (prn data))))




(defmacro load-edn [file]
  (slurp file))



(defmacro read-config [resource]
  (ig/read-string (slurp (get-path resource))))



;; todo: use read-edn
(defmacro with-in-> [in & body]
  `(-> ~in
       slurp
       clojure.edn/read-string
       ~@body))




;; todo: use write-edn
(defmacro with-out-> [out & body]
  `(binding [*print-length* nil]
     (-> ~@body
         pr-str
         (#(spit ~out %)))))




(defmacro with-in-out-> [in out & body]
  `(binding [*print-length* nil]
     (-> ~in
         slurp
         clojure.edn/read-string
         ~@body
         pr-str
         (#(spit ~out %)))))




(defmacro when-read [[name fname] & body]
  `(let [file# (io/file ~fname)]
     (when (.exists file#)
       (with-open [rdr# (io/reader file#)]
         (let [~name (slurp rdr#)]
           ~@body)))))




(defmacro when-read-edn [[name path] & body]
  `(let [file# (io/file (get-path ~path))]
     (when (.exists file#)
       (with-open [rdr# (java.io.PushbackReader.
                         (clojure.java.io/reader file#))]
         (when-let [~name (clojure.edn/read rdr#)]
           ~@body)))))




(defn parse-int [s]
  (try (Integer/parseInt (re-find #"\A-?\d+" s))
       (catch Exception e nil)))




(defn read-tsv [tsv]
  (some->>
   (clojure.string/split tsv  #"[\r\n]+")
   (map #(clojure.string/split % #"\t"))
   ((juxt (comp cycle list first)
          (comp (partial remove (comp empty? first)) rest)))
   (apply map zipmap)))




(defn coerce-map [coerce-fns m]
  (loop [m m
         ks (keys coerce-fns)]
    (let [[k & rst] ks]
      (if k
        (recur (update m k (or (get coerce-fns k)
                               identity))
               rst)
        m))))




(defn coll->pattern [coll]
  (re-pattern
   (str "(?i)\\b("
        (->> (interpose "|" coll)
             (apply str))
        ")\\b")))



(defn fn->name [f]
  (-> (str f)
      clojure.main/demunge
      (clojure.string/split #"@")
      first
      symbol))



(defn spp [& args]
  (with-out-str (apply clojure.pprint/pprint args)))



(defn str->map-list [s ks]
  (or
   (some->> s (clojure.string/trim)
            (#(clojure.string/split % #"\n\n+"))
            (map #(clojure.string/split % #"\n"))
            (map cycle)
            (mapv (partial zipmap ks)))
   []))




(defn imagelist [s]
  (or (some-> (not-empty s)
              (str->map-list ["desktop" "mobile"]))
      []))



(defn taglist [s]
  (let [coll (some->
              s (clojure.string/split #"\n\n")
              (->>
               (map #(clojure.string/split % #",|\n"))
               (map (partial map clojure.string/trim))
               (remove empty?)
               (mapv (comp vec (partial remove empty?)))))]
    
    (cond
      (= 1 (count coll)) (first coll)
      :default (or coll []))))



(defn normalize-path [path]
  (-> (.replaceFirst path "^~" (System/getProperty "user.home"))
      io/file
      .getCanonicalPath))



(defn format-parent [file]
  (let [fname (.getName file)
        parent (->> (.getParent file) (re-find #"\w*$"))]
    (str (some-> (not-empty parent) (str "/"))
         fname)))
