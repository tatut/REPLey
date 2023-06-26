(ns repley.protocols)

(defprotocol Visualizer
  (label [this] "The label to use in the UI for this visualizer")
  (supports? [this data]
    "Check if the given data can be visualizer by this visualizer.
Should not block or do any costly calculations as this is used when
rendering the user interface.")
  (render [this data]
    "Render a Ripley component that visualizes the data."))
