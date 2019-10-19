(ns roll.handler
  (:require [taoensso.timbre :refer [info]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.session :as ring-session]
            [ring.middleware.anti-forgery :as anti-forgery]
            [integrant.core :as ig]
            [reitit.core :as reitit]
            [reitit.ring :as ring]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.middleware :as middleware]
            ;;[reitit.ring.middleware.dev :as rdev]
            [muuntaja.core :as m]
            [roll.sente :as sente]
            [roll.util :refer [resolve-map-syms spp]]))



(defonce ring-handler (promise))


(def default-middleware
  [wrap-params
   wrap-keyword-params
   muuntaja/format-middleware])


(def session-middleware
  [ring-session/wrap-session
   anti-forgery/wrap-anti-forgery])



(defn wrap-csrf
  "Replace {{csrf}} in response body with actual token."
  [handler]
  (fn [req]
    (let [resp (handler req)]
      (update resp :body
              clojure.string/replace "{{csrf}}"
              (str "<div id=\"sente-csrf-token\" data-csrf-token=\""
                   (:anti-forgery-token req) "\"></div>")))))



(defn init-router
  "Create router with optional extra routes"
  [& [{:as opts :keys [sente routes conflicts]}]]
  (let [new-routes (cond->> routes
                     sente  (into [(:routes sente)]))]

    (ring/router
     new-routes
     (cond-> { ;;:reitit.middleware/transform rdev/print-request-diffs
              :data {:muuntaja m/instance}}
       (not (true? conflicts))
       (assoc :conflicts conflicts)

       :default (merge (select-keys opts [::middleware/transform]))))))



(defn init-handler
  "Initialize ring handler."
  [& [{:as opts :keys [sente middleware]}]]
  (ring/ring-handler
   (init-router opts)
   (ring/routes
    (ring/create-resource-handler {:path "/"})
    (ring/create-default-handler
     ;;(select-keys opts [:not-found])
     (merge
      {:not-found (constantly {:status 404 :body ""})}
      (select-keys opts [:not-found]))))

   {:middleware (or (some-> middleware flatten)
                    (cond-> default-middleware
                      sente (into session-middleware)))}))




(defn default-handler [req]
  (@ring-handler req))



(defn get-default-handler
  "Make sure we have an initialized handler and return it."
  [& [opts]]
  (when-not (or (realized? ring-handler)
                (delay? ring-handler))
    (deliver ring-handler (init-handler opts)))
  
  default-handler)




(defn href [router & keys]
  (when router
    (:path (apply reitit/match-by-name router keys))))




(defmethod ig/init-key :roll/handler [_ opts]
  (info "initializing roll/handler:")
  (info (spp (cond-> opts
               (:sente opts) (assoc :sente "{...}"))))

  (let [{:as opts :keys [handler]} (resolve-map-syms opts)]
    (->> (or handler (init-handler opts))
         ;; (future) might not be realized before next call to
         ;; (get-default-handler) => (init-handler)
         (delay)
         (constantly)
         (alter-var-root #'ring-handler)
         ;; force delay to realize
         deref)
    
    default-handler))




(defmethod ig/halt-key! :roll/handler [_ handler]
  (when handler
    (info "reseting roll/handler...")
    (alter-var-root #'ring-handler (constantly (promise)))))

