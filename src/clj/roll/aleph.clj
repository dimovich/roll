(ns roll.aleph
  (:require [taoensso.timbre :refer [info]]
            [integrant.core :as ig]
            [aleph.http :as http]
            [manifold.deferred :as d]
            [roll.handler :refer [get-default-handler]]
            [roll.util :as u]))



;; Middleware
;;

(defn wrap-ring-async
  "Converts given asynchronous Ring handler to Aleph-compliant handler."
  [handler]
  (fn [request]
    (let [response (d/deferred)]
      (handler request #(d/success! response %) #(d/error! response %))
      response)))




(defn wrap-ring-async-identity
  "Force the use of asynchronous Ring handler arity."
  [handler]
  (fn [request]
    (handler request identity identity)))




(defn wrap-deferred
  "Chains the deferred handler response for the next asynchronous Ring
  middlewares that expect plain map responses."
  [handler]
  (fn [request respond raise]
    (-> (d/chain' (handler request)
                  respond)
        (d/catch' raise))))





;; Integrant
;;



(defmethod ig/prep-key :roll/aleph [_ config]
  ;; make sure we have a handler
  (merge {:handler (ig/ref :roll/handler)} config))




(defmethod ig/init-key :roll/aleph [_ opts]
  (when-let [opts (cond (map? opts) (u/resolve-syms opts)
                        (true? opts) {}
                        :default nil)]

    (info "starting roll/aleph:")
    (info (u/spp (update opts :handler #(cond-> % (fn? %) u/fn->name))))

    (let [{:as opts :keys [handler port]
           :or {port 5000}} opts]
      (http/start-server handler {:port port}))))



(defmethod ig/halt-key! :roll/aleph [_ stop-fn]
  (when stop-fn
    (info "stopping roll/aleph...")
    (.close stop-fn)))

