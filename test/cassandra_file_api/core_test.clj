;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns cassandra-file-api.core-test
  (:require [cassandra-file-api.core :refer :all]
            [clojure.test :refer :all]
            [clojure.java.io :refer (copy)]
            [midje.sweet :refer :all]
            [containium.systems :refer (with-systems)]
            [containium.systems.config :refer (map-config)]
            [containium.systems.cassandra :refer (embedded12)]
            [prime.types.cassandra-repository :as cr]
            [qbits.alia :as alia]
            [org.httpkit.server :refer (run-server)]
            [prime.utils :refer (with-resource)]
            [taoensso.timbre :as timbre :refer (info)]))


;;; Fixture functions.

(def cluster (delay (alia/cluster "localhost" :port 9042)))


(defn- prepare-cassandra
  "Write the schema to the database."
  [cluster]
  (alia/with-session (alia/connect cluster)
    (try
      (alia/execute "DROP KEYSPACE fs;")
      (catch Exception ex))
    (cr/write-schema cluster)))


(defn loglevel-fixture
  [f]
  (let [log-level-before (:current-level @timbre/config)]
    (timbre/set-level! :info)
    (try
      (f)
      (finally
        (timbre/set-level! log-level-before)))))


(defn http-kit-fixture
  [f]
  (let [stop-fn (run-server #'app {:port 58080})]
    (try
      (f)
      (finally
        (stop-fn)))))


(defn systems-fixture
  [f]
  (with-systems systems [:config (map-config {:cassandra {:config-file "cassandra.yaml"}})
                         :cassandra embedded12]
    (prepare-cassandra @cluster)
    (reset! cr/consistency :one)
    (start systems)
    (try
      (f)
      (finally
        (alia/shutdown @cluster)))))


(use-fixtures :once loglevel-fixture http-kit-fixture systems-fixture)


;;; Test functions.

(deftest retrieve-test
  (fact "a file can be retrieved"
    (let [repo (cr/cassandra-repository @cluster "not-used-atm")]
      (cr/store repo #(copy "hi there!" %))
      (slurp "http://localhost:58080/PjbTYi9a2tAQgMwhILtywHFOzsYRjrlSNYZBC3Q1roA") => "hi there!"
      (slurp "http://localhost:58080/PjbTYi9a2tAQgMwhILtywHFOzsYRjrlSNYZBC3Q1roA") => "hi there!")))
