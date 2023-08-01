(ns repley.ui.edn
  "Pretty HTML rendering of arbitrary EDN."
  (:require [ripley.html :as h]
            [ripley.live.source :as source]
            [repley.ui.edn :as edn]
            [repley.repl :as repl]
            [clojure.string :as str]))

;; Keep track of how much visible (non-markup) output has been
;; written so far... when we reach max-output, stop rendering
;; more and show links to expand
(def ^{:private true :dynamic true} *truncate* nil)

;; When *top* is true, set on-click handlers that navigate
;; deeper into collections
(def ^{:private true :dynamic true} *top* false)

(defn- truncate-state [max-output]
  (atom {:output 0
         :max-output max-output

         ;; If true, don't output anything more
         ;; except the link to render more
         :truncated? false}))

(defn- truncated? []
  (let [{:keys [truncated? max-output]} @*truncate*]
    (if (zero? max-output)
      ;; Render all, if max output is zero
      false
      truncated?)))

(defn- visible [& things]
  (when-not (truncated?)
    (let [string (apply str things)
          len (count string)
          {:keys [output max-output]} @*truncate*]
      (if (and (not= 0 max-output) (> (+ len output) max-output))
        (do
          (h/dyn! (subs string 0 (- max-output output)))
          (swap! *truncate* assoc
                 :output max-output
                 :truncated? true))
        (do
          (h/dyn! string)
          (swap! *truncate* update :output + len))))))


(defmulti render (fn [_ctx item] (type item)))

(defn- render-top [ctx item]
  (binding [*top* true] (render ctx item)))

(defn- render-nested [ctx item]
  (binding [*top* false] (render ctx item)))

(defmulti summary (fn [_ctx item] (type item)))
(defmulti summarize? (fn [item] (type item)))

(defmethod render :default [_ item]
  (h/html [:span (visible (pr-str item))]))

(defmethod render java.lang.String [_ctx str]
  (h/html [:span.text-lime-500 (visible "\"" str "\"")]))

(defmethod render java.lang.Number [_ctx num]
  (h/html [:span.text-red-300 (visible (pr-str num))]))

(defmethod render clojure.lang.Keyword [_ctx kw]
  (h/html [:span.text-emerald-700 (visible (pr-str kw))]))

(defn- nav [key]
  (when *top*
    (str "_nav(" repl/*result-id* ",'"
         (-> key pr-str
             (str/replace "\\" "\\\\")
             (str/replace "'" "\\'"))
         "')")))

(defn- collection [{render-item :render-item :as ctx
                    :or {render-item render-nested}}
                   before after items]
  (let [cls (str "inline-flex space-x-2 flex-wrap "
                 (if (every? map? items)
                   "flex-col"
                   "flex-row"))]
    (h/html
     [:div.flex
      (visible before)
      [:div {:class cls}
       [::h/for [[i v] (map-indexed vector items)
                 :when (not (truncated?))
                 :let [cls (when *top* "hover:bg-primary")]]
        [:div.inline-block {:class cls :on-click (nav i)}
         (render-item (dissoc ctx :render-item) v)]]]
      (visible after)])))

(defmethod render clojure.lang.PersistentVector [ctx vec]
  (collection ctx "[" "]" vec))

(defmethod render clojure.lang.PersistentList [ctx vec]
  (collection ctx "(" ")" vec))

(defmethod render clojure.lang.LazySeq [ctx vec]
  (collection ctx "(" ")" vec))

(defmethod render clojure.lang.IPersistentMap [ctx m]
  (if (empty? m)
    (h/html [:div.inline-block (visible "{}")])
    (let [entries (seq m)
          normal-entries (butlast entries)
          last-entry (last entries)
          hover (when *top*
                  "hover:bg-primary")]
      (h/html
       [:div.inline-block.flex
        (visible "{")
        [:table
         [::h/for [[key val] normal-entries
                   :when (not (truncated?))]
          [:tr.whitespace-pre {:class hover :on-click (nav key)}
           [:td.align-top.py-0.pl-0.pr-2
            (render-nested ctx key)]
           [:td.align-top.p-0
            (render-nested ctx val)]]]
         [:tr.whitespace-pre {:class hover :on-click (nav (key last-entry))}
          [:td.align-top.py-0.pl-0.pr-2 (render-nested ctx (key last-entry))]
          [:td.align-top.p-0 [:div.inline-block (render-nested ctx (val last-entry))]
           (visible "}")]]]]))))

(defmethod render clojure.lang.Var [ctx v]
  (render ctx (deref v)))

(defmethod summary clojure.lang.LazySeq [ctx thing]
  (h/out! "Lazy sequence with " (count thing) " elements"))

(defmethod summary clojure.lang.PersistentList [ctx thing]
  (h/out! "List with " (count thing) " elements"))

(defmethod summary clojure.lang.PersistentVector [ctx thing]
  (h/out! "Vector with " (count thing) " elements"))

(defmethod summary clojure.lang.IPersistentMap [ctx m]
  (h/out! "Map with " (count m) " entries"))

(defmethod summary clojure.lang.Var [ctx v]
  (h/out! "Var " (str v)))

(defmethod summary java.lang.Throwable [ctx ex]
  (if (= clojure.lang.Compiler$CompilerException (type ex))
    ;; Special handling for compiler exceptions
    (let [msg (.getMessage (.getCause ex))
          {:clojure.error/keys [phase line column]} (ex-data ex)
          phase (name phase)]
      (h/html
       [:div.text-red-500.inline msg [:span.font-xs " (" phase " at line " line ", column " column ")"]]))
    (let [ex-type (.getName (type ex))
          ex-msg (.getMessage ex)]
      (h/html
       [:div.text-red-500.inline ex-type ": " ex-msg]))))

(defmethod summary :default [ctx thing]
  (h/out! (str (type thing)) " instance"))

(defmethod summarize? java.lang.String [s] false)
(defmethod summarize? java.lang.Number [_] false)
(defmethod summarize? clojure.lang.Keyword [_] false)
(defmethod summarize? :default [thing] true)

(def ^:private initial-max-output 1024)

(defn- expand-buttons [max-output set-max-output!]
  (h/html
   [::h/when (truncated?)
    [:div.flex
     [:div.inline.mx-2.text-accent "output truncated"]
     [:button.btn.btn-accent.btn-xs.mx-2 {:on-click #(set-max-output! (* 2 max-output))}
      "more"]
     [:button.btn.btn-accent.btn-xs.mx-2 {:on-click #(set-max-output! 0)}
      "full"]]]))

(defn edn
  ([thing] (edn {} thing))
  ([ctx thing]
   (let [[max-output-source set-max-output!] (source/use-state initial-max-output)
         id repl/*result-id*]
     (h/html
      [:div.edn
       [::h/if (nil? thing)
        [:span.nil "nil"]
        [::h/live max-output-source
         (fn [max-output]
           (binding [*truncate* (truncate-state max-output)
                     repl/*result-id* id]
             (let [expand (partial expand-buttons max-output set-max-output!)]
               (h/html
                [:div
                 [::h/if (not (summarize? thing))
                  [:div.my-2
                   (render-top ctx thing)
                   (expand)]
                  [:details {:open true}
                   [:summary (summary ctx thing)]
                   (render-top ctx thing)
                   (expand)]]]))))]]]))))
