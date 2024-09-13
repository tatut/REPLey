(ns repley.browser
  "Namespace browser UI"
  (:require [ripley.html :as h]
            [ripley.live.source :as source]
            [ripley.js :as js]
            [clojure.string :as str]
            [ripley.live.collection :as collection]
            [repley.ui.icon :as icon]
            [repley.repl :as repl]
            ))

(defn- ns-component [ns]
  (let [n (ns-name ns)
        [open? _ update-open!] (source/use-state false)
        toggle-open! #(update-open! not)]
    (h/html
     [:tr
      [:td.align-top
       [:button.btn.btn-xs
        {:onclick toggle-open!}
        [::h/live open? #(if %
                           (icon/caret-down)
                           (icon/caret-right))]]]
      [:td n

       [::h/live (source/computed
                  #(when % (vals (ns-publics ns)))
                  open?)
        (fn [children]
          (h/html
           [:ul
            [::h/for [c children
                      :let [var-ref (str c)
                            val @c
                            type (cond
                                   (fn? val) "fn"
                                   :else "v")
                            doc (some-> c meta :doc str/split-lines first)
                            [_ name] (str/split var-ref #"/")]]
             [:li
              [::h/when type
               [:div.badge.badge-neutral.badge-xs type]]
              " "
              [:span.cursor-pointer
               {:onclick #(repl/eval-input! var-ref)}
               [::h/if doc
                [:div.tooltip {:data-tip doc} name]
                name]]
              [::h/when (fn? val)
               ;; btn to copy to input
               [::button.btn.btn-xs
                {:onclick (str "_E.session.setValue('(" (subs var-ref 2) " )');"
                               "_E.moveCursorTo(0," (count var-ref) ");"
                               "_E.focus();")}
                (icon/pencil-2)]]]]]))]]])))

(defn browser []
  (let [[state _ update-state!] (source/use-state {:filter ""
                                                   :open #{}})
        set-filter! #(update-state! assoc :filter %)]
    (h/html
     [:div.browser.border-solid.border-2.p-2 {:style "width: 40vw;"}
      [:input#ns-filter.input.input-bordered.w-full
       {:placeholder "Filter namespaces..."
        :oninput (js/js-debounced 300 set-filter! (js/input-value :ns-filter))}]
      [:div {:style "max-height: 70vh; overflow-y: auto;"}
       "Showing "
       [::h/live-let [f (source/computed :filter state)]
        [:span
         [::h/if (str/blank? f)
          [:span "all namespaces"]
          [:span "namespaces matching: " f]]]]

       [:table.table.table-xs
        [:thread [:tr
                  [:td {:style "width: 30px;"}]
                  [:td "Namespace"]]]
        (collection/live-collection
         {:key ns-name
          :container-element :tbody
          :source (source/computed (fn [{filter-str :filter}]
                                     (for [ns (all-ns)
                                           :let [n (ns-name ns)]
                                           :when (str/includes? n filter-str)]
                                       ns))
                                   state)
          :render ns-component})]]])))
