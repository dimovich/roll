(ns roll.sente
  (:require [taoensso.timbre :refer [info]]
            [taoensso.sente  :as sente]
            [integrant.core  :as ig]
            [taoensso.sente.server-adapters.nginx-clojure :refer [get-sch-adapter]]
            ;;[taoensso.sente.server-adapters.http-kit :refer [get-sch-adapter]]
            [taoensso.sente.packers.transit :as sente-transit]
            [com.rpl.specter :as sr :refer [ALL MAP-VALS transform]]))


;; fixme: use tools.deps to dynamically load nginx / httpkit adapters?


(def sente-fns (atom nil))

(defn send-evt [& args]
  (some-> @sente-fns :chsk-send! (apply args)))

(defn connected-uids []
  (some-> @sente-fns :connected-uids))



#_(defn event-msg-handler
  "Wraps `-event-msg-handler` with logging, error catching, etc."
  [{:as ev-msg :keys [id ?data event]}]
  (-event-msg-handler ev-msg))



(defmulti event-msg-handler :id)

(defmethod event-msg-handler
  :default         ; Default/fallback case (no other matching handler)
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [session (:session ring-req)
        uid     (:uid     session)]
    (when ?reply-fn
      (?reply-fn {:umatched-event event}))))




(defn init-sente [& [opts]]
  (let [{:keys [ch-recv send-fn connected-uids
                ajax-post-fn ajax-get-or-ws-handshake-fn]}
        
        (sente/make-channel-socket!
         (get-sch-adapter)
         (-> {:packer (sente-transit/get-transit-packer)
              :user-id-fn #(:client-id %)}
             (merge opts)))]

    (->> {:ring-ajax-post                ajax-post-fn
          :ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn
          :ch-chsk                       ch-recv
          :chsk-send!                    send-fn
          :connected-uids                connected-uids})))



(defmethod ig/init-key :adapter/sente
  [_ opts]
  (info "starting sente: " opts)
  
  (let [{:as opts :keys [handler]
         :or {handler event-msg-handler}} (resolve-map-vals opts)
        
        fns (-> opts (dissoc :handler)
                (init-sente))]
    
    (->> handler
         (sente/start-server-chsk-router! (:ch-chsk fns))
         (assoc fns :stop-fn)
         (reset! sente-fns))))




(defmethod ig/halt-key! :adapter/sente
  [_ {:keys [stop-fn]}]
  
  (when stop-fn
    (info "stopping sente...")
    (stop-fn)))

