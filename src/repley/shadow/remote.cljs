(ns repley.shadow.remote)

(defn make-send [fetch]
  (fn submit
    ([value] (submit nil value))
    ([{:keys [port mode]
       :or   {port 3001 ;; TODO: use repley.config/default-config
              mode "cors"}}
      value]
     (fetch (str "http://localhost:" port "/receive")
            {:method  "POST"
             :mode    mode
             :headers {"content-type" "application/edn"}
             :body    (binding [*print-meta* true]
                        (pr-str value))}))))

(defn- fetch [url options]
  (js/fetch url (clj->js options)))

(def send (make-send fetch))


(comment
  (send "Hello World")
  (send {:port 3002 :mode "no-cors"} "hello world")
  (tap> {:runtime :cljs :value "hello web"})
  (add-tap send))
