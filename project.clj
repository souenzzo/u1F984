(defproject u1F984 "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [clj-http/clj-http "3.9.0"]
                 [io.pedestal/pedestal.interceptor "0.5.3"]
                 [org.clojure/data.json "0.2.6"]]
  :uberjar-name "u1F984.jar"
  :profiles {:dev {:dependencies [[org.clojure/test.check "0.9.0"]
                                  [midje/midje "1.9.1"]]
                   :source-paths ["src" "dev" "test"]}})
