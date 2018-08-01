(ns roll.handler
  (:require [ring.middleware.params         :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [muuntaja.middleware :refer [wrap-format]]
            [reitit.ring         :as ring]
            [integrant.core      :as ig]
            [taoensso.timbre  :refer [info]]))



(def default-middlewares
  [wrap-params
   wrap-keyword-params
   wrap-format])


(defn init-router [& [{:keys [sente routes]}]]
  (->
   (cond-> []
     routes (into @(resolve routes))
     sente  (conj ["/chsk" {:get  (:ring-ajax-get-or-ws-handshake sente)
                            :post (:ring-ajax-post sente)}]))
   
   (conj {:data {:middleware default-middlewares}})
   ring/router))


(def reitit-router (atom (init-router)))


(defn init-handler [router]
  (ring/ring-handler
   router
   (ring/routes
    (ring/create-resource-handler {:path "/"})
    (ring/create-default-handler))))


(def ring-handler (atom (init-handler (init-router))))


(defn handler [req]
  (@ring-handler req))



(defmethod ig/init-key :adapter/handler [_ {:keys [sente routes] :as opts}]
  (info "initializing handler:" opts)
  
  (->> (init-router opts)
       (init-handler)
       (reset! ring-handler))
  
  (or (some-> (:handler opts) resolve)
      handler))
