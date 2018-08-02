(ns roll.handler
  (:require [ring.middleware.params         :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [muuntaja.middleware            :refer [wrap-format]]
            [taoensso.timbre :refer [info]]
            [reitit.core     :as r]
            [reitit.ring     :as ring]
            [integrant.core  :as ig]))



(def default-middlewares
  [wrap-format
   wrap-params
   wrap-keyword-params])



(defn init-router [& [{:keys [sente routes]}]]
  (let [new-routes
        (cond-> []
          routes (into @(resolve routes))
          sente  (conj ["/chsk" {:get  (:ring-ajax-get-or-ws-handshake sente)
                                 :post (:ring-ajax-post sente)}])
          true   (conj {:data {:middleware default-middlewares}}))]

    (info "routes:" (clojure.pprint/pprint new-routes))
    
    (ring/router new-routes)))



(defn init-handler [router]
  (ring/ring-handler
   router
   (ring/routes
    (ring/create-resource-handler {:path "/"})
    (ring/create-default-handler))))


(def ring-handler (atom (promise)))

(defn default-handler [req]
  (@@ring-handler req))


(defmethod ig/init-key :adapter/handler [_ {:as opts :keys [sente routes handler]}]
  (info "initializing handler:" opts)

  (if handler
    @(resolve handler)
    
    (do (->> (init-router opts)
             (init-handler)
             (deliver @ring-handler))
        default-handler)))


;; make sure it's initialized even if we don't specify handler
;; (but httpkit wont work for example)
