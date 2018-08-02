(ns roll.handler
  (:require [taoensso.timbre :refer [info]]
            [ring.middleware.params         :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [muuntaja.middleware            :refer [wrap-format]]
            [integrant.core :as ig]
            [reitit.core :as r]
            [reitit.ring :as ring]
            [roll.sente  :as sente]
            [roll.util   :refer [resolve-map-vals]]))



(def default-middlewares
  [wrap-params
   wrap-keyword-params
   wrap-format])



(defn init-router [& [{:keys [sente routes]}]]
  (let [new-routes
        (cond-> []
          routes (into routes)
          sente  (conj ["/chsk" {:get  (:ring-ajax-get-or-ws-handshake sente)
                                 :post (:ring-ajax-post sente)}]))]

    (ring/router
     new-routes
     {:data {:middleware default-middlewares}})))



(defn init-handler [& [opts]]
  (ring/ring-handler
   (init-router opts)
   (ring/routes
    (ring/create-resource-handler {:path "/"})
    (ring/create-default-handler))))



(def ring-handler (atom (promise)))

(defn default-handler [req]
  (@@ring-handler req))



(defn get-default-handler []
  (when-not (realized? @ring-handler)
    (deliver @ring-handler (init-handler)))
  default-handler)




(defmethod ig/init-key :adapter/handler [_ opts]
  (info "initializing handler with" (keys opts))

  (let [{:as opts :keys [handler]}
        (cond-> (resolve-map-vals opts)
          (true? (:sente opts))
          (assoc :sente (sente/start-sente)))]

    (->> (or handler (init-handler opts))
         (deliver @ring-handler))
    
    default-handler))

