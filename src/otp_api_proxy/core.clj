(ns otp-api-proxy.core
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
            ))

(defonce hack-hack-hack
  (nrepl-server/start-server :port 4100 :host "127.0.0.1"
                             :handler cider-nrepl-handler))


(defn pretty-json [value]
  (cheshire/encode value {:pretty true}))

(defn add-header
  [a-handler header value]
  (fn [req]
    (assoc-in (a-handler req) [:headers header] value)))

(defresource ws-help []
  :last-modified  #inst "2015-04-09"
  :available-media-types ["application/json" "text/plain"]
  :handle-ok (pretty-json
               { :help-message "Please use one of the following services"
                 :services [ 
                             { :name "Echo service" 
                               :url "/hello-world/echo" } 
                             { :name "OTP Example" 
                               :url "/otp-example" } 
                            ]}
               ))

(defresource ws-echo [echo]
  :last-modified  #inst "2015-04-09"
  :available-media-types ["application/json" "text/plain"]

  :handle-ok (pretty-json { :message "hello, world" :echo echo }))

(defresource ws-otp-example []
  :last-modified  #inst "2015-04-09"
  :available-media-types ["application/json" "text/plain"]
  :handle-ok (pretty-json (simple-otp-request)))

(defresource ws-otp-pass-through [otp-instance route-params params request]
  :last-modified  #inst "2015-04-09"
  :available-media-types ["application/json" "text/plain"]
  ;; :handle-ok (pretty-json (simple-otp-request)))
  :handle-ok 
    (pretty-json { :otp-instance otp-instance 
                   :route-params route-params
                   :params params 
                   :request (:uri request)} ))

(defresource ws-otp-params [params]
  :last-modified  #inst "2015-04-09"
  :available-media-types ["application/json" "text/plain"]
  ;; :handle-ok (pretty-json (simple-otp-request)))
  :handle-ok (let [params (merge params)] (pretty-json { :otp-instance (str (type params)) :params params} )))


(defroutes app
  (ANY "/" []
       (ws-help))
  (ANY "/otp-example" []
       (ws-otp-example ))
  ;; (ANY "/otp-params/:otp-instance" {params :params}
  (context "/trip-planner/:otp-instance" [otp-instance]
    ;; (ANY "/pass-through" {route-params :route-params params :params}
    (ANY "/pass-through" request
       (ws-otp-pass-through otp-instance
                            (:route-params request)
                            (:params request)
                            request)))
  (ANY "/otp-params" {params :params}
       (ws-otp-params params))
  (ANY "/hello-world/:echo" [echo]
       (ws-echo echo)))

(def handler
  (add-header (-> app
                wrap-params)
              "Access-Control-Allow-Origin" "*" ;; javascript cross domain scripting
              ))


(defn scratch-pad []
  ;; 
  (let [test-otp-url "http://anaheim-otp.ed-groth.com/otp/routers/default/plan?fromPlace=33.8046480634388,-117.915358543396&toPlace=33.77272636987434,-117.8671646118164&time=1:29pm&date=03-31-2015&mode=TRANSIT,WALK&maxWalkDistance=750&walkReluctance=40&walkSpeed=0.3&arriveBy=false&showIntermediateStops=false&_=1428612154915"]
    (http-client/get test-otp-url {:as :json}))

  (http-client/get "http://localhost:4000/" {:as :json})
  (http-client/get "http://localhost:4000/" {:as :json})
  )

(defn simple-otp-request []
  (let [test-otp-url "http://anaheim-otp.ed-groth.com/otp/routers/default/plan?fromPlace=33.8046480634388,-117.915358543396&toPlace=33.77272636987434,-117.8671646118164&time=1:29pm&date=03-31-2015&mode=TRANSIT,WALK&maxWalkDistance=750&walkReluctance=40&walkSpeed=0.3&arriveBy=false&showIntermediateStops=false&_=1428612154915"]
    (http-client/get test-otp-url {:as :json})))

