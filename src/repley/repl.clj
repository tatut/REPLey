(ns repley.repl
  "REPL state and functions that modify it"
  (:require [clojure.datafy :as df]))

(def ^:dynamic *result-id*
  "Current result id, bound when calling visualizer to render."
  nil)

(defonce initial-repl-data {:id 0
                            :results []
                            :ns (the-ns 'user)})
(defonce repl-data (atom initial-repl-data))

(defn- eval-result [ns code-str]
  {:ns ns
   :code-str code-str
   :result (try (binding [*ns* ns]
                  (load-string code-str))
                (catch Throwable t
                  t))})

(defn clear!
  "Clear all REPL evaluations."
  []
  (reset! repl-data initial-repl-data))

(defn- add-result [{:keys [id] :as repl} result]
  (-> repl
      (update :id inc)
      (update :results conj
              (merge result
                     {:id id}))))

(defn add-result! [result]
  (swap! repl-data add-result result))

(defn- eval-input [{:keys [ns] :as repl} code-str]
  (add-result repl (eval-result ns code-str)))

(defn eval-input! [input]
  (swap! repl-data eval-input input))

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

(defn nav!
  "Navigate down from current result id to a sub item denoted by k."
  [id k]
  (update-result!
   id
   (fn [{:keys [result breadcrumbs] :as r}]
     (let [next (df/nav result k (get result k))
           n (or (some-> breadcrumbs last :n inc) 1)]
       (assoc r
              :breadcrumbs (conj (or breadcrumbs
                                     [{:label :root :value result :n 0}])
                                 {:label (pr-str k)
                                  :value next
                                  :n n})
              :result next)))))

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
