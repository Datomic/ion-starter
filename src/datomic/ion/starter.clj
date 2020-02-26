;; Copyright (c) Cognitect, Inc.
;; All rights reserved.

(ns datomic.ion.starter
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [datomic.client.api :as d]
    [datomic.ion.starter.utils :as utils]))

(def database-name "datomic-docs-tutorial")

(def get-client
  "Return a shared client. Set datomic/ion/starter/config.edn resource
  before calling this function."
  (memoize #(if-let [r (io/resource "datomic/ion/starter/config.edn")]
              (d/client (edn/read-string (slurp r)))
              (throw (RuntimeException. "You need to add a resource datomic/ion/starter/config.edn with your connection config")))))

(defn get-connection
  "Get shared connection."
  []
  (utils/with-retry #(d/connect (get-client) {:db-name database-name})))

(defn- ensure-dataset
  "Ensure that a database named db-name exists, running setup-fn
  against the shared connection. Returns result of setup-fn"
  [db-name setup-sym]
  (require (symbol (namespace setup-sym)))
  (let [setup-var (resolve setup-sym)
        client (get-client)]
    (when-not setup-var
      (utils/anomaly! :not-found (str "Could not resolve " setup-sym)))
    (d/create-database client {:db-name db-name})
    (let [conn (get-connection)]
      (setup-var conn))))

(defn ensure-sample-dataset
  "Creates db (if necessary) and transacts sample data."
  []
  (utils/with-retry #(ensure-dataset database-name 'datomic.ion.starter.inventory/load-dataset)))

(defn get-db
  "Returns current db value from shared connection."
  []
  (d/db (get-connection)))

(defn get-schema
  "Returns a data representation of db schema."
  [db]
  (->> (d/pull db '{:eid 0 :selector [{:db.install/attribute [*]}]})
       :db.install/attribute
       (remove (fn [m] (str/starts-with? (namespace (:db/ident m)) "db")))
       (map #(update % :db/valueType :db/ident))
       (map #(update % :db/cardinality :db/ident))))

(defn get-items-by-type
  "Returns pull maps describing all items matching type"
  [db type pull-expr]
  (d/q '[:find (pull ?e pull-expr)
         :in $ ?type pull-expr
         :where [?e :inv/type ?type]]
       db type pull-expr))
