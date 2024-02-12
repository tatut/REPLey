# REPLey

![test workflow](https://github.com/tatut/repley/actions/workflows/test.yml/badge.svg)

A web REPL made with [Ripley](https://github.com/tatut/ripley)

Some features:
- Nice and extensible visualization of data (including tables and graphs)
- Clojure input with autocompletion (using compliment)
- `tap>` listener (to use as a fancy logging mechanism)
- easy to integrate into an existing app

## Usage

Start with ```% clj -M:dev``` and open browser to localhost
port 3001. Use Cmd-Enter to evaluate text in the editor.

Another way to use is to include this as a library and use `repley.main/repley-handler` as a handler
in a ring application. This code has **NO** authentication or sand boxing, so obviously don't use
this in a production app.

## Demos

Watch the  [YouTube intro video](https://youtu.be/UiRq97HZctQ).

### Inspecting properties map as a table

Clojure maps have a table visualization for convenient inspection.
![Props map as table](https://github.com/tatut/REPLey/assets/83725/6bdd7db7-6c30-4680-8bbf-e0ea9489eef2)


### File visualizer

Instances of `java.io.File` class have a visualizer that shows basic info and allows
downloading the file. This can be disabled in config.

![File visualizer](https://github.com/tatut/REPLey/assets/83725/135efabf-daf7-4ba6-9706-8b053bafea91)

### CSV support

CSV read by `clojure.data.csv` is also supported in the table visualizer.

![CSV table](https://github.com/tatut/REPLey/assets/83725/84df15cc-bc93-4cac-89ea-4229eecffbc8)


# Changes

* 2024-02-12: Support objects that render themselves (DefaultVisualizer protocol)
