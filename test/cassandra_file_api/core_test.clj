;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns cassandra-file-api.core-test
  (:require [cassandra-file-api.core :refer :all]
            [clojure.test :refer :all]
            [clojure.java.io :as io]
            [midje.sweet :refer :all]
            [containium.systems :refer (with-systems)]
            [containium.systems.config :as config]
            [containium.systems.ring :as ring]
            [containium.systems.cassandra :as cassandra]
            [containium.systems.cassandra.embedded12 :as embedded]
            [containium.systems.cassandra.alia1 :as alia]
            [prime.types.cassandra-repository :as repository]
            [prime.utils :as utils]
            [taoensso.timbre :as timbre :refer (info)]))


;;; Fixture functions.

(defn- prepare-cassandra
  "Write the schema to the database."
  [alia]
  (when (cassandra/has-keyspace? alia "fs")
    (cassandra/write-schema alia "DROP KEYSPACE fs;"))
  (repository/write-schema (:cluster alia))
  (let [repo (repository/cassandra-repository (:cluster alia) "not-used-atm")]
    (repository/store repo #(io/copy "hi there!" %))))


(defn loglevel-fixture
  [f]
  (let [log-level-before (:current-level @timbre/config)]
    (timbre/set-level! :info)
    (try
      (f)
      (finally
        (timbre/set-level! log-level-before)))))


(defn systems-fixture
  [f]
  (with-systems systems [:config (config/map-config {:cassandra {:config-file "cassandra.yaml"}
                                                     :alia {:contact-points ["localhost"]}
                                                     :http-kit {:port 58080}})
                         :cassandra embedded/embedded12
                         :alia (alia/alia1 :alia)
                         :ring (ring/test-http-kit #'app)]
    (reset! repository/consistency :one)
    (prepare-cassandra (:alia systems))
    (start systems {})
    (f)))


(use-fixtures :once loglevel-fixture systems-fixture)


;;; Test functions.

(deftest retrieve-test
  (fact "a file can be retrieved"
    ;; Retrieve it.
    (slurp "http://localhost:58080/PjbTYi9a2tAQgMwhILtywHFOzsYRjrlSNYZBC3Q1roA") => "hi there!"

    ;; Retrievi it a second time.
    (slurp "http://localhost:58080/PjbTYi9a2tAQgMwhILtywHFOzsYRjrlSNYZBC3Q1roA") => "hi there!"

    ;; Ignore an arbitrary extension after the hash.
    (slurp "http://localhost:58080/PjbTYi9a2tAQgMwhILtywHFOzsYRjrlSNYZBC3Q1roA.doc") => "hi there!"))
