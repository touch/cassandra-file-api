;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns cassandra-file-api.netty
  "The netty related functions."
  (:use [taoensso.timbre :as timbre :only (trace debug info warn error fatal spy)])
  (:import [java.net InetSocketAddress]
           [java.util.concurrent Executors]
           [org.jboss.netty.bootstrap ServerBootstrap]
           [org.jboss.netty.channel Channels ChannelPipelineFactory SimpleChannelHandler
            SimpleChannelUpstreamHandler ChannelHandler]
           [org.jboss.netty.channel.socket.nio NioServerSocketChannelFactory]
           [org.jboss.netty.buffer ChannelBuffers]
           [org.jboss.netty.handler.codec.http HttpRequestDecoder HttpResponseEncoder]))


(defrecord Server [bootstrap channel])


(defn make-handler
  "Returns a Netty handler, using the supplied function for the
  messageReceived method."
  [f]
  (proxy [SimpleChannelHandler] []
    (messageReceived [ctx e] (f ctx e))
    (exceptionCaught [ctx e]
      (error (.getCause e))
      (.. e getChannel close))))


(defn start-netty
  "Start a Netty server. Returns the Server instance."
  [port ^ChannelHandler handler]
  (info "Starting Netty server on port" port)
  (let [channel-factory (NioServerSocketChannelFactory.
                         (Executors/newCachedThreadPool)
                         (Executors/newCachedThreadPool))
        bootstrap (ServerBootstrap. channel-factory)
        pipeline (.getPipeline bootstrap)]
    (.addLast pipeline "decoder" (new HttpRequestDecoder))
    (.addLast pipeline "encoder" (new HttpResponseEncoder))
    (.addLast pipeline "handler" handler)
    (.setOption bootstrap "child.tcpNoDelay", true)
    (.setOption bootstrap "child.keepAlive", true)
    (new Server bootstrap (.bind bootstrap (InetSocketAddress. port)))))


(defn stop-netty
  "Stops a Server instance"
  [{:keys [bootstrap channel] :as server}]
  (info "Stopping Netty server.")
  (.unbind channel)
  (.releaseExternalResources bootstrap))
