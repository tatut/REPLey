(ns repley.config
  "REPLey configuration data.
  Contains the full example and default configuration.")

(def default-config
  {;; Options to give http-kit
   :http {:port 3001}

   ;; URI prefix for ring handler, use this to serve REPLey from under
   ;; a different root path.
   :prefix ""

   ;; Receive endpoint, use nil to disable
   ;; The receive endpoint can take POSTed EDN values and add them
   ;; as results. This makes it possible to integrate REPLey to
   ;; ClojureScript by sending tap> values over HTTP.
   :receive-endpoint "/receive"

   ;; Timestamp format, if non-nil, a timestamp is shown for each result.
   :timestamp-format "yyyy-MM-dd HH:mm:ss.SSS"

   ;; Configuration for visualizers
   :visualizers
   {:edn-visualizer {:enabled? true}
    :table-visualizer {:enabled? true}
    :file-visualizer {:enabled? true
                      :allow-download? true}
    :throwable-visualizer {:enabled? true}
    :chart-visualizer {:enabled? true}
    :vega-visualizer {:enabled? true}}})

(defn config
  "Get the full configuration. Deeply merges given
  opts to the default configuration."
  [opts]
  (merge-with (fn merge-strategy [a b]
                (cond
                  (and (map? a) (map? b))
                  (merge-with merge-strategy a b)

                  (some? b)
                  b

                  :else
                  a)) default-config opts))
