# REPLey

A web REPL made with Ripley

## Usage

Start with ```% clj -e "((requiring-resolve 'repley.main/start))"``` and open browser to localhost
port 3001. Use Cmd-Enter to evaluate text in the editor.

Another way to use is to include this as a library and use `repley.main/repley-handler` as a handler
in a ring application. This code has **NO** authentication or sand boxing, so obviously don't use
this in a production app.

## Demos

Inspecting properties map as a table:
![Props map as table](https://github.com/tatut/REPLey/assets/83725/6bdd7db7-6c30-4680-8bbf-e0ea9489eef2)


File info can download file (can be disabled in config):
![File visualizer](https://github.com/tatut/REPLey/assets/83725/135efabf-daf7-4ba6-9706-8b053bafea91)


