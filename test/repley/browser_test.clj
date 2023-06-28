(ns repley.browser-test
  (:require [wally.main :as w]
            [wally.selectors :as ws]
            [repley.main :as main]
            [clojure.test :as t :refer [deftest is testing]]
            [clojure.string :as str]))

(defn with-repl [f]
  (let [stop-server (main/start {:http {:port 4444}})]
    (w/with-page (w/make-page {:headless false})
      (try
        (w/navigate "http://localhost:4444/")
        (f)
        (finally
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
  (w/click (w)"")
  )
