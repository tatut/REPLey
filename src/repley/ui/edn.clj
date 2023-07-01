(ns repley.ui.edn
  "Pretty HTML rendering of arbitrary EDN."
  (:require [ripley.html :as h]
            [ripley.live.source :as source]))

;; Keep track of how much visible (non-markup) output has been
;; written so far... when we reach max-output, stop rendering
;; more and show links to expand
(def ^{:private true :dynamic true} *truncate* nil)

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

(defn- collection [{render-item :render-item :as ctx
                    :or {render-item render}}
                   before after items]
  (let [cls (str "inline-flex space-x-2 flex-wrap "
                 (if (every? map? items)
                   "flex-col"
                   "flex-row"))]
    (h/html
     [:div.flex
      (visible before)
      [:div {:class cls}
       [::h/for [v items
                 :when (not (truncated?))]
        [:div.inline-block (render-item (dissoc ctx :render-item) v)]]]
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
          last-entry (last entries)]
      (h/html
       [:div.inline-block.flex
        (visible "{")
        [:table
         [::h/for [[key val] normal-entries
                   :when (not (truncated?))]
          [:tr.whitespace-pre
           [:td.align-top.py-0.pl-0.pr-2 (render ctx key)]
           [:td.align-top.p-0 (render ctx val)]]]
         [:tr.whitespace-pre
          [:td.align-top.py-0.pl-0.pr-2 (render ctx (key last-entry))]
          [:td.align-top.p-0 [:div.inline-block (render ctx (val last-entry))]
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
       [:div.text-red-500.inline ex-msg]))))

(def ^:const string-cutoff-length 64)
(defmethod summary java.lang.String [ctx str]
  (if (> (count str) string-cutoff-length)
    (h/out! "String of length " (count str) " \" " (subs str 0 string-cutoff-length) "\"…")
    (h/out! "String \" " str "\"")))

(defmethod summary :default [ctx thing]
  (h/out! (str (type thing)) " instance"))

(defmethod summarize? java.lang.String [s] (> (count s) string-cutoff-length))
(defmethod summarize? java.lang.Number [_] false)
(defmethod summarize? clojure.lang.Keyword [_] false)
(defmethod summarize? :default [thing] true)

(def ^:private initial-max-output 1024)
(defn edn
  ([thing] (edn {} thing))
  ([ctx thing]
   (let [[max-output-source set-max-output!] (source/use-state initial-max-output)]
     (h/html
      [:div
       [::h/if (nil? thing)
        [:span.nil "nil"]
        [::h/live max-output-source
         (fn [max-output]
           (binding [*truncate* (truncate-state max-output)]
             (h/html
              [:div
               [::h/if (not (summarize? thing))
                (render ctx thing)
                [:details {:open true}
                 [:summary (summary ctx thing)]
                 (render ctx thing)]]
               [::h/when (truncated?)
                [:div.flex
                 [:div.inline.mx-2.text-accent "output truncated"]
                 [:button.btn.btn-accent.btn-xs.mx-2 {:on-click #(set-max-output! (* 2 max-output))}
                  "more"]
                 [:button.btn.btn-accent.btn-xs.mx-2 {:on-click #(set-max-output! 0)}
                  "full"]]]])))]]]))))
