(ns otp-api-proxy.core
  (:use [otp-api-proxy.util]
        )
  (:require [clojure.edn :as edn]
            [clojure.tools.nrepl.server :as nrepl-server]
            [cider.nrepl :refer (cider-nrepl-handler)] 
            [liberator.core :refer [resource defresource]]
            [ring.middleware.params :refer [wrap-params]]
            [compojure.core :refer [defroutes context ANY]]
            [clojure.data.json :as json]
            [cheshire.core :as cheshire]
            [cheshire.generate :as cheshire-generate]
            [clj-http.client :as http-client]
            [clojure.walk :as walk]
            [clojure.java.javadoc]

            [otp-api-proxy.test-data :as test-data]
            [otp-api-proxy.web-service :as ws]
            ))

(defonce hack-hack-hack-start-nrepl-server
  (nrepl-server/start-server :port 4101 :bind "127.0.0.1"
                             :handler cider-nrepl-handler))

(defn ignore-trailing-slash
  "Modifies the request uri before calling the handler.  Removes a single
  trailing slash from the end of the uri if present.
 
  Useful for handling optional trailing slashes until Compojure's route
  matching syntax supports regex.  Adapted from
  http://stackoverflow.com/questions/8380468/compojure-regex-for-matching-a-trailing-slash"
  [handler]
  (fn [request]
    (let [uri (:uri request)]
      (handler (assoc request :uri (if (and (not (= "/" uri))
                                            (.endsWith uri "/"))
                                     (subs uri 0 (dec (count uri)))
                                     uri))))))
(defn add-header
  "add a header to a ring response map."
  [a-handler header value]
  (fn [req]
    (assoc-in (a-handler req) [:headers header] value)))

(defroutes app
  (ANY "/" []
       (ws/help))
  ;; (ANY "/otp-params/:otp-instance" {params :params}
  (context "/trip-planner/:otp-instance" [otp-instance]
     (ANY "/decode-polyline.js" []
        (ws/decode-polyline-function))
     (ANY "/plan" request
       (ws/otp-with-args (:params request)))
     (ANY "/plan-then-merge-by-route-sequence" request
       (ws/otp-merge-by-route-sequence (:params request)))
     (ANY "/plan-cooked" []
       (ws/otp-cooked))
     (ANY "/pass-through" request
       (ws/otp-pass-through otp-instance
                            (:route-params request)
                            (:params request)
                            request)))
  (ANY "/otp-params" {params :params}
       (ws/otp-params params))
  (ANY "/hello-world/:echo" [echo]
       (ws/echo echo)))


(def handler "ring handler"
  (add-header (-> app
                  ignore-trailing-slash
                  wrap-params)
              "Access-Control-Allow-Origin" "*" ;; javascript cross domain scripting
              ))

