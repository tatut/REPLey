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
            [repley.visualizers :as visualizers]
            [repley.config :as config]
            [repley.ui.icon :as icon]
            [clojure.datafy :as df]
            [repley.repl :as repl]
            [clojure.edn :as edn]))

(defn listen-to-tap>
  "Install Clojure tap> listener. All values sent via tap> are
  added as results to the REPL output.

  Returns a 0 argument function that will remote the tap listener
  when called."
  []
  (let [f #(repl/add-result! {:code-str (str  ";; tap> value received "
                                              (pr-str (java.util.Date.)))
                              :result %})]
    (add-tap f)
    #(remove-tap f)))

(defn- display
  "Prepare value for display."
  [value]
  (cond
    (var? value)
    (deref value)

    (instance? Throwable value)
    value

    ;; We don't want atoms to be datafied, we may want
    ;; to use them as Ripley sources (so UI autoupdates
    ;; if it changes)
    (instance? clojure.lang.IDeref value)
    value

    :else
    (df/datafy value)))

(defn select-visualizer [vs]
  (:v (reduce (fn [{:keys [v p] :as acc} vis]
                (let [prec (p/precedence vis)]
                  (if (or (nil? p) (> prec p))
                    {:v vis :p prec}
                    acc)))
              {} vs)))


(defn evaluation
  "Ripley component that renders one evaluated result"
  [{:keys [timestamp-format] :as _opts} visualizers {:keys [id code-str ns result breadcrumbs timestamp duration]}]
  (let [value (display result)
        supported-visualizers (filter #(p/supports? % value) visualizers)
        tabs (into [[:code "Code"]]
                   (for [v supported-visualizers]
                     [v (p/label v)]))
        [tab-source set-tab!] (source/use-state
                               (select-visualizer supported-visualizers))]
    (h/html
     [:div.evaluation
      [:div.actions.mr-2 {:style "float: right;"}
       [:button.btn.btn-outline.btn-xs.m-1 {:on-click #(repl/remove-result! id)}
        (icon/trashcan)]
       [:button.btn.btn-outline.btn-xs.m-1 {:on-click #(repl/retry! id)}
        (icon/reload)]]
      [::h/live tab-source
       (fn [tab]
         (h/html
          [:div
           [::h/when timestamp-format
            [:div.badge.badge-ghost.badge-xs
             (h/dyn! (.format (java.text.SimpleDateFormat. timestamp-format)
                              timestamp))
             (when duration (h/dyn! " (took " duration " ms)"))]]
           [::h/when (seq breadcrumbs)
            [:div.text-sm.breadcrumbs.ml-4
             [:ul
              [::h/for [{:keys [label value n]} breadcrumbs]
               [:li [:a {:on-click (format "_crumb(%d,%d)" id n)}
                     [::h/if (= label :root)
                      (icon/home)
                      label]]]]]]]
           [:div.tabs
            [::h/for [[tab-name tab-label] tabs
                      :let [cls (when (= tab-name tab) "tab-active")]]
             [:a.tab.tab-xs {:class cls
                             :on-click #(set-tab! tab-name)} tab-label]]]
           [:div.card.ml-4
            (if (= tab :code)
              (h/html
               [:div
                [:div.badge.badge-primary.badge-xs (h/out! (str ns))]
                [:pre [:code code-str]]])
              ;; Tab is the visualizer impl, call it
              (binding [repl/*result-id* id]
                (p/render tab value)))]]))]
      [:div.divider]])))

(defn- repl-page [opts visualizers prefix]
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
       (js/export-callbacks
        {:_eval repl/eval-input!
         :_crumb repl/nav-to-crumb!
         :_nav  (fn [id k]
                  (repl/nav! id (read-string k)))})
       [:script
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
        ;; (not changed ones)
        "let mo = new MutationObserver(ms => { ms.forEach(m => { "
        " let ids = new Set(); "
        " m.removedNodes.forEach(n => ids.add(n.getAttribute('data-rl'))); "
        " m.addedNodes.forEach(n => { if(!ids.has(n.getAttribute('data-rl'))) n.scrollIntoView(false) });})});"
        "mo.observe(document.querySelector('span.repl-output'), {childList: true});"
        " }"]
       (h/live-client-script (str prefix "/_ws"))]
      [:body {:on-load "initREPL()"}
       [:div "REPLey"]
       [:div.flex.flex-col
        [:div {:style "height: 80vh; overflow-y: auto;"}
         (collection/live-collection
          {:source (source/computed :results repl/repl-data)
           :render (partial evaluation opts visualizers)
           :key :id
           :container-element :span.repl-output})

         ;; Add filler element so we always have scroll
         ;; and navigating doesn't make results jump around.
         [:div {:style "height: 0vh;"}]]

        [:div.m-2.border {:style "height: 15vh;"}
         [:textarea#repl.w-full ""]]]]])))

(defn repley-handler
  "Return a Reply ring handler.

  See `#'repley.config/default-config` for a description of
  all the configuration options available."
  [config]
  (let [{:keys [prefix receive-endpoint edn-readers] :as opts} (config/config config)
        ws-handler (context/connection-handler (str prefix "/_ws"))
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
          (h/render-response #(repl-page opts visualizers prefix))

          receive-endpoint
          (when (= :post (:request-method req))
            (binding [*read-eval* false]
              (repl/add-result! {:timestamp (java.util.Date.)
                                 :code-str ";; received via HTTP"
                                 :result (edn/read-string {:readers edn-readers}
                                                          (slurp (:body req)))})
              {:status 204}))

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
