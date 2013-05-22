;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns cassandra-file-api.cassandra
  "Functions for starting and stopping an embedded Cassandra instance."
  (:use [taoensso.timbre :as timbre :only (trace debug info warn error fatal spy)])
  (:import [org.apache.cassandra.service CassandraDaemon]))


(defrecord Server [daemon thread])


(defn start-cassandra
  "Start a Cassandra instance. Give a config location, for example
  `file:dev-resources/cassandra.yaml`. Returns the Server."
  [config-location]
  (info "Starting embedded Cassandra instance, using config:" config-location)
  (System/setProperty "cassandra.config" config-location)
  (System/setProperty "cassandra-foreground" "false")
  (let [daemon (CassandraDaemon.)
        thread (Thread. #(.activate daemon))]
    (.setDaemon thread true)
    (.start thread)
    (new Server daemon thread)))


(defn stop-cassandra
  "Stop a Cassandra server instance."
  [{:keys [daemon thread] :as server}]
  (info "Stopping embedded Cassandra instance.")
  (.deactivate daemon)
  (.interrupt thread))
