;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns cassandra-file-api.core
  "The core functionality, glueing the other namespaces together."
  (:use [taoensso.timbre :as timbre :only (trace debug info warn error fatal spy)]
        [prime.utils :only (with-resource)])
  (:require [cassandra-file-api.netty :as cn]
            [cassandra-file-api.cassandra :as cc])
  (:import [org.jboss.netty.channel ChannelHandlerContext MessageEvent Channel
            ChannelFutureListener]
           [org.jboss.netty.handler.codec.http HttpRequest HttpMethod DefaultHttpResponse
            HttpVersion HttpResponseStatus HttpHeaders$Names]
           [org.jboss.netty.buffer ChannelBuffers]
           [org.apache.cassandra.cql3 QueryProcessor]
           [org.apache.cassandra.db ConsistencyLevel])
  (:gen-class))


;;; Cassandra related.

(defn- retrieve-data
  "Retrieve the file for the specified hash using the internal API of
  Cassandra. It returns a ByteBuffer to the file data, or nil if the
  file could not be found.

  This internal use of the API is based on
  https://github.com/apache/cassandra/blob/trunk/examples/client_only/src/ClientOnlyExample.java

  We will have to see how this works out, or whether this is better:
  https://svn.apache.org/repos/asf/cassandra/trunk/examples/client_only/src/ClientOnlyExample.java"
  [hash]
  (let [data (QueryProcessor/process (str "SELECT data FROM fs.files WHERE hash ='" hash "';")
                                     ConsistencyLevel/ONE)]
    (when-not (.isEmpty data)
      (.. data one (getBytes "data")))))


;;; Netty related.

(defn- handle-code
  "Respond only with a status code."
  [^Channel channel ^HttpResponseStatus code]
  (debug "Responding with bare code:" code)
  (let [response (DefaultHttpResponse. HttpVersion/HTTP_1_1 code)
        future (.write channel response)]
    (.addListener future ChannelFutureListener/CLOSE)))


(defn- handle-file-request
  "Handle a file request."
  [^HttpRequest request ^Channel channel]
  (let [hash (subs (.getUri request) 1)
        modified-since (.getHeader request HttpHeaders$Names/IF_MODIFIED_SINCE)]
    (debug "Got file request for hash:" hash)
    (if (and modified-since (seq modified-since))
      ;; A file is never modified. Keep an eye on this though, as it may not play
      ;; nice with caches and removed files.
      (handle-code channel HttpResponseStatus/NOT_MODIFIED)
      (if-let [bytebuffer (retrieve-data hash)]
        (do
          (debug "Responding with code 200 and file data.")
          (.write channel (DefaultHttpResponse. HttpVersion/HTTP_1_1 HttpResponseStatus/OK))
          (let [future (.write channel (ChannelBuffers/wrappedBuffer bytebuffer))]
            (.addListener future ChannelFutureListener/CLOSE)))
        (handle-code channel HttpResponseStatus/NOT_FOUND)))))


(defn handler-fn
  "The handler function for Netty, receiving a context and event."
  [^ChannelHandlerContext ctx ^MessageEvent e]
  (let [^HttpRequest request (.getMessage e)
        ^Channel channel (.getChannel e)]
    (if (= (.getMethod request) HttpMethod/GET)
      (handle-file-request request channel)
      (handle-code channel HttpResponseStatus/METHOD_NOT_ALLOWED))))


;;; Testing functions.

(defn -main
  "A main method for testing."
  [& args]
  (with-resource [_ (cc/start-cassandra "file:dev-resources/cassandra.yaml")] cc/stop-cassandra
    (with-resource [_ (cn/start-netty 8080 (cn/make-handler handler-fn))] cn/stop-netty
      (Thread/sleep 5000)
      (println "Press ENTER to stop.")
      (read-line))))
