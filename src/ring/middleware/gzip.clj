(ns ring.middleware.gzip
  "Ring gzip compression."
  (:require [clojure.java.io :as io]
            [taoensso.timbre :as timbre :refer (trace debug info warn error fatal spy)])
  (:import (java.io InputStream
                    Closeable
                    File
                    PipedInputStream
                    PipedOutputStream)
           (java.nio ByteBuffer)
           (java.nio.channels Channels)
           (java.util.zip GZIPOutputStream)))

(defn- accepts-gzip?
  [req]
  (if-let [accepts (get-in req [:headers "accept-encoding"])]
    ;; Be aggressive in supporting clients with mangled headers (due to
    ;; proxies, av software, buggy browsers, etc...)
    (re-seq
      #"(gzip\s*,?\s*(gzip|deflate)?|X{4,13}|~{4,13}|\-{4,13})"
      accepts)))

;; Set Vary to make sure proxies don't deliver the wrong content.
(defn- set-response-headers
  [headers]
  (if-let [vary (or (get headers "vary") (get headers "Vary"))]
    (-> headers
      (assoc "Vary" (str vary ", Accept-Encoding"))
      (assoc "Content-Encoding" "gzip")
      (dissoc "Content-Length" "content-length")
      (dissoc "vary"))
    (-> headers
      (assoc "Vary" "Accept-Encoding")
      (assoc "Content-Encoding" "gzip")
      (dissoc "Content-Length" "content-length"))))

(def ^:private supported-status? #{200, 201, 202, 203, 204, 205 403, 404})

(defn- unencoded-type?
  [headers]
  (if (or (headers "Content-Encoding") (headers "content-encoding"))
    false
    true))

(defn- supported-type?
  [resp]
  (let [{:keys [headers body]} resp]
    (or (string? body)
        (seq? body)
        (instance? InputStream body)
        (instance? ByteBuffer body)
        (and (instance? File body)
             (re-seq #"(?i)\.(htm|html|css|js|json|xml)" (pr-str body))))))

(def ^:private min-length 859)

(defn- supported-size?
  [resp]
  (let [{body :body} resp]
    (cond
      (string? body) (> (count body) min-length)
      (seq? body) (> (count body) min-length)
      (instance? File body) (> (.length ^File body) min-length)
      :else true)))

(defn- supported-response?
  [resp]
  (let [{:keys [status headers]} resp]
    (debug (str
        "(supported-status? status) " (supported-status? status)
        "\n(unencoded-type? headers)" (unencoded-type? headers)
        "\n(supported-type? resp)" (supported-type? resp)
        "\n(supported-size? resp)" (supported-size? resp)
        ))
    (and (supported-status? status)
         (unencoded-type? headers)
         (supported-type? resp)
         (supported-size? resp))))

(defn- compress-body
  [body]
  (let [p-in (PipedInputStream.)
        p-out (PipedOutputStream. p-in)]
    (future
      (with-open [out (GZIPOutputStream. p-out)]
        (if (seq? body)
          (doseq [string body] (io/copy (str string) out))
          (if (instance? ByteBuffer body) 
              (let [outChannel (Channels/newChannel out)]
                  (.write outChannel body))
              (io/copy body out))))
      (when (instance? Closeable body)
        (.close ^Closeable body)))
    p-in))

(defn- gzip-response
  [resp]
  (-> resp
    (update-in [:headers] set-response-headers)
    (update-in [:body] compress-body)))

(defn wrap-gzip
  "Middleware that compresses responses with gzip for supported user-agents."
  [handler]
  (fn [req]
    (if (accepts-gzip? req)
      (do
          (debug "accept gzip")
          (let [resp (handler req)]
            (if (supported-response? resp)
                (do
                    (debug "supported response")
                    (gzip-response resp))
                (do
                    (debug "unsupported response")
                    resp))))
        (do
            (debug "do not accept gzip")
            (handler req)))))