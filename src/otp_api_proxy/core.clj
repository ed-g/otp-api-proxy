(ns otp-api-proxy.core
  (:require [clojure.edn :as edn]
            [clojure.tools.nrepl.server :as nrepl-server]
            [cider.nrepl :refer (cider-nrepl-handler)] 
            [liberator.core :refer [resource defresource]]
            [ring.middleware.params :refer [wrap-params]]
            [compojure.core :refer [defroutes ANY]]
            [clojure.data.json :as json]
            [cheshire.core :as cheshire]
            [cheshire.generate :as cheshire-generate]
  
            ))

(defonce hack-hack-hack
  (nrepl-server/start-server :port 4100 :host "127.0.0.1"
                             :handler cider-nrepl-handler))

(defn add-header
  [a-handler header value]
  (fn [req]
    (assoc-in (a-handler req) [:headers header] value)))

(defresource ws-echo [echo]
  :last-modified  #inst "2015-04-09"
  :available-media-types ["application/json" "text/plain"]

  :handle-ok ( cheshire/encode { :message "hello, world" :echo echo }))


(defroutes app
  (ANY "/hello-world/:echo" [echo]
       (ws-echo echo)))

(def handler
  (add-header (-> app
                wrap-params)
              "Access-Control-Allow-Origin" "*" ;; javascript cross domain scripting
              ))


(defn foo
  "I don't do a whole lot."
 [x]
  (println x "Hello, World!"))

(def bar "docs for bar"
  "foo blah blah
  akjadf ")


(+ 8 
   (/ 2 3))

bar

