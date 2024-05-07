(ns repley.visualizers
  "Default visualizer implementation."
  (:require [repley.ui.chart :as chart]
            [repley.ui.edn :as edn]
            [repley.ui.table :as table]
            [repley.protocols :as p]
            [ripley.live.source :as source]
            [ripley.html :as h]
            [repley.repl :as repl]
            [repley.visualizer.file :as file]
            [repley.visualizer.vega :as vega]))

(def sample-size
  "How many items to check when testing collection items."
  10)

(defn- map-columns-and-data [data]
  (let [id repl/*result-id*]
    (when (map? data)
      {:columns [{:label "Key" :accessor key} {:label "Value" :accessor val}]
       :data data
       :on-row-click #(repl/nav! id (key %))})))

(defn- seq-of-maps-columns-and-data [data]
  (let [id repl/*result-id*]
    (when (and (sequential? data)
               (every? map? (take 10 data)))
      {:columns (sort-by :label
                         (for [k (into #{}
                                       (mapcat keys)
                                       data)]
                           {:label (str k) :accessor #(get % k)}))
       :data (map-indexed (fn [i obj]
                            (with-meta obj {::index i})) data)
       :on-row-click #(repl/nav! id (::index (meta %)))})))

(defn- csv-columns-and-data [data]
  (when (and (seq? data)
             (every? vector? (take 10 data)))
    (let [columns (first data)
          data (rest data)]
      {:columns (map-indexed (fn [i label]
                               {:label label :accessor #(nth % i)}) columns)
       :data data})))

(def columns-and-data (some-fn map-columns-and-data
                               seq-of-maps-columns-and-data
                               csv-columns-and-data))

(def supported-data? (comp boolean columns-and-data))

(defn table-visualizer [_repley-opts {:keys [enabled? precedence]
                                      :or {precedence 0}}]
  (when enabled?
    (reify p/Visualizer
      (label [_] "Table")
      (supports? [_ data]
        (supported-data? data))
      (precedence [_] precedence)
      (render [_ data]
        (let [{:keys [columns data on-row-click]} (columns-and-data data)
              [data-source _] (source/use-state data)]
          (table/table
           {:columns columns
            :on-row-click on-row-click}
           data-source)))
      (ring-handler [_] nil)
      (assets [_] nil))))



(defn edn-visualizer [_ {:keys [enabled? precedence]
                         :or {precedence 0}}]
  (when enabled?
    (reify p/Visualizer
      (label [_] "Result")
      (supports? [_ _] true)
      (precedence [_] precedence)
      (render [_ data]
        (edn/edn data))
      (ring-handler [_] nil)
      (assets [_] nil))))

(defn- render-throwable [ex]
  (let [id repl/*result-id*
        type-label (.getName (type ex))
        msg (ex-message ex)
        data (ex-data ex)
        trace (.getStackTrace ex)
        cause (.getCause ex)
        cause-label (when cause
                      (str (.getName (type cause)) ": "
                           (.getMessage cause)))]
    (h/html
      [:div
       [:div [:b "Type: "] type-label]
       [:div [:b "Message:​ "] msg]
       [::h/when cause-label
        [:div [:b "Cause: "]
         [:a {:on-click #(repl/nav-by! id
                                       (constantly
                                        {:label (.getName (type cause))
                                         :value cause}))}
          cause-label]]]
       [::h/when (seq data)
        [:div [:b "Data​ "]
         (edn/edn data)]]
       [:div [:b "Stack trace​ "]
        [:details
         [:summary (h/out! (count trace) " stack trace lines")]
         [:ul
          [::h/for [st trace
                    :let [cls (.getClassName st)
                          method (.getMethodName st)
                          file (.getFileName st)
                          line (.getLineNumber st)]]
           [:li cls "." method " (" file ":" line ")"]]]]]])))

(defn throwable-visualizer [_ {:keys [enabled? precedence]
                               :or {precedence 100}}]
  (when enabled?
    (reify p/Visualizer
      (label [_] "Throwable")
      (supports? [_ ex] (instance? java.lang.Throwable ex))
      (precedence [_] precedence)
      (render [_ ex] (render-throwable ex))
      (ring-handler [_] nil)
      (assets [_] nil))))

(defn- chart-supports? [_opts x]
  (and (map? x)
       (every? number? (take sample-size (vals x)))))

(defn- chart-render [_opts data]
  (h/html
   [:div.chart
    (chart/bar-chart
     {:label-accessor (comp str key)
      :value-accessor val}
     (source/static data))]))

(defn chart-visualizer [_ {enabled? :enabled? :as opts}]
  (when enabled?
    (reify p/Visualizer
      (label [_] "Chart")
      (supports? [_ data] (chart-supports? opts data))
      (precedence [_] 0)
      (render [_ data] (chart-render opts data))
      (ring-handler [_] nil)
      (assets [_] nil))))

(defn default-visualizers
  [opts]
  (let [{v :visualizers :as opts} opts]
    (remove nil?
            [(edn-visualizer opts (:edn-visualizer v))
             (table-visualizer opts (:table-visualizer v))
             (file/file-visualizer opts (:file-visualizer v))
             (throwable-visualizer opts (:throwable-visualizer v))
             (chart-visualizer opts (:chart-visualizer v))
             (vega/vega-visualizer opts (:vega-visualizer v))])))

(defn default-visualizer
  "Utility to create a default visualizer for the given object."
  [label object render-fn]
  (reify p/DefaultVisualizer
    (default-visualizer [_]
      (reify p/Visualizer
        (label [_] label)
        (supports? [_ data] (= data object))
        (precedence [_] Long/MAX_VALUE)
        (render [_ _] (render-fn object))
        (ring-handler [_] nil)
        (assets [_] nil)))
    (object [_] object)))
