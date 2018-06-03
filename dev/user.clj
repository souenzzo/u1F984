(ns user
  (:require [clojure.java.jdbc :as j]
            [com.wsscode.pathom.core :as p]
            [walkable.sql-query-builder :as sqb]))

(def db
  {:dbtype   "postgresql"
   :dbname   "u1f984"
   :host     "localhost"
   :user     "postgres"
   :password "abc123"})


(defn create-database
  [{:keys [dbtype dbname host user password]}]
  (j/db-do-commands
    (format "%s://%s:%s@%s:5432/"
            dbtype user password host)
    false
    (format "CREATE DATABASE %s;"
            dbname)))

(defn create-tables
  [db]
  (j/db-do-commands db
                    (j/create-table-ddl :message
                                        {:id      "uuid primary key not null"
                                         :text    "text not null"
                                         :chat_id "text not null"
                                         :sent    "boolean"}))
  (j/db-do-commands db
                    (j/create-table-ddl :message
                                        {:id      "uuid primary key not null"
                                         :text    "text not null"
                                         :chat_id "text not null"
                                         :sent    "boolean"})))

#_(p/parser {::p/plugins [(p/env-plugin {::p/reader [sqb/pull-entities
                                                     p/map-reader]})]})
#_(parser {::sqb/sql-db     db
           ::sqb/run-query  (fn [& args]
                              (prn (second args))
                              (apply j/query args))
           ::sqb/sql-schema sql-schema}
          '[{:messages/all [:message/id]}])

(def schema
  (sqb/compile-schema
    {:quote-marks sqb/quotation-marks
     :idents      {:messages/all "message"}
     :columns     #{:message/id
                    :message/sent
                    :message/chat_id
                    :message/text}}))