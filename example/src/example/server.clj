(ns example.server
  (:require [roll.core :as roll]
            [hiccup.core :as hiccup]
            [ring.util.response :as resp]
            [ring.middleware.session :as ring-session]
            [ring.middleware.anti-forgery :as anti-forgery]
            [ring.middleware.session.cookie :as cookie]))


(defn wrap-session [handler]
  (-> (anti-forgery/wrap-anti-forgery handler)
      (ring-session/wrap-session
       {:cookie-attrs {:max-age 3600}
        :store (cookie/cookie-store {:key "example.cookie!!"})})))


(defn index [req]
  (resp/response
   (hiccup/html
    [:html
     [:head
      [:meta {:charset "UTF-8"}]]
     [:body
      [:div "hello world"]
    
      ;; Sente CSRF
      [:div#sente-csrf-token
       {:data-csrf-token (:anti-forgery-token req)}]

      [:script {:src "js/main.js" :type "text/javascript"}]
      [:script "example.client.init()"]]])))


(defn -main []
  (roll/init "config.edn"))
