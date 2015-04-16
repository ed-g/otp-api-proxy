(ns otp-api-proxy.scratch-pad
  (:use [otp-api-proxy.util]
        [otp-api-proxy.core]
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
            [otp-api-proxy.test-data :as test-data]
            [clojure.walk :as walk]
            [clojure.java.javadoc]
            [otp-api-proxy.web-service :as ws]
            )) 

(comment 
  "http://archive.oregon-gtfs.com/gtfs-api/route-span/day-in-la/2015-4-13/by-feed/anaheim-ca-us/route-id/1704")

(comment 
  "http://gtfs-api.ed-groth.com/gtfs-api/stops/by-feed/anaheim-ca-us")

(comment 
  "http://gtfs-api.ed-groth.com/gtfs-api/routes/by-feed/anaheim-ca-us")


(when false ;; for debugging
  (defn demo-itineraries
    []
    (-> test-data/otp-response-2
        otp-response->remove-trace
        otp-response->plan
        plan->merge-similar
        :itineraries))

  ;; reduce example 
  (reduce (fn [acc i] 
            (update-in acc 
                       [(even? i)] 
                       #((fnil conj []) % i))) 
          {} 
          [1 2 3 4])
  ;(map :walkDistance (demo-plan->merge-similar))
  ;(map count-legs (demo-plan->merge-similar))
  ;(map transit-routes (demo-itineraries))

  (for [a [[1] [2 3 4] [5 6 7 8 9]]] (let [b (count a)] b))

  (defn count-legs 
    [itin]
    (let [legs (:legs itin)]
      {:count-legs (count legs)}))

  (map (juxt :duration :minDuration :maxDuration :countWithThisRouteSequence)
       (summarize-collection (collect-by-route-sequence (demo-itineraries)))))


(defn f[x] (* x (+ x 1)))

(defn scratch-pad []
  ;; 
  (let [test-otp-url "http://anaheim-otp.ed-groth.com/otp/routers/default/plan?fromPlace=33.8046480634388,-117.915358543396&toPlace=33.77272636987434,-117.8671646118164&time=1:29pm&date=03-31-2015&mode=TRANSIT,WALK&maxWalkDistance=750&walkReluctance=40&walkSpeed=0.3&arriveBy=false&showIntermediateStops=false&_=1428612154915"]
    (http-client/get test-otp-url {:as :json}))

  (http-client/get "http://localhost:4000/" {:as :json})
  (http-client/get "http://localhost:4000/" {:as :json})
  )


