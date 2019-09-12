;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(defproject cassandra-file-api "0.2.0-SNAPSHOT"
  :description "A REST interface to retrieve files from our Cassandra repository."
  :url "https://github.com/containium/cassandra-file-api"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [prime/utils "0.2.0-SNAPSHOT"
                  :exclusions [org.slf4j/slf4j-api org.slf4j/slf4j-log4j12]]
                 [prime/filerepository-cassandra "0.2.0-SNAPSHOT"]
                 [ring-cors "0.1.6"]
                 [com.esotericsoftware.kryo/kryo "2.22"]
                 [com.taoensso/timbre "4.3.1"]
                 [commons-codec "1.8"]]
  :profiles {:test {:dependencies [[containium/containium "0.2.0-SNAPSHOT"
                                    :exclusions [leiningen-core clojure-complete boxure
                                                 com.taoensso/nippy
                                                 jline midje boxure/clojure]]
                                   [boxure "0.1.0-SNAPSHOT"]]}}
  :plugins [[lein-libdir "0.1.1"]]
  :containium {:start cassandra-file-api.core/start
              :stop cassandra-file-api.core/stop
              :ring {:handler cassandra-file-api.core/app
                     :context-path "/files"}})
