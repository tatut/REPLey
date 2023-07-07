(ns repley.shadow.preload
  "May be added to `shadow-cljs.edn` `:preloads` to add tap on start."
  (:require
   [repley.shadow.remote :as r]
   goog.object))

(def build-opts
  (-> (goog.object/get js/CLOSURE_DEFINES "repley.shadow.build.opts")
      (js->clj :keywordize-keys true)
      r/config->fetch-opts))

(add-tap (partial r/send (merge build-opts {:mode "no-cors"})))
