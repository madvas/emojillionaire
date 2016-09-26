 (ns user
   (:require [figwheel-sidecar.repl-api]
             [emojillionaire.core]
             [ring.middleware.reload :refer [wrap-reload]]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(def http-handler
  (wrap-reload #'emojillionaire.core/http-handler))

(comment
  (cemerick.piggieback/load-file)
  (cljs.closure/source-for-namespace)
  (figwheel-sidecar.repl-api/start-figwheel! (figwheel-sidecar.config/fetch-config))
  (figwheel-sidecar.repl-api/figwheel-running?)
  (figwheel-sidecar.repl-api/cljs-repl)
  (figwheel-sidecar.repl-api/stop-figwheel!))