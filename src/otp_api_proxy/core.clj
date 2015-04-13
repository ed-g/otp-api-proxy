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
            [otp-api-proxy.test-data :as test-data]
            [clojure.walk :as walk]
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
                             { :name "OTP Server Proxy Test" 
                               :url "/trip-planner/anaheim-ca-us/plan" } 
                              { :name "OTP Server Proxy Test" 
                               :url "/trip-planner/anaheim-ca-us/pass-through" } 
                             { :name "OTP Params Test" 
                               :url "/otp-params" } 
                             { :name "OTP Example" 
                               :url "/otp-example" } 
                            ]}
               ))

(defn otp-response->itinerary [r]
  (get-in r [:body :plan]))


(defn otp-response->remove-trace [r]
  (-> r
      (dissoc :orig-content-encoding)
      (dissoc :trace-redirects)))


(comment "http://archive.oregon-gtfs.com/gtfs-api/route-span/day-in-la/2015-4-13/by-feed/anaheim-ca-us/route-id/1704")

(comment "http://gtfs-api.ed-groth.com/gtfs-api/stops/by-feed/anaheim-ca-us")

(comment "http://gtfs-api.ed-groth.com/gtfs-api/routes/by-feed/anaheim-ca-us")


(defn gtfs-api-stops-request []
  (let [test-api-url 
        (str "http://gtfs-api.ed-groth.com/gtfs-api/"
             (reduce str (interpose "/" 
                                    [ "stops"
                                      "by-feed/anaheim-ca-us"
                                      ])))]
    (:body
      (http-client/get test-api-url {:as :json}))))

(comment 
  (let [rs (simple-gtfs-api-routes-request)]
    (clojure.pprint/pprint (map :route_url (:body rs)))))

(defn gtfs-api-routes-request []
  (let [test-api-url 
        (str "http://gtfs-api.ed-groth.com/gtfs-api/"
             (reduce str (interpose "/" 
                                    [ "routes"
                                      "by-feed/anaheim-ca-us"
                                      ])))]
    (:body
      (http-client/get test-api-url {:as :json}))))


;; It would be really handy if we could retrieve the time zone from the API.
(defn clean-route-span [route-span]
  (let [remove-debug-info
        (fn [early-or-late]
          (-> early-or-late
              (dissoc :departure_time_la)
              (dissoc :service_time_range)))]
  { :early (remove-debug-info (:early route-span)) 
    :late  (remove-debug-info (:late  route-span)) }))

;; "http://gtfs-api.ed-groth.com/gtfs-api/route-span/day-in-la/2015-4-13/by-feed/anaheim-ca-us/route-id/1700"
(defn gtfs-api-routes-span-request
  "route-id should be a text gtfs route_id. day-in-la is formatted YYYY-MM-DD."
  [route-id day-in-la]
  (let [test-api-url 
        (str "http://gtfs-api.ed-groth.com/gtfs-api/route-span/"
             (reduce str (interpose "/" 
                                    [ "day-in-la" day-in-la
                                      "by-feed/anaheim-ca-us"
                                      "route-id" route-id
                                      ])))]
    (-> (:body
          (http-client/get test-api-url {:as :json}))
        clean-route-span)))

(defn demo-gtfs-api-routes-span-request []
  (gtfs-api-routes-span-request "1702" "2015-04-13"))


;; Clearly *anaheim-routes / *anaheim-stops is a huge hack. 
;; We should instead maintain a data cache with expiration, based on the HTTP
;; Expiration headers from the REST server.  Is there a clojure library which
;; provides this at the HTTP level?  Also, we should retry if the first request
;; fails or times out, and fall over to other servers. This all points at an
;; HTTP client library.
;;
(do 
  (defonce *anaheim-routes (atom nil))
  (defn load-anaheim-routes!! [] 
    (when-not @*anaheim-routes
      (reset! *anaheim-routes 
              (gtfs-api-routes-request)))
    @*anaheim-routes))

(do
  (defonce *anaheim-stops (atom nil))
  (defn load-anaheim-stops!! []
    (when-not @*anaheim-stops
      (reset! *anaheim-stops 
              (gtfs-api-stops-request)))
    @*anaheim-stops))

(defn anaheim-route-url [route-id]
  (let [routes (load-anaheim-routes!!)]
    (:route_url
      (first (filter #(= (:route_id %) route-id) routes )))))

(defn anaheim-route-span [route-id day-in-la]
  (let [routes (load-anaheim-routes!!)]
    (:route_url
      (first (filter #(= (:route_id %) route-id) routes )))))

(defn anaheim-stop-text2go [stop-id]
  (let [stops (load-anaheim-stops!!)]
    (:stop_code
      (first (filter #(= (:stop_id %) stop-id) stops )))))


(defn simple-otp-request-live []
  (let [test-otp-url 
        (str "http://anaheim-otp.ed-groth.com/otp/routers/default/plan"
             "?"
             (reduce str (interpose "&" 
                                    [ "fromPlace=33.8046480634388,-117.915358543396"
                                      "toPlace=33.77272636987434,-117.8671646118164"
                                      "time=1:29pm&date=03-31-2015"
                                      "mode=TRANSIT,WALK"
                                      "maxWalkDistance=750"
                                      "walkReluctance=40"
                                      "walkSpeed=0.3"
                                      "arriveBy=false"
                                      "showIntermediateStops=false"
                                      ;;"_=1428612154915"
                                      ])))]
    (http-client/get test-otp-url {:as :json})))


;; Fixme: verify agency-id along with stop code
(defn itinernary->add-text2go
  "add text2go codes into the stopCode field of an OTP Itinerary"
  [itin]
  (let [add-code (fn [i path]
                   (assoc-in i
                             (concat path [:stopCode])
                             (anaheim-stop-text2go 
                               (get-in i (concat path [:stopId :id])))))
        walk-add-code (fn [i]
                        (clojure.walk/postwalk
                          (fn [x]
                            (if (:stopId x)
                              (assoc x
                                     :stopCode
                                     (anaheim-stop-text2go
                                       (get-in x [:stopId :id])))
                              x))
                          i))]
    (-> itin
        (add-code [:from]) 
        (add-code [:to])
        (walk-add-code)
        )))

(defresource ws-echo [echo]
  :last-modified  #inst "2015-04-09"
  :available-media-types ["application/json" "text/plain"]

  :handle-ok (pretty-json { :message "hello, world" :echo echo }))

(defresource ws-otp-example []
  :last-modified  #inst "2015-04-09"
  :available-media-types ["application/json" "text/plain"]
  :handle-ok (pretty-json 
               (-> (simple-otp-request-cached)
                   otp-response->itinerary)))

(defresource ws-otp-cooked []
  :last-modified  #inst "2015-04-09"
  :available-media-types ["application/json" "text/plain"]
  :handle-ok (pretty-json 
               (-> (simple-otp-request-cached)
                   otp-response->itinerary
                   itinernary->add-text2go
                   )))

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
  ;; (ANY "/otp-params/:otp-instance" {params :params}
  (context "/trip-planner/:otp-instance" [otp-instance]
    ;; (ANY "/pass-through" {route-params :route-params params :params}
     (ANY "/plan" []
       (ws-otp-example))
     (ANY "/plan-cooked" []
       (ws-otp-cooked))
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


