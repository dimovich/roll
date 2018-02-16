(ns roll.macros
  (:import java.io.File))


(defmacro load-edn [file]
  (slurp file))
