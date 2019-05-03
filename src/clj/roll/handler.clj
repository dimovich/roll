(ns roll.handler
  (:require [taoensso.timbre :refer [info]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [integrant.core :as ig]
            [reitit.core :as r]
            [reitit.ring :as ring]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            ;;[reitit.ring.middleware.dev :as rdev]
            [muuntaja.core :as m]
            [roll.sente :as sente]
            [roll.util :refer [resolve-map-syms spp]]))




(def default-middleware
  [wrap-params
   wrap-keyword-params
   muuntaja/format-middleware])



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
     {;;:reitit.middleware/transform rdev/print-request-diffs
      :data {:muuntaja m/instance
             :middleware (into default-middleware middleware)}})))



(defn init-handler
  "Initialize ring handler."
  [& [opts]]
  (ring/ring-handler
   (init-router opts)
   (ring/routes
    (ring/create-resource-handler {:path "/"})
    (ring/create-default-handler
     ;;(select-keys opts [:not-found])
     (merge
      {:not-found (constantly {:status 404 :body ""})}
      (select-keys opts [:not-found]))))))



(def ring-handler (atom (promise)))

(defn default-handler [req]
  (@@ring-handler req))



(defn get-default-handler
  "Make sure we have an initialized handler and return it."
  []
  (when-not (or (realized? @ring-handler)
                (delay? @ring-handler))
    (deliver @ring-handler (init-handler)))
  
  default-handler)




(defmethod ig/init-key :roll/handler [_ opts]
  (info "initializing roll/handler:")
  (info (spp (cond-> opts
               (:sente opts) (assoc :sente true))))

  (let [{:as opts :keys [handler]}
        (cond-> (resolve-map-syms opts)
          (true? (:sente opts))
          (assoc :sente (sente/start-sente)))]

    (->> (or handler (init-handler opts))
         (delay)
         (reset! ring-handler))
    
    default-handler))




(defmethod ig/halt-key! :roll/handler [_ handler]
  (when handler
    (info "reseting roll/handler...")
    (reset! ring-handler (promise))))


