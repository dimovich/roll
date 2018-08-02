(ns roll.handler
  (:require [ring.middleware.params         :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [muuntaja.middleware            :refer [wrap-format]]
            [taoensso.timbre :refer [info]]
            [reitit.core     :as r]
            [reitit.ring     :as ring]
            [integrant.core  :as ig]
            [roll.util :refer [resolve-map-vals]]))



(def default-middlewares
  [wrap-params
   wrap-keyword-params
   wrap-format])



(defn init-router [& [{:keys [sente routes]}]]
  (let [new-routes
        (cond-> []
          routes (into routes)
          sente  (conj ["/chsk" {:get  (:ring-ajax-get-or-ws-handshake sente)
                                 :post (:ring-ajax-post sente)}])
          true   (conj {:data {:middleware default-middlewares}}))]

    (info "routes:")
    (clojure.pprint/pprint new-routes)
    
    (ring/router new-routes)))



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
  (info "initializing handler:" opts)

  (let [{:as opts :keys [handler]} (resolve-map-vals opts)]

    (->> (or handler (init-handler opts))
         (deliver @ring-handler))
    
    default-handler))



;; make sure it's initialized even if we don't specify handler
;; (but httpkit wont work for example)
