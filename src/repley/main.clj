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
            [repley.ui.edn :as edn]
            [repley.visualizers :as visualizers]))


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

(defn icon [path]
  (h/html
   [:svg {:width 15 :height 15 :viewBox "0 0 15 15" :fill "none"}
    [:path {:d path
            :fill "currentColor"
            :fill-rule "evenodd"
            :clip-rule "evenodd"}]]))

(def reload-icon (partial icon "M1.84998 7.49998C1.84998 4.66458 4.05979 1.84998 7.49998 1.84998C10.2783 1.84998 11.6515 3.9064 12.2367 5H10.5C10.2239 5 10 5.22386 10 5.5C10 5.77614 10.2239 6 10.5 6H13.5C13.7761 6 14 5.77614 14 5.5V2.5C14 2.22386 13.7761 2 13.5 2C13.2239 2 13 2.22386 13 2.5V4.31318C12.2955 3.07126 10.6659 0.849976 7.49998 0.849976C3.43716 0.849976 0.849976 4.18537 0.849976 7.49998C0.849976 10.8146 3.43716 14.15 7.49998 14.15C9.44382 14.15 11.0622 13.3808 12.2145 12.2084C12.8315 11.5806 13.3133 10.839 13.6418 10.0407C13.7469 9.78536 13.6251 9.49315 13.3698 9.38806C13.1144 9.28296 12.8222 9.40478 12.7171 9.66014C12.4363 10.3425 12.0251 10.9745 11.5013 11.5074C10.5295 12.4963 9.16504 13.15 7.49998 13.15C4.05979 13.15 1.84998 10.3354 1.84998 7.49998Z"))

(def trashcan-icon (partial icon "M5.5 1C5.22386 1 5 1.22386 5 1.5C5 1.77614 5.22386 2 5.5 2H9.5C9.77614 2 10 1.77614 10 1.5C10 1.22386 9.77614 1 9.5 1H5.5ZM3 3.5C3 3.22386 3.22386 3 3.5 3H5H10H11.5C11.7761 3 12 3.22386 12 3.5C12 3.77614 11.7761 4 11.5 4H11V12C11 12.5523 10.5523 13 10 13H5C4.44772 13 4 12.5523 4 12V4L3.5 4C3.22386 4 3 3.77614 3 3.5ZM5 4H10V12H5V4Z"))

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
       [:button.btn.btn-outline.btn-xs.m-1 {:on-click #(remove-result! id)} (trashcan-icon)]
       [:button.btn.btn-outline.btn-xs.m-1 {:on-click #(retry! id)} (reload-icon)]]
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

  - :prefix       prefix to use for all routes
  - :visualizers  sequence of visualization implementations
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
