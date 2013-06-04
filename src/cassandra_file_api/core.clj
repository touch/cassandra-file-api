;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns cassandra-file-api.core
  "The core functionality, glueing the other namespaces together."
  (:require [cassandra-file-api.netty :as cn]
            [cassandra-file-api.cassandra :as cc]
            [taoensso.timbre :as timbre :refer (trace debug info warn error fatal spy)]
            [prime.utils :refer (with-resource guard-let)]
            [clojure.string :refer (blank?)])
  (:import [org.jboss.netty.channel ChannelHandlerContext MessageEvent Channel
            ChannelFutureListener]
           [org.jboss.netty.handler.codec.http HttpRequest HttpMethod DefaultHttpResponse
            HttpVersion HttpResponseStatus HttpHeaders$Names HttpResponse]
           [org.jboss.netty.buffer ChannelBuffers]
           [org.apache.cassandra.cql3 QueryProcessor]
           [org.apache.cassandra.db ConsistencyLevel]
           [java.text SimpleDateFormat]
           [java.util Calendar])
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

;; Create default responses only once during startup. Don't mutate these
;; objects, as they are reused for every request
(defmacro defresponse
  [name ^HttpResponseStatus code]
  `(def ~name (DefaultHttpResponse. HttpVersion/HTTP_1_1 ~code)))

(defresponse response-ok HttpResponseStatus/OK)
(defresponse response-not-modified HttpResponseStatus/NOT_MODIFIED)
(defresponse response-not-found HttpResponseStatus/NOT_FOUND)
(defresponse response-method-not-allowed HttpResponseStatus/METHOD_NOT_ALLOWED)
(defresponse response-bad-request HttpResponseStatus/BAD_REQUEST)

;; Set the expires header for the OK response.
(let [rfc1123-formatter (SimpleDateFormat. "EEE, dd MMM yyyy HH:mm:ss zzz")
      expires-str (.format rfc1123-formatter (.getTime (doto (Calendar/getInstance) (.add Calendar/YEAR 1))))]
  (.setHeader response-ok HttpHeaders$Names/EXPIRES expires-str))


(defn- do-bare-response
  "Respond only with a status code."
  [^Channel channel ^HttpResponse response]
  (debug "Responding with bare code:" (.. response getStatus getCode))
  (.addListener (.write channel response) ChannelFutureListener/CLOSE))


(defn- handle-file-request
  "Handle a file request."
  [^HttpRequest request ^Channel channel]
  (debug "Got request:" request)
  (if (seq (.getHeader request HttpHeaders$Names/IF_MODIFIED_SINCE))
    (do-bare-response channel response-not-modified)
    (guard-let [hash (subs (.getUri request) 1) :when-not blank?]
      (if-let [bytebuffer (retrieve-data hash)]
        (do
          (debug "Responding with code 200, expires header and file data.")
          (.write channel response-ok)
          (let [future (.write channel (ChannelBuffers/wrappedBuffer bytebuffer))]
            (.addListener future ChannelFutureListener/CLOSE)))
        (do-bare-response channel response-not-found))
      (do-bare-response channel response-bad-request))))


(defn handler-fn
  "The handler function for Netty, receiving a context and event."
  [^ChannelHandlerContext ctx ^MessageEvent e]
  (let [^HttpRequest request (.getMessage e)
        ^Channel channel (.getChannel e)]
    (if (= (.getMethod request) HttpMethod/GET)
      (handle-file-request request channel)
      (do-bare-response channel response-method-not-allowed))))


;;; Testing functions.

(defn -main
  "A main method for testing."
  [& args]
  (with-resource [_ (cc/start-cassandra "file:dev-resources/cassandra.yaml")] cc/stop-cassandra
    (with-resource [_ (cn/start-netty 8080 (cn/make-handler handler-fn))] cn/stop-netty
      (timbre/set-level! :debug)
      (Thread/sleep 5000)
      (println "Press ENTER to stop.")
      (read-line))))
