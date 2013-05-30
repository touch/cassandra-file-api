;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns immutant.init
  (:require [immutant.daemons :as daemons]
            [immutant.registry :as registry]
            [cassandra-file-api.cassandra :as cassandra]
            [cassandra-file-api.netty :as netty]
            [cassandra-file-api.core :as core]
            [taoensso.timbre :refer (info error)]))


;;; Cassandra Immutant daemon.

(def cassandra-server (atom nil))

(defn start-cassandra []
  (if-let [config-path (:cassandra-config-file-path (registry/get :config))]
    (reset! cassandra-server (cassandra/start-cassandra config-path))
    (error "Could not start Cassandra immutant daemon, no :cassandra-config-file-path setting"
           "found in project map.")))

(defn stop-cassandra []
  (when @cassandra-server
    (cassandra/stop-cassandra @cassandra-server)
    (reset! cassandra-server nil)))

(daemons/daemonize "cassandra" start-cassandra stop-cassandra)


;;; Netty Immutant daemon.

(def netty-server (atom nil))

(defn start-netty []
  (if-let [port (:netty-port (registry/get :config))]
    (reset! netty-server (netty/start-netty port (netty/make-handler core/handler-fn)))
    (error "Could not start Netty immutant daemon, no :netty-port setting found in project map.")))

(defn stop-netty []
  (when @netty-server
    (netty/stop-netty @netty-server)
    (reset! netty-server nil)))

(daemons/daemonize "netty" start-netty stop-netty)
