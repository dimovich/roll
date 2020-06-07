(ns roll.sente
  (:require [taoensso.timbre :refer [info]]
            [taoensso.sente  :as sente :refer [cb-success?]]
            [taoensso.sente.packers.transit :as st]))


(defonce sente-fns nil)


(defn send-msg [& args]
  (some-> (:chsk-send! sente-fns)
          (apply args)))



(defmulti event-msg-handler :id)

;; Default/fallback case (no other matching handler)
(defmethod event-msg-handler :default
  [{:as ev-msg :keys [event id]}])




(defn get-packer [& [{:keys [write-handlers read-handlers]}]]
  (st/->TransitPacker
   :json
   {:handlers (into {} (apply merge write-handlers))}
   {:handlers (into {} (apply merge read-handlers))}))



(def ?csrf-token
  (when-let [el (.getElementById js/document "sente-csrf-token")]
    (.getAttribute el "data-csrf-token")))



(defn init-sente [& [{:as init-opts :keys [packer path opts]}]]
  (let [{:keys [chsk ch-recv send-fn state]}
        (sente/make-channel-socket-client!
         (or path "/chsk")
         ?csrf-token
         (merge {:packer (get-packer packer)} opts))]
    {:chsk       chsk
     :ch-chsk    ch-recv
     :chsk-send! send-fn
     :chsk-state state}))



(defn stop-router! []
  (when-let [stop-fn (:stop-fn sente-fns)] (stop-fn))
  (set! sente-fns nil))



(defn start-router! [& [{:as opts :keys [handler]
                         :or {handler event-msg-handler}}]]
  (stop-router!)
  (let [fns (init-sente (dissoc opts :handler))]
    (->> (sente/start-client-chsk-router! (:ch-chsk fns) handler)
         (assoc fns :stop-fn)
         (set! sente-fns))))


(def start! start-router!)
(def stop!  stop-router!)
