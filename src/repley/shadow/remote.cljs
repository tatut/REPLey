(ns repley.shadow.remote
  (:require [repley.config :as config]))

(defn config->fetch-opts [{:keys [http] :as config}]
  (cond-> config
    (contains? http :port) (assoc :port (:port http))
    true (select-keys [:port :prefix :receive-endpoint])))

(comment
  (config->fetch-opts {:http {:port 1} :receive-endpoint nil :prefix "/foo"}))

(def default-fetch-opts
  (-> config/default-config
      config->fetch-opts
      (assoc :mode "cors")))

(defn make-send [fetch]
  (fn submit
    ([value] (submit nil value))
    ([opts value]
     (let [{:keys [port mode prefix receive-endpoint]} (merge default-fetch-opts opts)]
       (when (some? receive-endpoint) ;; nil disables the endpoint
         (fetch (str "http://localhost:" port prefix receive-endpoint)
                {:method  "POST"
                 :mode    mode
                 :headers {"content-type" "application/edn"}
                 :body    (binding [*print-meta* true]
                            (pr-str value))}))))))

(defn- fetch [url options]
  (js/fetch url (clj->js options)))

(def send (make-send fetch))


(comment
  (send "Hello World")
  (send {:port 3002 :mode "no-cors"} "hello world")
  (tap> {:runtime :cljs :value "hello web"})
  (add-tap send))
