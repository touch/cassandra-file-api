;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns cassandra-file-api.core
  "The core functionality, glueing the other namespaces together."
  (:require [taoensso.timbre :as timbre :refer (trace debug info warn error fatal spy)]
            [prime.utils :refer (guard-let)]
            [clojure.string :refer (blank?)])
  (:import [org.apache.cassandra.cql3 QueryProcessor UntypedResultSet]
           [org.apache.cassandra.db ConsistencyLevel]
           [org.apache.cassandra.service ClientState QueryState]
           [java.text SimpleDateFormat]
           [java.util Calendar]
           [java.nio CharBuffer]
           [java.nio.charset Charset]))


;;; Cassandra related.

(def prepared-query
  (delay (let [query "SELECT data FROM fs.files WHERE hash = ?;"
               prep-result (QueryProcessor/prepare query (ClientState. true) false)]
           (QueryProcessor/getPrepared (.statementId prep-result)))))


(defn- string->bytebuffer
  "Wrap a String in a ByteBuffer."
  [s]
  (let [encoder (.newEncoder (Charset/forName "UTF-8"))]
    (.encode encoder (CharBuffer/wrap s))))


(defn- retrieve-data
  "Retrieve the file for the specified hash using the internal API of
  Cassandra. It returns a ByteBuffer to the file data, or nil if the
  file could not be found.

  This internal use of the API is based on
  https://github.com/apache/cassandra/blob/trunk/examples/client_only/src/ClientOnlyExample.java

  We will have to see how this works out, or whether this is better:
  https://svn.apache.org/repos/asf/cassandra/trunk/examples/client_only/src/ClientOnlyExample.java"
  [hash]
  (let [msg (QueryProcessor/processPrepared (deref prepared-query)
                                             ConsistencyLevel/ONE
                                             (QueryState. (ClientState. true))
                                             (list (string->bytebuffer hash)))
        data (UntypedResultSet. (.result msg))]
    (when-not (.isEmpty data)
      (.. data one (getBytes "data")))))


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
  [config]
  (info "Cassandra File API started."))


(defn stop
  [_]
  (info "Cassandra File API stopped."))
