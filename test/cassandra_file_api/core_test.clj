;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns cassandra-file-api.core-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :refer (copy)]
            [midje.sweet :refer :all]
            [cassandra-file-api.core :refer :all]
            [cassandra-file-api.cassandra :as cc]
            [prime.types.cassandra-repository :as cr]
            [prime.utils :refer (with-resource)]
            [qbits.alia :as alia]
            [taoensso.timbre :as timbre :refer (info)]
            [org.httpkit.server :refer (run-server)]))


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


(defn cassandra-fixture
  [f]
  (with-resource [cassandra (cc/start-cassandra "file:dev-resources/cassandra.yaml")]
    cc/stop-cassandra
    (while (not (cc/running? cassandra))
      (info "Waiting for Cassandra daemon to be fully started...")
      (Thread/sleep 500))
    (info "Cassandra daemon fully started.")
    (prepare-cassandra @cluster)
    (reset! cr/consistency :one)
    (try
      (f)
      (finally
        (alia/shutdown @cluster)))))


(defn http-kit-fixture
  [f]
  (let [stop-fn (run-server #'app {:port 58080})]
    (try
      (f)
      (finally
        (stop-fn)))))


(defn loglevel-fixture
  [f]
  (let [log-level-before (:current-level @timbre/config)]
    (timbre/set-level! :info)
    (try
      (f)
      (finally
        (timbre/set-level! log-level-before)))))


(use-fixtures :once loglevel-fixture http-kit-fixture cassandra-fixture)


;;; Test functions.

(deftest retrieve-test
  (fact "a file can be retrieved"
    (let [repo (cr/cassandra-repository @cluster "not-used-atm")]
      (cr/store repo #(copy "hi there!" %))
      (slurp "http://localhost:58080/PjbTYi9a2tAQgMwhILtywHFOzsYRjrlSNYZBC3Q1roA") => "hi there!"
      (slurp "http://localhost:58080/PjbTYi9a2tAQgMwhILtywHFOzsYRjrlSNYZBC3Q1roA") => "hi there!")))
