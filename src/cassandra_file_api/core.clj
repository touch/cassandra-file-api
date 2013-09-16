;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns cassandra-file-api.core
  "The core functionality, glueing the other namespaces together."
  (:require [taoensso.timbre :as timbre :refer (trace debug info warn error fatal spy)]
            [clojure.string :refer (blank?)]
            [prime.utils :refer (guard-let)]
            [containium.systems :refer (protocol-forwarder)]
            [containium.systems.cassandra :refer (EmbeddedCassandra prepare do-prepared)])
  (:import [org.apache.cassandra.cql3 UntypedResultSet]
           [java.text SimpleDateFormat]
           [java.util Calendar]
           [java.nio CharBuffer]
           [java.nio.charset Charset]))


;;; Cassandra related.

(def cassandra-system nil)

(def retrieve-query "SELECT data FROM fs.files WHERE hash = ?;")


(defn- retrieve-data
  [hash]
  (let [^UntypedResultSet data (do-prepared cassandra-system retrieve-query :one [hash])]
    (when-not (.isEmpty data)
      (.. data one (getBytes "data") slice))))


;;; Ring related.

(def ok-response
  (let [rfc1123-formatter (SimpleDateFormat. "EEE, dd MMM yyyy HH:mm:ss zzz")
        expires-str (.format rfc1123-formatter (.getTime (doto (Calendar/getInstance)
                                                           (.add Calendar/YEAR 1))))]
    {:status 200 :headers {"Expires" expires-str}}))


(defn debug-response
  [response]
  (debug "Responding with:" response)
  response)


(defn app
  [request]
  (debug "Got request:" request)
  (if ((request :headers) "If-Modified-Since")
    (debug-response {:status 304})
    (guard-let [hash (subs (:uri request) 1) :when-not blank?]
      (if-let [bytebuffer (retrieve-data hash)]
        (debug-response (assoc ok-response :body bytebuffer))
        (debug-response {:status 404}))
      (debug-response {:status 400}))))


;;; Containium related.

(defn start
  [systems]
  (if-let [cassandra (:cassandra systems)]
    (let [cassandra ((protocol-forwarder EmbeddedCassandra) cassandra)]
      (alter-var-root #'cassandra-system (constantly cassandra))
      (alter-var-root #'retrieve-query #(prepare cassandra %))
      (info "Cassandra File API started."))
    (throw (Exception. "Missing embedded Cassandra system."))))


(defn stop [_] (info "Cassandra File API stopped."))
