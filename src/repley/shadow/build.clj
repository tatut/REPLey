(ns repley.shadow.build
  (:require [repley.main :as r]))

(defn start-and-listen
  "Starts the REPLey server and adds tap.

   May be added to `shadow-cljs.edn`:
   ```clojure
   {:builds
    {:build
     {:build-hooks [(repley.shadow.build/start-and-listen opts)]}}}
   ```
   `opts` are passed to `repley.main/start`.
   "
  {:shadow.build/stage :compile-prepare}
  ([build-state]
   (start-and-listen build-state nil))
  ([build-state options]
   (if (not= (:shadow.build/mode build-state) :dev)
     build-state
     (do (if options (r/start options) (r/start))
         (r/listen-to-tap>)
         (assoc-in build-state [:compiler-options :closure-defines `opts] options)))))
