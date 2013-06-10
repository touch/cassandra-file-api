;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(defproject cassandra-file-api "0.1.0-SNAPSHOT"
  :description "A REST interface to retrieve files from our Cassandra repository."
  :url "https://github.com/containium/cassandra-file-api"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [boxure/shared_2.9.2 "0.1-SNAPSHOT"]
                 [org.apache.cassandra/cassandra-all "1.2.3"]
                 [io.netty/netty "3.5.9.Final"]  ; Remove this when Cassandra pom gets its dependencies right.
                 [midje "1.5.1"]
                 [com.taoensso/timbre "0.8.1"]]  ; Version 1.6 is available, update all?
  :profiles {:dev {:plugins [[lein-immutant "1.0.0.beta1"]]
                   :immutant {:cassandra-config-file-path "cassandra.yaml"
                              :netty-port 8090
                              :nrepl-port 4343}}
             :prod {:immutant {:cassandra-config-file-path "/etc/immutant/cassandra.yaml"
                               :netty-port 8090}}}
  :main cassandra-file-api.core)
