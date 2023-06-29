(ns repley.main
  "Main start namespace for REPLey web."
  (:require [ripley.html :as h]
            [ripley.live.context :as context]
            [org.httpkit.server :as server]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [repley.protocols :as p]
            [ripley.live.collection :as collection]
            [ripley.live.source :as source]
            [ripley.js :as js]
            [repley.ui.edn :as edn]
            [repley.visualizers :as visualizers]
            [repley.ui.icon :as icon]))


(defonce repl-data (atom {:id 0 :results []}))

(defn- eval-result [id code-str]
  (try
    {:id id
     :code-str code-str
     :result (binding [*ns* (the-ns 'user)]
               (load-string code-str))}
    (catch Throwable t
      {:id id
       :code-str code-str
       :error t})))

(defn eval! [{:keys [id results]} code-str]
  {:id (inc id)
   :results (conj results
                  (eval-result id code-str))})

(defn- eval-input! [input]
  (swap! repl-data eval! input))

(defn- remove-result! [id]
  (swap! repl-data update :results
         (fn [results]
           (filterv #(not= (:id %) id) results))))

(defn- retry! [id-to-retry]
  (swap! repl-data update :results
         (fn [results]
           (mapv (fn [{:keys [id code-str] :as result}]
                   (if (= id id-to-retry)
                     (eval-result id code-str)
                     result)) results))))

(defn listen-to-tap>
  "Install Clojure tap> listener. All values sent via tap> are
  added as results to the REPL output.

  Returns a 0 argument function that will remote the tap listener
  when called."
  []
  (let [f #(swap! repl-data (fn [{:keys [id results]}]
                              {:id (inc id)
                               :results (conj results
                                              {:id id
                                               :code-str ";; tap> value"
                                               :result %})}))]
    (add-tap f)
    #(remove-tap f)))



(defn evaluation
  "Ripley component that renders one evaluated result"
  [visualizers {:keys [id code-str result error]}]
  (let [[tab-source set-tab!] (source/use-state :edn)
        value (or result error)

        ;; Deref vars directly
        value (if (var? value)
                (deref value)
                value)
        tabs (into [[:code "Code"]
                    [:edn "Result"]]
                   (for [v visualizers
                         :when (p/supports? v value)
                         :let [label (p/label v)]]
                     [v label]))]
    (h/html
     [:div.evaluation
      [:div.actions.mr-2 {:style "float: right;"}
       [:button.btn.btn-outline.btn-xs.m-1 {:on-click #(remove-result! id)}
        (icon/trashcan-icon)]
       [:button.btn.btn-outline.btn-xs.m-1 {:on-click #(retry! id)}
        (icon/reload-icon)]]
      [::h/live tab-source
       (fn [tab]
         (h/html
          [:div
           [:div.tabs
            [::h/for [[tab-name tab-label] tabs
                      :let [cls (when (= tab-name tab) "tab-active")]]
             [:a.tab.tab-xs {:class cls
                             :on-click #(set-tab! tab-name)} tab-label]]]
           [:div.card.ml-4
            (case tab
              :code (h/html [:pre [:code code-str]])
              :edn (edn/edn value)
              ;; Tab is the visualizer impl, call it
              (p/render tab value))]]))]
      [:div.divider]])))

(defn- repl-page [visualizers prefix]
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
        "editor.focus();"

        ;; Add mutation observer to scroll new evaluations into view
        "let mo = new MutationObserver(ms => ms.forEach(m => m.addedNodes.forEach(n => n.scrollIntoView())));"
        "mo.observe(document.querySelector('span.repl-output'), {childList: true});"
        " }"]
       (h/live-client-script (str prefix "/_ws"))]
      [:body {:on-load "initREPL()"}
       [:div "REPLey"]
       [:div.flex.flex-col
        [:div {:style "height: 80vh; overflow-y: auto;"}
         (collection/live-collection
          {:source (source/computed :results repl-data)
           :render (partial evaluation visualizers)
           :key :id
           :container-element :span.repl-output})]

        [:div.m-2.border {:style "height: 15vh;"}
         [:textarea#repl.w-full ""]]]]])))

(defn repley-handler
  "Return a Reply ring handler.
  The following options are supported:

  - :prefix        prefix to use for all routes
  - :visualizers   sequence of visualization implementations
                   (see repley.protocols/Visualizer).
                   These visualizers are added to the defaults.
  "
  [{:keys [prefix] :as opts
    :or {prefix ""}}]
  (let [ws-handler (context/connection-handler (str prefix "/_ws"))
        c (count prefix)
        ->path (fn [uri] (subs uri c))
        visualizers (visualizers/default-visualizers opts)
        visualizer-handlers (apply some-fn (for [v visualizers
                                                 :let [handler (p/ring-handler v)]
                                                 :when handler] handler))]
    (fn [{uri :uri :as req}]
      (when (str/starts-with? uri prefix)
        (condp = (->path uri)
          "/_ws" (ws-handler req)

          "/repley.css"
          {:status 200 :headers {"Content-Type" "text/css"}
           :body (slurp (io/resource "public/repley.css"))}

          "/"
          (h/render-response #(repl-page visualizers prefix))

          ;; Try visualizer handlers
          (visualizer-handlers req))))))

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
