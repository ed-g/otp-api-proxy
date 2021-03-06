(defproject otp-api-proxy "0.1.0"
  :description "OpenTripPlanner REST API proxy, which adds more useful information"
  :url "https://github.com/ed-g/otp-api-proxy"
  :ring {:handler otp-api-proxy.core/handler
         :port 4001}
  :repl-options {:init-ns otp-api-proxy.core}
  :plugins [[lein-ancient "0.6.6"] 
            [lein-ring "0.9.3"]
            [lein-kibit "0.0.8"]
            [jonase/eastwood "0.2.1"] ;; clojure lint tool.
            [cider/cider-nrepl "0.8.2"]] ;; for lein repl.
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  ;; graphical namespace viewer.
  :profiles {:dev {:dependencies [[clj-ns-browser "1.3.1"]
                                  ]}}

  :dependencies [ 
                 [org.clojure/clojure "1.8.0"] 
                 [cheshire "5.4.0"] ;; fast JSON
                 [cider/cider-nrepl "0.8.2"] ; for builtin repl.
                 [clj-time "0.9.0"] ;; joda time wrapper
                 [compojure "1.3.3"]
                 [liberator "0.12.2"] ;; simple REST, similar to webmachine
                 [net.drib/mrhyde "0.5.3"] ;; clojure <-> js interop library
                 [environ "1.1.0"];; for environment variables
                 [org.clojure/clojure "1.6.0"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"] 
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/tools.nrepl "0.2.10"]
                 [ring/ring-core "1.3.2"]
                 [clj-http "1.1.0"] ; for client to OpenTripPlanner
                 ])
