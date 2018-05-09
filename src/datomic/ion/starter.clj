;; Copyright (c) Cognitect, Inc.
;; All rights reserved.

(ns datomic.ion.starter
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.pprint :as pp]
   [datomic.client.api :as d]
   [datomic.java.io.bbuf :as bbuf]
   [datomic.ion.lambda.api-gateway :as apigw]))

(def client (d/client {:server-type :ion
                       :region "us-east-1"
                       :system "stu-8"
                       :query-group "stu-8"
                       :endpoint "http://entry.stu-8.us-east-1.datomic.net:8182/"
                       :proxy-port 8182}))

(defn- anom-map
  [category msg]
  {:cognitect.anomalies/category (keyword "cognitect.anomalies" (name category))
   :cognitect.anomalies/message msg})

(defn- anomaly!
  ([name msg]
     (throw (ex-info msg (anom-map name msg))))
  ([name msg cause]
     (throw (ex-info msg (anom-map name msg) cause))))

(defn ensure-dataset
  "Ensure that a database named db-name exists, running setup-fn
against a connection. Returns connection"
  [db-name setup-sym]
  (require (symbol (namespace setup-sym)))
  (let [setup-var (resolve setup-sym)]
    (when-not setup-var
      (anomaly! :not-found (str "Could not resolve " setup-sym)))
    (d/create-database client {:db-name db-name})
    (let [conn (d/connect client {:db-name db-name})
          db (d/db conn)]
      (setup-var conn)
      conn)))

(defn inc-attr
  "Transaction function that increments the value of entity's card-1
attr by amount, treating a missing value as 0."
  [db entity attr amount]
  (let [m (d/pull db {:eid entity :selector [:db/id attr]})]
    [[:db/add (:db/id m) attr (+ (or (attr m) 0) amount)]]))

(defn modes
  "Query aggregate fn that returns the set of modes for a collection."
  [coll]
  (->> (frequencies coll)
       (reduce
        (fn [[modes ct] [k v]]
          (cond
           (< v ct)  [modes ct]
           (= v ct)  [(conj modes k) ct]
           (> v ct) [#{k} v]))
        [#{} 2])
       first))

(defn pp-str
  [x]
  (binding [*print-length* nil
            *print-level* nil]
    (with-out-str (pp/pprint x))))

(defn get-connection
  []
  (ensure-dataset "datomic-docs-tutorial"
                  'datomic.ion.starter.examples.tutorial/load-dataset))

(defn schema
  "Returns a data representation of db schema."
  [db]
  (->> (d/pull db '{:eid 0 :selector [{:db.install/attribute [*]}]})
       :db.install/attribute
       (map #(update % :db/valueType :db/ident))
       (map #(update % :db/cardinality :db/ident))))

(defn tutorial-schema-handler
  "Web handler that returns the schema for datomic-docs-tutorial"
  [{:keys [headers body]}]
  {:status 200
   :headers {"Content-Type" "application/edn"} 
   :body (-> (get-connection) d/db schema pp-str)})

(def get-tutorial-schema
  "API Gateway Ion for tutorial-schema-handler."
  (apigw/ionize tutorial-schema-handler))

(defn echo
  [{:keys [context input]}]
  input)


