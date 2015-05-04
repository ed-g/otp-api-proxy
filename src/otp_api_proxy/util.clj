(ns otp-api-proxy.util
  (:require [clojure.edn :as edn]
            [clojure.tools.nrepl.server :as nrepl-server]
            [cider.nrepl :refer (cider-nrepl-handler)] 
            [liberator.core :refer [resource defresource]]
            [ring.middleware.params :refer [wrap-params]]
            [compojure.core :refer [defroutes context ANY]]
            [clojure.string :refer [split]]
            [clojure.data.json :as json]
            [cheshire.core :as cheshire]
            [cheshire.generate :as cheshire-generate]
            [clj-http.client :as http-client]
            [otp-api-proxy.test-data :as test-data]
            [clojure.walk :as walk]
            [clojure.java.javadoc]))

(defn pretty-json [value]
  (cheshire/encode value {:pretty true}))

(defn otp-response->plan [r]
  (get-in r [:body :plan]))


(defn otp-response->remove-trace [r]
  (-> r
      (dissoc :orig-content-encoding)
      (dissoc :trace-redirects)))

(def route-lines-request
  "retrieve route_lines file which contains human-readable route frequency information"
  (memoize (fn []
             (let [route-lines-url 
                   (str "http://rideart.org/wp-content/transit-data/route_lines.ssv")]
               (:body
                 (http-client/get route-lines-url))))))

(defn anaheim-route-lines []
  (let [lines (split (route-lines-request) #"\n")]
    (for [l lines]
      (let [fields (split l #"\\")]
        {:route_id (nth fields 0)
         :frequency (nth fields 5)}
        ))))

(defn anaheim-frequency-for-route-id [id]
  (-> (filter (comp #{id} :route_id) (anaheim-route-lines))
      first
      :frequency))


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
  ;; should we do any caching here??
  (gtfs-api-routes-span-request route-id day-in-la))

(defn anaheim-stop-text2go [stop-id]
  (let [stops (load-anaheim-stops!!)]
    (:stop_code
      (first (filter #(= (:stop_id %) stop-id) stops )))))


;; TODO: very important, sanitize the arguments passed.
(defn otp-request-live [params]
  (let [pass-arg (fn [arg] (if (get params arg)
                             (str arg "=" (get params arg))
                             ""))
        otp-url 
        (str "http://anaheim-otp.ed-groth.com/otp/routers/default/plan"
             "?"
             (reduce str [(pass-arg "fromPlace")
                          "&" (pass-arg "toPlace")
                          "&" (pass-arg "time")
                          "&" (pass-arg "date")
                          "&mode=TRANSIT,WALK"
                          "&maxWalkDistance=750"
                          "&walkReluctance=40"
                          "&walkSpeed=0.3"
                          "&arriveBy=false"
                          "&showIntermediateStops=false"
                          ]))]
    ; {:body {:plan otp-url}} ))
    (http-client/get otp-url {:as :json})))

(defn simple-otp-request-cached []
  test-data/otp-response-1)

(defn simple-otp-request-live-2 []
  (let [test-otp-url 
        (str "http://anaheim-otp.ed-groth.com/otp/routers/default/plan"
             "?"
             (reduce str (interpose "&" 
                                    [ "fromPlace=33.8046480634388,-117.915358543396"
                                      "toPlace=33.82422318995612,-117.90390014648436"
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

(defn transit-route-sequence
  "transit routes for each leg of itinerary itin, in order.
  non-transit legs are ignored."
  [itin]
  (let [legs (:legs itin)
        legs-with-routeid (remove #(nil? (:routeId %))
                                    legs)]
    (into [] (map :routeId legs-with-routeid))))

(defn collect-by-route-sequence [itins]
  (reduce (fn [acc itin]
            (update-in acc 
                       [(transit-route-sequence itin)]
                       (fn [itins] 
                         ((fnil conj [])
                          itins itin))))
          ;; itins ((juxt :duration :endTime) itin)))))
          {}
          itins))


(defn summarize-collection [itin-collection]
  (let [itin-lol (vals itin-collection)]
    (for [itins itin-lol]
      (let [fst (first itins)
            durations (map :duration itins)
            num-itins  (count itins)
            maxduration (reduce max durations)
            minduration (reduce min durations)]
        (-> fst
            (dissoc :startTime :endTime :duration)
            ;; TODO: summarize the min/max duration for each leg??
            (assoc :legs (map #(dissoc % :startTime :endTime )
                              (:legs fst)))
            (assoc :minDuration minduration)
            (assoc :maxDuration maxduration)
            (assoc :countWithThisRouteSequence num-itins))))))



;; Fixme: verify agency-id along with stop code
(defn plan->add-text2go
  "add text2go codes into the stopCode field of an OTP Itinerary"
  [plan]
  (let [walk-add-code (fn [x]
                        (if (:stopId x)
                          (assoc x
                                 :stopCode
                                 (anaheim-stop-text2go
                                   (get-in x [:stopId :id])))
                          x))]
    (clojure.walk/postwalk walk-add-code plan)))


(defn plan->merge-similar
  "merge itineraries which use the same bus routes in the same order, since our
  ridership does not operate on an exact schedule."
  [plan]
  ;; see java.util.Comparator docs 
  (let [itins (:itineraries plan)
        itins-merged (summarize-collection (collect-by-route-sequence itins))
        itins-sorted (sort
                       (fn [a b]
                         (- (:walkDistance a) (:walkDistance b)))
                       itins-merged)]
    (assoc plan :itineraries itins-sorted)))

(defn plan->add-route-url 
  "Add routeUrl (schedule information) for each route in otp itinerary."
  [plan]
  (let [walk-add-url (fn [x]
                       (if (:routeId x)
                         (assoc x
                                :routeUrl
                                (anaheim-route-url
                                  (get x :routeId)))
                         x))]
    (clojure.walk/postwalk walk-add-url plan)))

(defn plan->add-frequency
  "Add route frequency for each route in otp itinerary."
  [plan]
  (let [walk-add-url (fn [x]
                       (if (:routeId x)
                         (assoc x
                                :routeHumanFrequency
                                (anaheim-frequency-for-route-id
                                  (get x :routeId)))
                         x))]
    (clojure.walk/postwalk walk-add-url plan)))


(defn service-date->day-in-la 
  "convert service date of the form YYYYMMDD to YYYY-MM-DD"
  [service-date]
  (clojure.string/replace service-date #"^(....)(..)(..)$" "$1-$2-$3"))

  
(defn plan->add-route-span
  "add routeSpan (service span) for each route in otp itinerary"
  [itin]
  (let [walk-add-span (fn [x]
                       (if (and (:serviceDate x) (:routeId x))
                         (assoc x
                                :routeSpan
                                (anaheim-route-span
                                  (get x :routeId)
                                  (service-date->day-in-la (:serviceDate x))))
                         x))]
    (clojure.walk/postwalk walk-add-span itin)))


