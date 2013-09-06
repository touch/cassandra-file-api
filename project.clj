;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(defproject cassandra-file-api "0.1.0-SNAPSHOT"
  :description "A REST interface to retrieve files from our Cassandra repository."
  :url "https://github.com/containium/cassandra-file-api"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [boxure/shared_2.9.2 "0.1-SNAPSHOT"]
                 [com.taoensso/timbre "2.6.1"]]
  :profiles {:provided {:dependencies [[org.apache.cassandra/cassandra-all "1.2.8"]]}
             :dev {:dependencies [[midje "1.5.1"]
                                  [http-kit "2.1.10"]]}}
  :containium {:start cassandra-file-api.core/start
              :stop cassandra-file-api.core/stop
              :ring {:handler cassandra-file-api.core/app
                     :context-path "/files"}})
