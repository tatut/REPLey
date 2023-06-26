# REPLey

A web REPL made with Ripley

## Usage

Start with ```% clj -e "((requiring-resolve 'repley.main/start))"``` and open browser to localhost
port 3001. Use Cmd-Enter to evaluate text in the editor.

Another way to use is to include this as a library and use `repley.main/repley-handler` as a handler
in a ring application. This code has **NO** authentication or sand boxing, so obviously don't use
this in a production app.
