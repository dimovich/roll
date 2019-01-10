(ns roll.handler
  (:require [taoensso.timbre :refer [info]]
            [ring.middleware.params         :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [muuntaja.middleware            :refer [wrap-format]]
            [integrant.core :as ig]
            [reitit.core :as r]
            [reitit.ring :as ring]
            [roll.sente  :as sente]
            [roll.util   :refer [resolve-map-syms]]))



(def default-middleware
  [wrap-params
   wrap-keyword-params
   wrap-format])



(defn init-router
  "Create router with default middleware and optional extra routes and middleware."
  [& [{:keys [sente routes middleware]}]]
  (let [new-routes
        (cond-> []
          routes (into routes)
          sente  (conj ["/chsk" {:get  (:ring-ajax-get-or-ws-handshake sente)
                                 :post (:ring-ajax-post sente)}]))]

    (ring/router
     new-routes
     {:data {:middleware (into default-middleware middleware)}})))



(defn init-handler
  "Initialize ring handler."
  [& [opts]]
  (ring/ring-handler
   (init-router opts)
   (ring/routes
    (ring/create-resource-handler {:path "/"})
    (ring/create-default-handler))))



(def ring-handler (atom (promise)))

(defn default-handler [req]
  (@@ring-handler req))



(defn get-default-handler
  "Make sure we have an initialized handler and return it."
  []
  (when-not (realized? @ring-handler)
    (deliver @ring-handler (init-handler)))
  default-handler)




(defmethod ig/init-key :roll/handler [_ opts]
  (info "initializing handler with" (keys opts))

  (let [{:as opts :keys [handler]}
        (cond-> (resolve-map-syms opts)
          (true? (:sente opts))
          (assoc :sente (sente/start-sente)))]

    (->> (or handler (init-handler opts))
         (deliver @ring-handler))
    
    default-handler))

