(ns roll.handler
  (:require [compojure.core   :refer [defroutes GET POST]]
            [compojure.route  :refer [files resources not-found]]
            [taoensso.timbre  :refer [info]]))


(defroutes handler
  (files     "/" {:root "resources"}) ;; to serve static resources
  (resources "/" {:root "resources"}) ;; to serve anything else
  (not-found "Page Not Found")) ;; page not found
