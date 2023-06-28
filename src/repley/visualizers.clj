(ns repley.visualizers
  "Default visualizer implementation."
  (:require [repley.ui.chart :as chart]
            [repley.ui.edn :as edn]
            [repley.ui.table :as table]
            [repley.protocols :as p]
            [ripley.live.source :as source]
            [ripley.html :as h]
            [ring.util.io :as ring-io]
            [clojure.java.io :as io]))

(def sample-size
  "How many items to check when testing collection items."
  10)

(defn- map-columns-and-data [data]
  (when (map? data)
    [[{:label "Key" :accessor key} {:label "Value" :accessor val}]
     data]))

(defn- seq-of-maps-columns-and-data [data]
  (when (and (sequential? data)
             (every? map? (take 10 data)))
    [(sort-by :label
              (for [k (into #{}
                            (mapcat keys)
                            data)]
                {:label (str k) :accessor #(get % k)}))
     data]))

(defn- csv-columns-and-data [data]
  (when (and (seq? data)
             (every? vector? (take 10 data)))
    (let [columns (first data)
          data (rest data)]
      [(map-indexed (fn [i label]
                      {:label label :accessor #(nth % i)}) columns)
       data])))

(def columns-and-data (some-fn map-columns-and-data
                               seq-of-maps-columns-and-data
                               csv-columns-and-data))

(def supported-data? (comp boolean columns-and-data))

(defn table-visualizer [_repley-opts {:keys [enabled?]}]
  (when enabled?
    (reify p/Visualizer
      (label [_] "Table")
      (supports? [_ data]
        (supported-data? data))
      (render [_ data]
        (let [[columns data] (columns-and-data data)
              [data-source _] (source/use-state data)]
          (table/table
           {:columns columns}
           data-source)))
      (ring-handler [_] nil))))

(defn file-visualizer [{prefix :prefix :as _repley-opts} {:keys [enabled? allow-download?]}]
  (let [downloads (atom {})
        download! #(swap! downloads assoc % (str (random-uuid)))]
    (when enabled?
      (reify p/Visualizer
        (label [_] "File")
        (supports? [_ data]
          (instance? java.io.File data))
        (render [_ data]
          (h/html
           [:div
            [:p "File info"]
            [:div [:b "Name: "] (h/dyn! (.getName data))]
            [::h/when (.isFile data)
             [:div [:b "Size: "] (h/dyn! (.format (java.text.NumberFormat/getIntegerInstance) (.length data))) " bytes"]]
            [::h/when (and (.isFile data) allow-download?)
             [:button.btn {:on-click #(download! data)} "Download"]]
            [::h/live (source/computed #(get % data) downloads)
             (fn [id]
               (let [url (str prefix "/file-visualizer/download?id=" id)]
                 (h/html
                  [:div [:a {:target :_blank :href url} "Download here"]])))]]))
        (ring-handler [_]
          (fn [{uri :uri q :query-string}]
            (when (= uri (str prefix "/file-visualizer/download"))
              (let [id (subs q 3)
                    file (some (fn [[file id*]]
                                 (when (= id* id) file)) @downloads)]
                (def *dl downloads)
                (swap! downloads dissoc file)
                (when file
                  {:status 200
                   :headers {"Content-Type" "application/octet-stream"
                             "Content-Disposition" (str "attachment; filename=" (.getName file))}
                   :body (ring-io/piped-input-stream
                          (fn [out]
                            (with-open [in (io/input-stream file)]
                              (io/copy in out))))})))))))))

(def default-options
  {:visualizers
   {:table-visualizer {:enabled? true}
    :file-visualizer {:enabled? true
                      :allow-download? true}}})

(defn default-visualizers
  [opts]
  (let [{v :visualizers :as opts} (merge-with merge default-options opts)]
    (remove nil?
            [(table-visualizer opts (:table-visualizer v))
             (file-visualizer opts (:file-visualizer v))])))
