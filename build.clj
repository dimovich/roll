(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'dimovich/roll)
(def version (format "0.3.%s" (b/git-count-revs nil)))
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def jar-file (format "target/%s-%s.jar" (name lib) version))

(defn clean [_]
  (b/delete {:path "target"}))

(defn jar [_]
  (b/write-pom {:src-pom "template/pom.xml"
                :class-dir class-dir
                :lib lib
                :version version
                :basis basis
                :src-dirs ["src"]})
  
  (b/copy-dir {:src-dirs ["src/clj" "src/cljc" "src/cljs"]
               :target-dir class-dir})
  
  (b/copy-file {:src "src/data_readers.clj"
                :target (str class-dir "/data_readers.clj")})
  
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))
