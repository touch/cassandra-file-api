;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns cassandra-file-api.core-test
  (:require [cassandra-file-api.core :refer :all]
            [clojure.test :refer :all]
            [clojure.java.io :as io]
            [containium.systems :refer (with-systems)]
            [containium.systems.config :as config]
            [containium.systems.ring.http-kit :as ring]
            [containium.systems.cassandra :as cassandra]
            [containium.systems.cassandra.embedded12 :as embedded]
            [containium.systems.logging :as logging]
            [prime.types.cassandra-repository :as repository]
            [prime.utils :as utils]
            [taoensso.timbre :as timbre :refer (info)]))


;;; Fixture functions.

(def repository (promise))


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
                                                     :http-kit {:port 58080}})
                         :logging logging/logger
                         :cassandra embedded/embedded12
                         :ring (ring/test-http-kit #'app)]
    (deliver repository (repository/cassandra-repository (:cassandra systems) :one "not-used-atm"))
    (start systems {})
    (f)))


(use-fixtures :once loglevel-fixture systems-fixture)


;;; Test functions.

(deftest retrieve-test
  (testing "a file can be retrieved"
    ;; Write a file first.
    (repository/store @repository #(io/copy "hi there!" %))

    ;; Retrieve it.
    (is (= (slurp "http://localhost:58080/PjbTYi9a2tAQgMwhILtywHFOzsYRjrlSNYZBC3Q1roA")
           "hi there!"))

    ;; Retrieve it a second time.
    (is (= (slurp "http://localhost:58080/PjbTYi9a2tAQgMwhILtywHFOzsYRjrlSNYZBC3Q1roA")
           "hi there!"))

    ;; Ignore an arbitrary extension after the hash.
    (is (= (slurp "http://localhost:58080/PjbTYi9a2tAQgMwhILtywHFOzsYRjrlSNYZBC3Q1roA.doc")
           "hi there!"))))
