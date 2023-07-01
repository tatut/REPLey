(ns repley.browser-test
  (:require [wally.main :as w]
            [repley.main :as main]
            [repley.repl :as repl]
            [clojure.test :as t :refer [deftest is testing]]
            [clojure.string :as str]))

(defn with-repl [f]
  (let [stop-server (main/start {:http {:port 4444}})]
    (w/with-page (w/make-page {:headless true})
      (try
        (w/navigate "http://localhost:4444/")
        (f)
        (finally
          (repl/clear!)
          (stop-server))))))

(t/use-fixtures :each with-repl)

(defn eval-in-repl [& code]
  (let [js (str/replace (apply str code) "'" "\\'")]
    (.evaluate (w/get-page) (str "() => _eval('" js "')"))))

(def sample-csv-url "https://media.githubusercontent.com/media/datablist/sample-csv-files/main/files/customers/customers-100.csv")

(deftest csv-table-test
  (eval-in-repl "(def url \"" sample-csv-url "\")")
  (is (= (-> 'user ns-publics (get 'url) deref) sample-csv-url))
  (eval-in-repl "(require '[clojure.data.csv :as csv]) (def customers (csv/read-csv (clojure.java.io/reader url)))")
  (Thread/sleep 1000) ;; wait for HTTP load and CSV parsing to take place
  (let [customers (-> 'user ns-publics (get 'customers) deref)]
    (is (= 101 (count customers))))
  (w/click (w/find-one-by-text :.tab "Table"))
  (w/wait "table.table")
  (is (= 100 (w/count* (w/query "table.table tbody tr"))))
  (w/fill [:evaluation "input"] "Fiji")
  (Thread/sleep 200)
  (is (= 1 (w/count* (w/query "table.table tbody tr")))))

#_(defn breadcrumbs-test
  ;; evaluate a nested map structure
  (eval-in-repl "{:hello {:there {:my [\"friend\" {:name \"Rich\"} \"!\"]}}}")
  ;; drill down from table

  ;; drill back to :there

  ;; drill back to home, breadcrumbs disappear
  )

(deftest table-data-escaped
  (eval-in-repl "{:foo 1 :bar \"<script>alert(2)</script>\"}")
  (w/click (w/find-one-by-text :.tab "Table"))
  (.pause (w/get-page))
  (is (w/find-one-by-text "td" "<script>alert(2)</script>")))
