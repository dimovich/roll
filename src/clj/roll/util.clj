(ns roll.util
  (:require [clojure.java.io :as io]
            [com.rpl.specter :as sr :refer [ALL MAP-VALS transform]])
  (:import [java.io PushbackReader]))



(defn sym->var [s]
  (if (symbol? s)
    @(resolve s)
    s))


(declare resolve-map-syms)
(declare resolve-coll-syms)


(defn resolve-sym [v]
  (cond
    (coll? v) (resolve-coll-syms v)
    (map? v) (resolve-map-syms v)
    :else (sym->var v)))


(defn resolve-map-syms [m]
  (transform [MAP-VALS] resolve-sym m))


(defn resolve-coll-syms [coll]
  (transform [ALL] resolve-sym coll))



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



(defn read-edn-seq [path]
  (with-open [r (PushbackReader. (io/reader (get-path path)))]
    (doall (take-while #(not= ::EOF %) (repeatedly #(read-one r))))))




(defn write-edn [path data & opts]
  (with-open [w (apply io/writer path opts)]
    (binding [*print-length* nil
              *out* w]
      (prn data))))




(defmacro load-edn [file]
  (slurp file))



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



(defn slurp-tsv
  "Download and decode tsv."
  [url & [fields]]
  (some->>
   ;; split into lines
   (clojure.string/split (slurp url)  #"\r\n")
   ;; each line split into columns
   (map #(clojure.string/split % #"\t"))
   ;; remove lines that don't have the first column
   (remove #(-> % first empty?))
   ;; remove header
   rest
   (#(cond->> %
       ;; extract only given fields
       fields (map (fn [xs] (zipmap fields xs)))))))



(defn coerce-map [coerce-fns m]
  (loop [m m
         ks (keys coerce-fns)]
    (let [[k & rst] ks]
      (if k
        (recur (update m k (get coerce-fns k))
               rst)
        m))))



(defn coll->pattern [coll]
  (re-pattern
   (str "(?i)\\b("
        (->> (interpose "|" coll)
             (apply str))
        ")\\b")))

