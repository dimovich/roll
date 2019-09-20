(ns example.server
  (:require [roll.core :as roll]
            [roll.sente :refer [event-msg-handler]]
            [hiccup.core :as hiccup]
            [taoensso.timbre :refer [info]]
            [ring.util.response :as resp]
            [ring.middleware.session :as ring-session]
            [ring.middleware.anti-forgery :as anti-forgery]
            [ring.middleware.session.cookie :as cookie]))



(defn index [req]
  (resp/response
   (hiccup/html
    [:html
     [:head [:meta {:charset "UTF-8"}]]
     [:body
      [:div {:id "app"}]
      ;; Sente CSRF
      [:div#sente-csrf-token
       {:data-csrf-token (:anti-forgery-token req)}]

      [:script {:src "js/main.js" :type "text/javascript"}]
      [:script "example.client.init()"]]])))



;; we need cookies for websocket session
(defn wrap-session [handler]
  (-> (anti-forgery/wrap-anti-forgery handler)
      (ring-session/wrap-session
       {:cookie-attrs {:max-age 3600}
        :store (cookie/cookie-store {:key "example.cookie!!"})})))



;; websocket message handlers. add more as needed

(defmethod event-msg-handler :client/click
  [{:as ev-msg :keys [client-id ?data ?reply-fn]}]
  (info ?data "from" client-id)
  (when ?reply-fn
    (?reply-fn ["some" {:reply ?data}])))



(defn handshake-data-fn
  "Initial data sent to client."
  [{{:keys [client-id]} :params :as req }]
  [["some handshake data for" client-id]])



(defn -main []
  (roll/init "config.edn"))
