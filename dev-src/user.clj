(ns user)

(defn start []
  ((requiring-resolve 'repley.main/start)))

(start)
