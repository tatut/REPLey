(ns repley.ui.edn
  "Pretty HTML rendering of arbitrary EDN."
  (:require [ripley.html :as h]))

(defmulti render (fn [_ctx item] (type item)))
(defmulti summary (fn [_ctx item] (type item)))

(defmethod render :default [_ item]
  (let [str (pr-str item)]
    (h/html [:span str])))

(defmethod render java.lang.String [_ctx str]
  (h/html [:span.text-lime-500 "\"" str "\""]))

(defmethod render java.lang.Number [_ctx num]
  (let [str (pr-str num)]
    (h/html [:span.text-red-300 str])))

(defmethod render clojure.lang.Keyword [_ctx kw]
  (let [str (pr-str kw)]
    (h/html [:span.text-emerald-700 str])))

(defn- collection [{render-item :render-item :as ctx
                    :or {render-item render}}
                   before after items]
  (let [cls (str "inline-flex space-x-2 flex-wrap "
                 (if (every? map? items)
                   "flex-col"
                   "flex-row"))]
    (h/html
     [:div.flex
      before
      [:div {:class cls}
       [::h/for [v items]
        [:div.inline-block (render-item (dissoc ctx :render-item) v)]]]
      after])))

(defmethod render clojure.lang.PersistentVector [ctx vec]
  (collection ctx "[" "]" vec))

(defmethod render clojure.lang.PersistentList [ctx vec]
  (collection ctx "(" ")" vec))

(defmethod render clojure.lang.LazySeq [ctx vec]
  (collection ctx "(" ")" vec))

(defmethod render clojure.lang.IPersistentMap [ctx m]
  (if (empty? m)
    (h/html [:div.inline-block "{}"])
    (let [entries (seq m)
          normal-entries (butlast entries)
          last-entry (last entries)]
      (h/html
       [:div.inline-block.flex
        "{"
        [:table
         [::h/for [[key val] normal-entries]
          [:tr.whitespace-pre
           [:td.align-top.py-0.pl-0.pr-2 (render ctx key)]
           [:td.align-top.p-0 (render ctx val)]]]
         [:tr.whitespace-pre
          [:td.align-top.py-0.pl-0.pr-2 (render ctx (key last-entry))]
          [:td.align-top.p-0 [:div.inline-block (render ctx (val last-entry))] "}"]]]]))))

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

(def ^:const string-cutoff-length 64)
(defmethod summary java.lang.String [ctx str]
  (if (> (count str) string-cutoff-length)
    (h/out! "String of length " (count str) " \" " (subs str 0 string-cutoff-length) "\"…")
    (h/out! "String \" " str "\"")))

(defmethod summary :default [ctx thing]
  (h/out! (str (type thing)) " instance"))


(defn edn
  ([thing] (edn {} thing))
  ([ctx thing]
   (h/html
    [:details
     [:summary (summary ctx thing)]
     (render ctx thing)])))
