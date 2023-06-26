(ns repley.main
  "Main start namespace for REPLey web."
  (:require [ripley.html :as h]
            [ripley.live.context :as context]
            [org.httpkit.server :as server]
            [clojure.tools.logging :as log]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [repley.protocols :as p]
            [ripley.live.collection :as collection]
            [ripley.live.source :as source]
            [ripley.js :as js]
            [repley.ui.edn :as edn]))


(defonce repl-data (atom {:id 0 :results []}))

(defn eval! [{:keys [id results]} code-str]
  (println "EVAL: " code-str)
  (let [code (read-string code-str)]
    (try
      (let [result (binding [*ns* (the-ns 'user)]
                     (eval code))]
      ;; handle errors and such, even with
        {:id (inc id)
         :results (conj results
                        {:id id
                         :code-str code-str
                         :code code
                         :result result})})
      (catch Throwable t
        (println "EXCEPTION: " t)
        {:id (inc id)
         :results (conj results
                        {:id id
                         :code-str code-str
                         :code code
                         :error t})}))))


(defn- eval-input! [input]
  (swap! repl-data eval! input))


(defn evaluation
  "Ripley component that renders one evaluated result"
  [{:keys [code-str result]}]
  (let [[tab-source set-tab!] (source/use-state :edn)
        tabs [[:code "Code"] [:edn "Result"]]]
    (h/html
     [:div.evaluation
      [::h/live tab-source
       (fn [tab]
         (h/html
          [:div
           [:div.tabs
            [::h/for [[tab-name tab-label] tabs
                      :let [cls (when (= tab-name tab) "tab-active")]]
             [:a.tab.tab-xs {:class cls
                             :on-click #(set-tab! tab-name)} tab-label]]]
           [:div.card
            (case tab
              :code (h/html [:pre [:code code-str]])
              :edn (edn/edn result))]]))]
      [:div.divider]])))

(defn- repl-page [prefix]
  (h/out! "<!DOCTYPE html>\n")
  (let [css (str prefix "/repley.css")]
    (h/html
     [:html {:data-theme "garden"}
      [:head
       [:meta {:charset "UTF-8"}]
       [:link {:rel "stylesheet" :href css}]
       [:script {:src "https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.63.1/codemirror.min.js"}]
       [:script {:src "https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.63.1/mode/clojure/clojure.min.js"}]
       [:link {:rel :stylesheet :href "https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.63.1/codemirror.min.css"}]
       [:script
        "function _eval(txt) {"
        (h/out! (h/register-callback (js/js eval-input! "txt")))
        "}"
        "function initREPL() {"
        "let editor = CodeMirror.fromTextArea(document.getElementById('repl'), {"
        "lineNumbers: true,"
        "autoCloseBrackets: true,"
        "matchBrackets: true,"
        "mode: 'text/x-clojure',"
        "extraKeys: {'Cmd-Enter': e => _eval(e.doc.getValue())}"
        "});"
        "editor.setSize('100%', '100%');"
        "window._repley_editor = editor;"
        " }"]
       (h/live-client-script (str prefix "/_ws"))]
      [:body {:on-load "initREPL()"}
       [:div "REPLey"]
       [:div.flex.flex-col
        [:div {:style "height: 80vh; overflow-y: auto;"}
         (collection/live-collection
          {:source (source/computed :results repl-data)
           :render evaluation
           :key :id})]

        [:div.m-2 {:style "height: 15vh;"}
         [:textarea#repl.w-full ""]]]]])))

(defn repley-handler
  "Return a Reply ring handler.
  The following options are supported:

  - :prefix       prefix to use for all routes
  - :visualizers  sequence of visualization implementations
                  (see repley.protocols/Visualizer).
                  These visualizers are added to the defaults.
  - :default-visualizers?
                  If false, no default visualizers are added
  "
  [{:keys [prefix] :as opts
    :or {prefix ""}}]
  (let [ws-handler (context/connection-handler (str prefix "/_ws"))
        c (count prefix)
        ->path (fn [uri] (subs uri c))]
    (fn [{uri :uri :as req}]
      (when (str/starts-with? uri prefix)
        (try
          (condp = (->path uri)
            "/_ws" (ws-handler req)

            "/repley.css"
            {:status 200 :headers {"Content-Type" "text/css"}
             :body (slurp (io/resource "public/repley.css"))}

            "/"
            (h/render-response #(repl-page prefix)))
          (catch Throwable t
            (println "T: " t)))))))

(def ^{:private true :doc "The currently running server"} server nil)

(defn start
  "Start REPLey HTTP server.
  Any options under `:http` will be passed to http-kit.
  See [[repley-handler]] for other options."
  ([] (start {:http {:port 3001}}))
  ([opts]
   (alter-var-root
    #'server
    (fn [old-server]
      (when old-server
        (old-server))
      (server/run-server (repley-handler opts) (:http opts))))))
