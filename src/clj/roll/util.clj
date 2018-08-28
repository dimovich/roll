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


(defmacro load-edn [file]
  (slurp file))



(defmacro with-in-> [in & body]
  `(-> ~in
       slurp
       clojure.edn/read-string
       ~@body))



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
  [url & [columns]]
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
       ;; coerce
       columns (map (fn [xs] (zipmap columns xs)))))))



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
