(ns repley.shadow.preload
  "May be added to `shadow-cljs.edn` `:preloads` to add tap on start."
  (:require
   [repley.shadow.remote :as r]
   goog.object))

(let [port (-> (goog.object/get js/CLOSURE_DEFINES "repley.shadow.build.opts")
               (js->clj :keywordize-keys true)
               (get-in [:http :port]))
      opts (cond-> {:mode "no-cors"}
             port (assoc :port port))]
  (add-tap (partial r/send opts)))
