(ns repley.protocols)

(defprotocol Visualizer
  (label [this] "The label to use in the UI for this visualizer")
  (supports? [this data]
    "Check if the given data can be visualizer by this visualizer.
Should not block or do any costly calculations as this is used when
rendering the user interface.")
  (precedence [this] "Return the precedence score of this visualizer. Bigger
number takes precedence over lower and default. Return 0 for a generic
data visualizer and 100 for a custom visualizer of a type that should be
shown by default.")
  (render [this data]
    "Render a Ripley component that visualizes the data.")
  (ring-handler [this]
    "Optional ring handler if the visualizer exposes HTTP endpoints")
  (assets [this]
    "Return optional map of frontend assets to include in the page when
this visualizer is enabled. This makes it possible to integrate JS based
visualizations. Must return either nil or a map containing keys:
  :js  a sequence of JavaScript script source URLs
  :css a sequence of CSS sources"))
