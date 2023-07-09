(ns repley.visualizer.file
  (:require [repley.protocols :as p]
            [ripley.live.source :as source]
            [ripley.html :as h]
            [repley.repl :as repl]
            [ring.util.io :as ring-io]
            [clojure.java.io :as io]
            [repley.ui.icon :as icon]
            [repley.ui.table :as table]
            [clojure.string :as str]
            [ripley.js :as js])
  (:import (java.io File)
           (java.util Base64)))

(defn file-size [f]
  (str (.format (java.text.NumberFormat/getIntegerInstance) (.length f)) " bytes"))

(defn supports? [data]
  (instance? File data))

(defn image-type
  "A very limited extension based image type detection."
  [^File f]
  (let [ext (-> f .getName (str/split #"\.") last str/lower-case)]
    (case ext
      ("jpg" "jpeg") "image/jpeg"
      "png" "image/png"
      "svg" "image/svg+xml"

      ;; Not an image we care to show
      nil)))

(defn- slurp-bytes [f]
  (with-open [in (io/input-stream f)
              out (java.io.ByteArrayOutputStream.)]
    (io/copy in out)
    (.toByteArray out)))

(defn contents [file]
  (if-let [img (image-type file)]
    (let [src (str "data:" img ";base64,"
                   (.encodeToString (Base64/getEncoder) (slurp-bytes file)))]
      (h/html
       [:img {:src src}]))
    ;; Not an image, show as text in a textarea
    (h/html
     [:textarea.w-full {:rows 10} (h/dyn! (slurp file))])))

(defn render [{prefix :prefix :as _repley-config} {:keys [allow-download?] :as _opts} downloads data]
  (let [id repl/*result-id*
        download! #(swap! downloads assoc % (str (java.util.UUID/randomUUID)))
        [show-contents? set-show-contents!] (source/use-state false)]
    (h/html
     [:div
      [:p "File info"]
      [:div [:b "Name: "] (h/dyn! (.getName data))]
      [::h/if (.isFile data)
       ;; Show file size for regular files
       [:div [:b "Size: "] (h/dyn! (file-size data))]

       ;; Show listing of files for directories
       (table/table
        {:key #(.getAbsolutePath %)
         :columns [{:label "Name" :accessor #(.getName %)}
                   {:label "Size" :accessor #(if (.isDirectory %)
                                               "[DIR]"
                                               (file-size %))}]
         :on-row-click #(repl/nav-by! id (fn [_]
                                           {:label (.getName %)
                                            :value %}))}
        (source/static (.listFiles data)))]

      [::h/when (and (.isFile data) (.canRead data))
       [:details {:on-toggle (js/js set-show-contents! "window.event.target.open")}
        [:summary "Show contents"]
        [::h/live show-contents?
         (fn [show?]
           (h/html
            [:div
             [::h/when show?
              (contents data)]]))]]]
      [::h/when (and (.isFile data) (.canRead data) allow-download?)
       [:button.btn {:on-click #(download! data)} (icon/download) "Download"]]
      [::h/live (source/computed #(get % data) downloads)
       (fn [id]
         (let [url (str prefix "/file-visualizer/download?id=" id)]
           (h/html
            [:div [:a {:target :_blank :href url} "Download here"]])))]])))

(defn ring-handler [{:keys [prefix] :as _config} downloads]
  (fn [{uri :uri q :query-string}]
    (when (= uri (str prefix "/file-visualizer/download"))
      (let [id (subs q 3)
            file (some (fn [[file id*]]
                         (when (= id* id) file)) @downloads)]
        (swap! downloads dissoc file)
        (when file
          {:status 200
           :headers {"Content-Type" "application/octet-stream"
                     "Content-Disposition" (str "attachment; filename=" (.getName file))}
           :body (ring-io/piped-input-stream
                  (fn [out]
                    (with-open [in (io/input-stream file)]
                      (io/copy in out))))})))))

(defn file-visualizer [config {:keys [enabled?] :as opts}]
  (let [downloads (atom {})]
    (when enabled?
      (reify p/Visualizer
        (label [_] "File")
        (supports? [_ data] (supports? data))
        (precedence [_] 100)
        (render [_ data] (render config opts downloads data))
        (ring-handler [_] (ring-handler config downloads))))))
