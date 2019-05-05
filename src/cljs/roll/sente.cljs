(ns roll.sente
  (:require [taoensso.timbre :refer [info]]
            [taoensso.sente  :as sente :refer [cb-success?]]
            [taoensso.sente.packers.transit :as st]
            [roll.util :as u]))




(defmulti event-msg-handler :id)

(defmethod event-msg-handler
  :default         ; Default/fallback case (no other matching handler)
  [{:as ev-msg :keys [event id]}])



(def sente-fns (atom nil))

(defn send-msg [& args]
  (apply (:chsk-send! @sente-fns) args))




(defn get-packer []
  (let [transit-handlers
        {:write-handlers
         (merge
          (u/resolve-cljs 'linked.transit     'write-handlers)
          (u/resolve-cljs 'datascript.transit 'write-handlers))

         :read-handlers
         (merge 
          (u/resolve-cljs 'linked.transit     'read-handlers)
          (u/resolve-cljs 'datascript.transit 'read-handlers))}]
    
    (info "making transit-packer with" transit-handlers)
    
    (->> transit-handlers
         ((juxt :write-handlers :read-handlers))
         (map (partial hash-map :handlers))
         (apply st/->TransitPacker :json))))




(defn init-sente [& [opts]]
  (let [{:keys [chsk ch-recv send-fn state]}
        (sente/make-channel-socket-client!
         "/chsk"
         (-> {:packer (get-packer)}
             (merge opts)))]
    {:chsk       chsk
     :ch-chsk    ch-recv
     :chsk-send! send-fn
     :chsk-state state}))




(defonce router_ (atom nil))
(defn  stop-router! [] (when-let [stop-f @router_] (stop-f)))
(defn start-router! [& [handler]]
  (stop-router!)
  (let [{:keys [ch-chsk]} (->> (init-sente)
                               (reset! sente-fns))]
    
    (->> (or handler event-msg-handler)
         (sente/start-client-chsk-router! ch-chsk)
         (reset! router_))))


(def start! start-router!)
(def stop!  stop-router!)
