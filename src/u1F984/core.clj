(ns u1F984.core
  (:gen-class)
  (:require [u1F984.telegram :as telegram]))

(def token (System/getenv "bot_token"))

(def base-url (format "https://api.telegram.org/bot%s" token))

(def routes
  [[:event.http/success []]
   [:event.telegram/new-update []]])


(defn -main
  [& args]
  (println "Hello"))


