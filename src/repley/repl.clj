(ns repley.repl
  "REPL state and functions that modify it"
  (:require [clojure.datafy :as df]
            [ripley.live.source :as source]))

(def ^:dynamic *result-id*
  "Current result id, bound when calling visualizer to render."
  nil)

(defonce initial-repl-data {:id 0
                            :results []
                            :ns (the-ns 'user)
                            :tap-listener? false})

(defonce repl-data (atom initial-repl-data))

(defn- eval-result [ns code-str]
  (let [timestamp (java.util.Date.)
        start (System/currentTimeMillis)
        result (try (binding [*ns* ns]
                      (load-string code-str))
                    (catch Throwable t
                      t))
        duration (- (System/currentTimeMillis) start)]
    {:ns ns
     :code-str code-str
     :result result
     :timestamp timestamp
     :duration duration}))

(defn clear!
  "Clear all REPL evaluations."
  []
  (swap! repl-data assoc :results []))

(defn- add-result [{:keys [id] :as repl} result]
  (-> repl
      (update :id inc)
      (update :results conj
              (merge result
                     {:id id}))))

(defn add-result! [result]
  (swap! repl-data add-result
         (merge {:timestamp (java.util.Date.)}
                result)))

(defn eval-input! [input]
  (let [result (eval-result (:ns @repl-data) input)]
    (swap! repl-data add-result result)))

(defn- update-result! [id function]
  (swap! repl-data update :results
         (fn [results]
           (mapv (fn [{id* :id :as r}]
                   (if (= id* id)
                     (function r)
                     r)) results))))

(defn remove-result! [id]
  (swap! repl-data update :results
         (fn [results]
           (filterv #(not= (:id %) id) results))))



(defn nav-by!
  "Navigate down by giving a function to get the next object from the current on.
  Next fn must return a map containing `:label` and `:value` for the breadcrumb
  label and the next result respectively."
  [id next-fn]
  (update-result!
   id
   (fn [{:keys [result breadcrumbs] :as r}]
     (let [{:keys [value label]} (next-fn result)
           n (or (some-> breadcrumbs last :n inc) 1)]
       (assoc r
              :breadcrumbs (conj (or breadcrumbs
                                     [{:label :root :value result :n 0}])
                                 {:label label
                                  :value value
                                  :n n})
              :result value)))))

(defn nav!
  "Navigate down from current result id to a sub item denoted by k."
  [id k]
  (nav-by! id (fn [result]
                {:label (pr-str k)
                 :value (df/nav result k (get result k))})))

(defn nav-to-crumb!
  "Navigate given result to the nth breadcrumb."
  [id n]
  (update-result!
   id
   (fn [{:keys [breadcrumbs] :as r}]
     (let [next (get-in breadcrumbs [n :value])]
       (if (zero? n)
         (-> r
             (dissoc :breadcrumbs)
             (assoc :result next))
         (-> r
             (update :breadcrumbs subvec 0 (inc n))
             (assoc :result next)))))))

(defn retry! [id-to-retry]
  (update-result! id-to-retry
                  (fn [{:keys [ns code-str] :as old-result}]
                    (merge old-result
                           (eval-result ns code-str)))))

(defn current-repl-ns []
  (-> repl-data deref :ns))

(defn field-source [& path]
  (let [p (vec path)
        getv #(get-in % p)]
    (source/computed getv repl-data)))

(defn disable-tap-listener! []
  (swap! repl-data
         (fn [{tl :tap-listener :as r}]
           (when tl
             (remove-tap tl))
           (-> r
               (assoc :tap-listener? false)
               (dissoc :tap-listener)))))

(defn enable-tap-listener! []
  (when-not (:tap-listener? @repl-data)
    (let [f #(add-result! {:code-str (str  ";; tap> value received "
                                           (pr-str (java.util.Date.)))
                           :result %})]

      (swap! repl-data assoc
             :tap-listener? true
             :tap-listener f)
      (add-tap f)
      disable-tap-listener!)))


(defn toggle-tap-listener! []
  (if (:tap-listener? @repl-data)
    (disable-tap-listener!)
    (enable-tap-listener!)))
