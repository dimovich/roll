(ns example.server
  (:require [roll.core :as roll]
            [hiccup.core :as hiccup]
            [ring.util.response :as resp]
            [ring.middleware.session :as ring-session]
            [ring.middleware.anti-forgery :as anti-forgery]
            [ring.middleware.session.cookie :refer [cookie-store]]))


(defn wrap-session [handler]
  (-> handler
      (anti-forgery/wrap-anti-forgery)
      (ring-session/wrap-session
       {:cookie-attrs {:max-age 3600}
        :store (cookie-store {:key "example.cookie!!"})})))


(defn index [req]
  (resp/response
   (hiccup/html
    [:div "hello world"]
    
    ;; Sente CSRF
    [:div#sente-csrf-token
     {:data-csrf-token (force anti-forgery/*anti-forgery-token*)}]

    [:script {:src "js/main.js" :type "text/javascript"}]
    [:script "example.client.init()"])))


(defn -main []
  (roll/init "config.edn"))
