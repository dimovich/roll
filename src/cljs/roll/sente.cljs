(ns roll.sente
  (:require [taoensso.timbre :refer [info]]
            [taoensso.sente  :as sente :refer [cb-success?]]
            [taoensso.sente.packers.transit :as sente-transit])
  
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]))



(def sente-fns (atom nil))


(defn send [& args]
  (apply (:chsk-send! @sente-fns) args))



(defn init-sente [& [opts]]
  (let [{:keys [chsk ch-recv send-fn state]}
        (sente/make-channel-socket-client!
         "/chsk"
         (-> {:packer (sente-transit/get-transit-packer)}
             (merge opts)))]
    {:chsk       chsk
     :ch-chsk    ch-recv
     :chsk-send! send-fn
     :chsk-state state}))





(defonce router_ (atom nil))
(defn  stop-router! [] (when-let [stop-f @router_] (stop-f)))
(defn start-router! [msg-handler]
  (stop-router!)
  (let [{:keys [ch-chsk]} (->> (init-sente)
                               (reset! sente-fns))]
    (reset! router_
            (sente/start-client-chsk-router! ch-chsk msg-handler))))

