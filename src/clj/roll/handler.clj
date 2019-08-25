(ns ^{:clojure.tools.namespace.repl/load false}
    roll.handler
    (:require [taoensso.timbre :refer [info]]
              [ring.middleware.params :refer [wrap-params]]
              [ring.middleware.keyword-params :refer [wrap-keyword-params]]
              [integrant.core :as ig]
              [linked.core :as linked]
              [reitit.core :as r]
              [reitit.ring :as ring]
              [reitit.ring.middleware.muuntaja :as muuntaja]
              ;;[reitit.ring.middleware.dev :as rdev]
              [muuntaja.core :as m]
              [roll.sente :as sente]
              [roll.util :refer [resolve-map-syms spp]]))



(defonce _router (atom nil))
(def ^:dynamic *router*)

(defonce ring-handler (atom (promise)))


(def default-middleware
  [wrap-params
   wrap-keyword-params
   muuntaja/format-middleware])



(defn init-router
  "Create router with optional extra routes and default or optional
  middleware."
  [& [{:keys [sente routes middleware conflicts]}]]
  (let [new-routes (cond->> routes
                     sente  (into [(:routes sente)]))
        new-middleware (or middleware default-middleware)]

    (->> (ring/router
          new-routes
          (cond-> { ;;:reitit.middleware/transform rdev/print-request-diffs
                   :data {:muuntaja m/instance
                          :middleware new-middleware}}

            (not (true? conflicts))
            (assoc :conflicts conflicts)))
         (reset! _router))))



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




(defn default-handler [req]
  (binding [*router* @_router]
    (@@ring-handler req)))



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
               (:sente opts) (assoc :sente "{...}"))))

  (let [{:as opts :keys [handler]} (resolve-map-syms opts)]
    (->> (or handler (init-handler opts))
         ;; (future) might not be realized before next call to
         ;; (get-default-handler) => (init-handler)
         (delay)
         (reset! ring-handler)
         ;; force delay to realize
         deref)
    
    default-handler))




(defmethod ig/halt-key! :roll/handler [_ handler]
  (when handler
    (info "reseting roll/handler...")
    (reset! ring-handler (promise))))

