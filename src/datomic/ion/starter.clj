;; Copyright (c) Cognitect, Inc.
;; All rights reserved.

(ns datomic.ion.starter
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [cognitect.anomalies :as anom]
   [datomic.client.api :as d]))

(def get-client
  "This function will return a local implementation of the client
interface when run on a Datomic compute node. If you want to call
locally, fill in the correct values in the map."
  (memoize #(if-let [r (io/resource "datomic/ion/starter/config.edn")]
              (d/client (edn/read-string (slurp r)))
              (throw (RuntimeException. "You need to add a resource datomic/ion/starter/config.edn with your connection config")))))

(defn- anom-map
  "Helper for anomaly!"
  [category msg]
  {::anom/category (keyword "cognitect.anomalies" (name category))
   ::anom/message msg})

(defn- anomaly!
  ([name msg]
     (throw (ex-info msg (anom-map name msg))))
  ([name msg cause]
     (throw (ex-info msg (anom-map name msg) cause))))

(defn- ensure-dataset
  "Ensure that a database named db-name exists, running setup-fn
against a connection. Returns connection"
  [db-name setup-sym]
  (require (symbol (namespace setup-sym)))
  (let [setup-var (resolve setup-sym)
        client (get-client)]
    (when-not setup-var
      (anomaly! :not-found (str "Could not resolve " setup-sym)))
    (d/create-database client {:db-name db-name})
    (let [conn (d/connect client {:db-name db-name})
          db (d/db conn)]
      (setup-var conn)
      conn)))

(defn get-connection
  "Returns shared connection."
  []
  (ensure-dataset "datomic-docs-tutorial"
                  'datomic.ion.starter.inventory/load-dataset))

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




