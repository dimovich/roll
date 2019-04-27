(ns roll.sente
  (:require [taoensso.timbre :refer [info]]
            [taoensso.sente  :as sente]
            [integrant.core  :as ig]
            [datascript.transit :as dt]
            [taoensso.sente.packers.transit :as sente-transit]
            [com.rpl.specter :as sr :refer [ALL MAP-VALS transform]]
            [roll.util :as u :refer [resolve-map-syms]]))



(defonce sente-fns (atom nil))

(defn send-evt [& args]
  (some-> @sente-fns :chsk-send! (apply args)))

(defn connected-uids []
  (some-> @sente-fns :connected-uids))



;; add new methods for custom events
(defmulti event-msg-handler :id)


(defmethod event-msg-handler
  :default         ; Default/fallback case (no other matching handler)
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [session (:session ring-req)
        uid     (:uid     session)]
    (when ?reply-fn
      (?reply-fn {:umatched-event event}))))



(defn broadcast [event]
  (when-let [uids (:any @(connected-uids))]
    ;;(info "broadcasting data to" (count uids) "clients...")
    (doseq [uid uids]
      (send-evt uid event))))



(defn get-sch-adapter []
  (when-let [server-name (cond
                           (resolve 'org.httpkit.server/run-server)
                           'http-kit

                           (resolve 'nginx.clojure.embed/run-server)
                           'nginx-clojure

                           (resolve 'aleph.http/start-server)
                           'aleph)]
    (let [sym (->> server-name (str "taoensso.sente.server-adapters.") symbol)]
      (require sym)
      ((resolve (symbol (str sym "/get-sch-adapter")))))))




(defn init-sente [& [opts]]
  (when-let [sch-adapter (get-sch-adapter)]
    (let [{:keys [ch-recv send-fn connected-uids
                  ajax-post-fn ajax-get-or-ws-handshake-fn]}
        
          (sente/make-channel-socket!
           sch-adapter
           ;;(sente-transit/get-transit-packer)
           (-> {:packer (sente-transit/->TransitPacker
                         :json
                         {:handlers dt/write-handlers}
                         {:handlers dt/read-handlers})
                :user-id-fn #(:client-id %)}
               (merge opts)))]

      (->> {:ring-ajax-post                ajax-post-fn
            :ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn
            :ch-chsk                       ch-recv
            :chsk-send!                    send-fn
            :connected-uids                connected-uids}))))



(defn start-sente [& [opts]]
  (info "starting roll/sente:")
  (info (u/spp opts))
  
  (let [{:as opts :keys [handler]
         :or {handler event-msg-handler}} (resolve-map-syms opts)]
    
    (when-let [fns (-> opts (dissoc :handler)
                       (init-sente))]
      (->> handler
           (sente/start-server-chsk-router! (:ch-chsk fns))
           (assoc fns :stop-fn)
           (reset! sente-fns)))))




(defmethod ig/init-key :roll/sente [_ opts]
  (start-sente opts))



(defmethod ig/halt-key! :roll/sente [_ {:keys [stop-fn]}]
  (when stop-fn
    (info "stopping roll/sente...")
    (stop-fn)))

