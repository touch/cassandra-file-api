;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns cassandra-file-api.core
  "The core functionality, glueing the other namespaces together."
  (:require [taoensso.timbre :as timbre :refer (trace debug info warn error fatal spy)]
            [clojure.string :refer (blank?)]
            [clojure.java.io :as io]
            [ring.middleware.cors :refer (wrap-cors)]
            [ring.middleware.gzip :refer (wrap-gzip)]
            [ring.util.response :refer (resource-response)]
            [prime.utils :refer (guard-let)]
            [prime.types.cassandra-repository :as cr])
  (:import [java.text SimpleDateFormat]
           [java.util Calendar Locale]
           [java.nio CharBuffer]
           [java.nio.charset Charset]
           [org.apache.commons.codec.binary Base64]))


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

(def cassandra-repo nil)


(defn- retrieve-data
  [^String hash]
  (cr/fetch cassandra-repo (prime.types/FileRef ^bytes (Base64/decodeBase64 hash))))


;;; Ring related.

(def ok-response
  (let [rfc1123-formatter (SimpleDateFormat. "EEE, dd MMM yyyy HH:mm:ss zzz" Locale/US)
        expires-str (.format rfc1123-formatter (.getTime (doto (Calendar/getInstance)
                                                           (.add Calendar/YEAR 1))))]
    {:status 200 :headers {"Expires" expires-str}}))


(defn debug-response
  [response]
  (debug "Responding with:" response)
  response)

(defn- with-headers [request response]
  (if (.endsWith (-> request :uri str) ".jsp")
    (update-in response [:headers] assoc "Content-Encoding" "gzip", "Content-Type" "text/js", "Access-Control-Allow-Origin" "*")
    response))

(defn cassandra-file-app
  [request]
  (debug "Got request:" request)
  (if (get (request :headers) "If-Modified-Since")
    (debug-response {:status 304})
    (guard-let [hash (subs (:uri request) 1) :when-not blank?]
      (if-let [stream (retrieve-data (strip-extension hash))] ; Hiercheck of gzip
        (debug-response (with-headers request (assoc ok-response :body stream)))
        (or (if-let [res (resource-response (:uri request))]
              (if (.endsWith (-> request :uri str) ".xml")
                (assoc-in res [:headers "content-type"] "application/xml")
                res))
            (debug-response {:status 404 :headers {"Access-Control-Allow-Origin" "*"}})))
      (debug-response {:status 400 :headers {"Access-Control-Allow-Origin" "*"}}))))


(def app
  (-> cassandra-file-app 
      (wrap-cors :access-control-allow-origin #".*"
                 :access-control-allow-methods [:get])
      (wrap-gzip)))

;;; Containium related.

(defn start
  [systems conf]
  (if-let [cassandra (:cassandra systems)]
    (let [repo (cr/cassandra-repository cassandra :one "fs")]
      (alter-var-root #'cassandra-repo (constantly repo))
      (info "Cassandra File API started."))
    (throw (Exception. "Missing embedded Cassandra system."))))


(defn stop [_] (info "Cassandra File API stopped."))
