(ns roll.util)


(defmacro with-in-> [in & body]
  `(-> ~in
       slurp
       clojure.edn/read-string
       ~@body))


(defmacro with-out-> [out & body]
  `(-> ~@body
       pr-str
       (#(spit ~out %))))


(defmacro with-in-out-> [in out & body]
  `(-> ~in
       slurp
       clojure.edn/read-string
       ~@body
       pr-str
       (#(spit ~out %))))

