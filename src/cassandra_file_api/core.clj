;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns cassandra-file-api.core
  "The core functionality, glueing the other namespaces together."
  (:require [taoensso.timbre :as timbre :refer (trace debug info warn error fatal spy)]
            [clojure.string :refer (blank?)]
            [clojure.java.io :as io]
            [prime.utils :refer (guard-let)]
            [containium.systems :refer (protocol-forwarder)]
            [containium.systems.cassandra :refer (Cassandra prepare do-prepared has-keyspace?
                                                           write-schema)])
  (:import [org.apache.cassandra.cql3 UntypedResultSet]
           [java.text SimpleDateFormat]
           [java.util Calendar]
           [java.nio CharBuffer]
           [java.nio.charset Charset]))


;;; Helper functions.

(defn strip-extension
  "Removes the extension from a String, e.g.
  (strip-extension \"foo.bar.baz\") => \"foo.bar\"
  (strip-extension \"foobarbaz\") => \"foobarbaz\""
  [s]
  (guard-let [i (.lastIndexOf s ".") :when-not (partial = -1)]
    (subs s 0 (.lastIndexOf s "."))
    s))


;;; Cassandra related.

(def cassandra-system nil)

(def retrieve-query "SELECT data FROM fs.files WHERE hash = ?;")


(defn- retrieve-data
  [hash]
  (let [data (do-prepared cassandra-system retrieve-query {:consistency :one} [hash])]
    (when-not (empty? data)
      (get (first data) "data"))))


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
      (if-let [bytebuffer (retrieve-data (strip-extension hash))]
        (debug-response (assoc ok-response :body bytebuffer))
        (debug-response {:status 404}))
      (debug-response {:status 400}))))


;;; Containium related.

(defn start
  [systems conf]
  (if-let [cassandra (:cassandra systems)]
    (let [cassandra ((protocol-forwarder Cassandra) cassandra)]
      (when-not (has-keyspace? cassandra "fs")
        (write-schema cassandra (slurp (io/resource "cassandra-repo-schema.cql"))))
      (alter-var-root #'cassandra-system (constantly cassandra))
      (alter-var-root #'retrieve-query #(prepare cassandra %))
      (info "Cassandra File API started."))
    (throw (Exception. "Missing embedded Cassandra system."))))


(defn stop [_] (info "Cassandra File API stopped."))
