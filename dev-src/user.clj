(ns user)

(defn start []
  ((requiring-resolve 'repley.main/start)))

(start)
(println "REPLey started, open browser to: http://localhost:3001/")
