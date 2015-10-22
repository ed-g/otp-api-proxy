(ns otp-api-proxy.web-service
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
            [otp-api-proxy.test-data :as test-data]
            [clojure.walk :as walk]
            [clojure.java.javadoc])) 

(defresource help []
  :last-modified  #inst "2015-04-09"
  :available-media-types ["application/json" "text/plain"]
  :handle-ok (pretty-json
               { :help-message "Please use one of the following services"
                 :services [ 
                             { :name "Echo service" 
                               :url "/hello-world/echo" } 
                             { :name "OTP Server Proxy, Merging results by Route-sequence" 
                               :url "/trip-planner/anaheim-ca-us/plan" } 
                             { :name "OTP Server Proxy" 
                               :url "/trip-planner/anaheim-ca-us/plan" } 
                              { :name "OTP Server Proxy Test" 
                               :url "/trip-planner/anaheim-ca-us/pass-through" } 
                             { :name "OTP Params Test" 
                               :url "/otp-params" } 
                             { :name "OTP Example" 
                               :url "/otp-example" } 
                            ]}))

(defresource echo [echo]
  :last-modified  #inst "2015-04-09"
  :available-media-types ["application/json" "text/plain"]

  :handle-ok (pretty-json { :message "hello, world" :echo echo }))

(defresource otp-example []
  :available-media-types ["application/json" "text/plain"]
  :handle-ok (pretty-json 
               (-> (simple-otp-request-cached)
                   otp-response->plan)))

(defresource otp-cooked []
  :available-media-types ["application/json" "text/plain"]
  :handle-ok (pretty-json 
               (-> (simple-otp-request-cached)
               ;; (-> (simple-otp-request-live)
                   otp-response->plan
                   plan->add-route-url
                   plan->add-route-span)))

(defresource otp-merge-by-route-sequence [get-params]
  :available-media-types ["application/json" "text/plain"]
  :handle-ok (pretty-json 
               (-> (otp-request-live get-params)
                   otp-response->plan
                   plan->merge-similar
                   plan->add-route-url
                   plan->add-frequency
                   plan->add-route-span)))

(defresource otp-with-args [get-params]
  :available-media-types ["application/json" "text/plain"]
  :handle-ok (pretty-json 
               (-> (otp-request-live get-params)
                   otp-response->plan
                   plan->add-route-url
                   plan->add-frequency
                   plan->add-route-span)))

(defresource otp-pass-through [otp-instance route-params params request]
  :last-modified  #inst "2015-04-09"
  :available-media-types ["application/json" "text/plain"]
  :handle-ok 
    (pretty-json { :otp-instance otp-instance 
                   :route-params route-params
                   :params params 
                   :request (:uri request)} ))

(defresource otp-params [params]
  :last-modified  #inst "2015-04-09"
  :available-media-types ["application/json" "text/plain"]
  :handle-ok (pretty-json { :otp-instance (str (type params)) :params params} ))

(defresource decode-polyline-function []
  :last-modified  #inst "2015-04-09"
  :available-media-types ["application/javascript" "text/plain"]
  :handle-ok "
  // decodePolyLine from otp-leaflet-client/src/main/webapp/js/otp/util/Geo.js
  /* This program is free software: you can redistribute it and/or
     modify it under the terms of the GNU Lesser General Public License
     as published by the Free Software Foundation, either version 3 of
     the License, or (at your option) any later version.
     
     This program is distributed in the hope that it will be useful,
     but WITHOUT ANY WARRANTY; without even the implied warranty of
     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
     GNU General Public License for more details.
     
     You should have received a copy of the GNU General Public License
     along with this program.  If not, see <http://www.gnu.org/licenses/>. 
   */
	var decodePolyline = function(polyline) {
		
		  var currentPosition = 0;

		  var currentLat = 0;
		  var currentLng = 0;
	
		  var dataLength  = polyline.length;
		  
		  var polylineLatLngs = new Array();
		  
		  while (currentPosition < dataLength) {
			  
			  var shift = 0;
			  var result = 0;
			  
			  var byte;
			  
			  do {
				  byte = polyline.charCodeAt(currentPosition++) - 63;
				  result |= (byte & 0x1f) << shift;
				  shift += 5;
			  } while (byte >= 0x20);
			  
			  var deltaLat = ((result & 1) ? ~(result >> 1) : (result >> 1));
			  currentLat += deltaLat;
	
			  shift = 0;
			  result = 0;
			
			  do {
				  byte = polyline.charCodeAt(currentPosition++) - 63;
				  result |= (byte & 0x1f) << shift;
				  shift += 5;
			  } while (byte >= 0x20);
			  
			  var deltLng = ((result & 1) ? ~(result >> 1) : (result >> 1));
			  
			  currentLng += deltLng;
	
			  polylineLatLngs.push(new L.LatLng(currentLat * 0.00001, currentLng * 0.00001));
		  }	
		  
		  return polylineLatLngs;
	};
  ")




