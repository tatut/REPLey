(ns repley.visualizer.vega
  "Visualize data using Vega-Lite."
  (:require [ripley.html :as h]
            [repley.protocols :as p]
            [cheshire.core :as cheshire]))

(def ^:private assets
  {:js [{:src "https://cdnjs.cloudflare.com/ajax/libs/vega/5.25.0/vega.min.js"
         :integrity "sha384-iY3zZAtrtgjJoD8rliThCLEeLUYo8aSNWYQkL+Jaa3KQEAACPnaw/lQIRrFbPCsj"
         :crossorigin "anonymous"}
        {:src "https://cdnjs.cloudflare.com/ajax/libs/vega-lite/5.13.0/vega-lite.min.js"
         :integrity "sha384-p6KYC7E/n1mGQ58+/RZp4aMe/eAJt3jkaZak4G0efYrc6X3101BXrpT7dOvYp+aC"
         :crossorigin "anonymous"}
        {:src "https://cdnjs.cloudflare.com/ajax/libs/vega-embed/6.22.1/vega-embed.min.js"
         :integrity "sha384-6U8AI3zeuX93P24mCh2fFtZitabRM/8UHBkdnUYSCtIDVlInxMeCw6N26Oeh66mi"
         :crossorigin "anonymous"}]})

(defn- strip-vega-ns [m]
  (into {}
        (map (fn [[k v]]
               (let [new-k (if (and (keyword? k)
                                    (= "vega" (namespace k)))
                             (keyword (name k))
                             k)
                     new-v (if (map? v)
                             (strip-vega-ns v)
                             v)]
                 [new-k new-v])))
        m))

(defn- as-vega [data]
  (cheshire/encode
   (strip-vega-ns
    (merge {"$schema" "https://vega.github.io/schema/vega-lite/v5.json"}
           data))))

(defn- render [_opts data]
  (let [id (gensym "vega")]
    (h/html
     [:div.m-4
      [:div {:id id}]
      [:script
       "vegaEmbed('#" id "', "
       (h/out! (as-vega data))
       ", {renderer: 'svg'});"]])))

(defn vega-visualizer [_ {enabled? :enabled? :as opts}]
  (when enabled?
    (reify p/Visualizer
      (label [_] "Vega")
      (supports? [_ data]
        (or (:vega? (meta data))
            (and (map? data)
                 (some #(and (keyword? %)
                             (= (namespace %) "vega")) (keys data)))))
      (precedence [_] 100)
      (render [_ data]
        (render opts data))
      (ring-handler [_] nil)
      (assets [_] assets))))


(comment

  ^{:vega? true}
  {:data
   {:values [{:a "C" :b 2}
             {:a "C" :b 7}
             {:a "C" :b 4}
             {:a "D" :b 1}
             {:a "D" :b 2}
             {:a "D" :b 6}
             {:a "E" :b 8}
             {:a "E" :b 4}
             {:a "E" :b 7}]}
   :mark :bar
   :encoding {:y {:field :a :type :nominal}
              :x {:aggregate :average
                  :field :b
                  :type :quantitative
                  :axis {:title "Average of b"}}}}


  #:vega
  {:data
   {:values [{:category "A" :group "x" :value 0.1}
             {:category "A" :group "y" :value 0.6}
             {:category "A" :group "z" :value 0.9}
             {:category "B" :group "x" :value 0.7}
             {:category "B" :group "y" :value 0.2}
             {:category "B" :group "z" :value 1.1}
             {:category "C" :group "x" :value 0.6}
             {:category "C" :group "y" :value 0.1}
             {:category "C" :group "z" :value 0.2}]}
   :mark :bar
   :encoding #:vega {:x {:field :category}
                     :y {:field :value :type :quantitative}
                     :xOffset {:field :group}
                     :color {:field :group}}}


  ^{:vega? true}
  {:mark :bar
   :encoding {:x {:field :length :title "Length of name"}
              :y {:aggregate :count :title "# of public vars"}}
   :data {:values (mapv (fn [[public-name _]]
                          {:length (count (name public-name))})
                        (ns-publics 'clojure.core))}}

  )
