(ns roll.util
  (:require [clojure.java.io :as io]
            [com.rpl.specter :as sr :refer [ALL MAP-VALS transform]]))


;; todo: make recursive
(defn resolve-map-syms
  "Find values that are symbols, and resolve them."
  [m]
  (->> m (transform
          [MAP-VALS]
          (fn [v]
            (if (symbol? v)
              @(resolve v)
              v)))))




(defn read-edn [path]
  (with-open [r (java.io.PushbackReader.
                 (clojure.java.io/reader path))]
    (clojure.edn/read r)))




(defn write-edn [path data]
  (with-open [w (clojure.java.io/writer path)]
    (binding [*out* w
              *print-length* nil]
      (pr data))))




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

