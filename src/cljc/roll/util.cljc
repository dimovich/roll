(ns roll.util
  (:require [com.rpl.specter :as sr :refer [ALL MAP-VALS transform]]
            [integrant.core :as ig]
            [clojure.pprint]
            #?@(:clj [[clojure.java.io :as io]]))
  
  #?(:cljs (:require-macros
            [roll.util :refer [read-config resolve-cljs
                               try-require-cljs]]))
  
  #?(:clj (:import [java.io PushbackReader])))




(defn deep-merge
  {:arglists '([& maps])}
  ([])
  ([a] a)
  ([a b]
   (if (and (map? a) (map? b))
     (merge-with deep-merge a b)
     b))
  ([a b & more]
   (apply merge-with deep-merge a b more)))



(defn deep-merge-replace
  {:arglists '([& maps])}
  ([])
  ([a] a)
  ([a b]
   (if (and (map? a) (map? b) (not-empty b))
     (merge-with deep-merge-replace a b)
     b))
  ([a b & more]
   (apply merge-with deep-merge-replace a b more)))



(defn deep-merge-into
  {:arglists '([& maps])}
  ([])
  ([a] a)
  ([a b]
   (cond
     (nil? a) b
     (nil? b) a

     (and (map? a) (map? b))
     (merge-with deep-merge-into a b)
     
     (and (coll? a) (coll? b))
     (if (list? a)
       (concat a b)
       (into a b))
     
     (and (set? a) (set? b))
     (clojure.set/union b a)
     
     :else b))
  ([a b & more]
   (apply merge-with deep-merge-into a b more)))


(defn meta-preserving-merge
  [& ms]
  (let [data (apply deep-merge-replace (map meta ms))]
    (with-meta (apply deep-merge-replace ms) data)))



#?(:clj
   (defn try-require
     "Tries to require the given namespace symbol, returning nil if not found."
     [sym]
     (try (do (require sym) sym)
          (catch java.io.FileNotFoundException _)
          #_(catch RuntimeException _))))



#?(:clj
   (defn try-resolve
     "Tries to resolve the given namespace-qualified symbol, returning nil if not found."
     [sym]
     (or
      (resolve sym)
      (when (try-require (symbol (namespace sym)))
        (resolve sym)))))




#?(:clj
   (defmacro resolve-cljs [ns-sym var-sym]
     `(some-> ((ns-publics ~ns-sym) ~var-sym)
              deref)))



#?(:clj
   (defmacro try-require-cljs [ns-sym]
     `(try
        (do (require ~ns-sym) ~ns-sym)
        (catch :default ex#))))




#?(:clj
   (defn sym->var [s]
     (if (symbol? s)
       ;; for production better without fn wrapping
       ;; (some-> (try-resolve s) deref)
       
       (or (some-> (try-resolve s) deref)
           (throw (Exception. (str "Could not resolve " s))))

       ;; for development, wrap config fns with a deref on every call
       ;; so autoreloading works
       
       #_(if-let [v (try-resolve s)]
           #_(if (fn? (deref v))
               #(-> (deref v) (apply %&))
               (deref v))
           (throw (Exception. (str "Could not resolve" s))))
       
       s)))


(declare resolve-map-syms)
(declare resolve-coll-syms)


#?(:clj
   (defn resolve-syms [v]
     (cond
       (coll? v) (resolve-coll-syms v)
       (map? v) (resolve-map-syms v)
       :else (sym->var v))))

;; try clojure.walk

#?(:clj
   (defn resolve-map-syms [m]
     (transform [MAP-VALS] resolve-syms m)))


#?(:clj
   (defn resolve-coll-syms [coll]
     (transform [ALL] resolve-syms coll)))



#?(:clj
   (defn exists? [path]
     (.exists (io/file path))))


#?(:clj
   (defn exists [path]
     (when (exists? path) path)))



#?(:clj
   (defn url? [s]
     (try (boolean (io/as-url s))
          (catch Exception ex false))))



#?(:clj
   (defn get-path [path]
     (or (io/resource path) path)))



#?(:clj
   (defn read-edn [path]
     (with-open [r (PushbackReader. (io/reader (get-path path)))]
       (clojure.edn/read {:readers *data-readers*} r))))



#?(:clj
   (defn- read-one [r]
     (try
       (clojure.edn/read {:readers *data-readers*} r)
       (catch java.lang.RuntimeException e
         (if (= "EOF while reading" (.getMessage e))
           ::EOF
           (throw e))))))




#?(:clj
   (defn read-edn-seq [path & [f]]
     (with-open [r (PushbackReader. (io/reader (get-path path)))]
       (let [coll (take-while #(not= ::EOF %) (repeatedly #(read-one r)))]
         (cond
           (fn? f) (doseq [item coll] (f item))
           (keyword? f) (doall (map f coll))
           :else (doall coll))))))



#?(:clj
   (defn lazy-read-edn-seq [path]
     (let [rdr (PushbackReader. (io/reader (get-path path)))]
       (letfn [(helper [rdr]
                 (lazy-seq
                  (let [line (read-one rdr)]
                    (if (not= ::EOF line)
                      (cons line (helper rdr))
                      (do (.close rdr) nil)))))]
         (helper rdr)))))





#?(:clj
   (defn write-edn [path data & opts]
     (with-open [w (apply io/writer path opts)]
       (binding [*print-length* nil
                 *out* w]
         (prn data)))))



#?(:clj
   (defn append-edn [path data]
     (write-edn path data :append true)
     data))



#?(:clj
   (defmacro read-config [resource]
     (ig/read-string (slurp (get-path resource)))))




#?(:clj
   (defmacro when-read [[name fname] & body]
     `(let [file# (io/file ~fname)]
        (when (.exists file#)
          (with-open [rdr# (io/reader file#)]
            (let [~name (slurp rdr#)]
              ~@body))))))




#?(:clj
   (defmacro when-read-edn [[name path] & body]
     `(let [file# (io/file (get-path ~path))]
        (when (.exists file#)
          (with-open [rdr# (java.io.PushbackReader.
                            (clojure.java.io/reader file#))]
            (when-let [~name (clojure.edn/read {:readers *data-readers*} rdr#)]
              ~@body))))))




#?(:clj
   (defn parse-int [s]
     (try (Integer/parseInt (re-find #"\A-?\d+" s))
          (catch Exception e nil))))




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



#?(:clj
   (defn fn->name [f]
     (-> (str f)
         clojure.main/demunge
         (clojure.string/split #"@")
         first
         symbol)))



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



#?(:clj
   (defn normalize-path [path]
     (-> (.replaceFirst path "^~" (System/getProperty "user.home"))
         io/file
         .getCanonicalPath)))



#?(:clj
   (defn format-parent [file]
     (let [fname (.getName file)
           parent (->> (.getParent file) (re-find #"\w*$"))]
       (str (some-> (not-empty parent) (str "/"))
            fname))))


#?(:clj
   (defn backup [path]
     (try
       (let [old (str path ".1")]
         (when (exists? old)
           (io/copy (io/file old)
                    (io/file (str path ".2"))))

         (io/copy (io/file path)
                  (io/file old)))
       
       
       (catch Exception _))
     path))



#?(:clj
   (defn backup-and-delete [path]
     (backup path)
     (io/delete-file path true)
     path))
