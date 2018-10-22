;; Copyright (c) Cognitect, Inc.
;; All rights reserved.

(ns datomic.ion.starter
  (:require
   [clojure.data.json :as json]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.pprint :as pp]
   [datomic.client.api :as d]
   [datomic.ion.lambda.api-gateway :as apigw]))

(def get-client
  "This function will return a local implementation of the client
interface when run on a Datomic compute node. If you want to call
locally, fill in the correct values in the map."
  (memoize #(d/client {:server-type :ion
                       :region "us-east-1"
                       :system "stu-8"
                       :query-group "stu-8"
                       :endpoint "http://entry.stu-8.us-east-1.datomic.net:8182/"
                       :proxy-port 8182})))

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
  (let [setup-var (resolve setup-sym)
        client (get-client)]
    (when-not setup-var
      (anomaly! :not-found (str "Could not resolve " setup-sym)))
    (d/create-database client {:db-name db-name})
    (let [conn (d/connect client {:db-name db-name})
          db (d/db conn)]
      (setup-var conn)
      conn)))

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

;; Ions ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn tutorial-schema-handler
  "Web handler that returns the schema for datomic-docs-tutorial"
  [{:keys [headers body]}]
  {:status 200
   :headers {"Content-Type" "application/edn"} 
   :body (-> (get-connection) d/db schema pp-str)})

(def get-tutorial-schema
  "API Gateway web service ion for tutorial-schema-handler."
  (apigw/ionize tutorial-schema-handler))

(defn echo
  "Lambda ion that simply echoes its input"
  [{:keys [context input]}]
  input)

(defn items-by-type*
  "Returns info about items matching type"
  [db type]
  (d/q '[:find ?sku ?size ?color
         :in $ ?type
         :where
         [?e :inv/type ?type]
         [?e :inv/sku ?sku]
         [?e :inv/size ?size]
         [?e :inv/color ?color]
         #_[(datomic.ion.starter/feature-item? $ ?e) ?featured]]
       db type))

(defn items-by-type
  "Lambda ion that returns sample database items matching type."
  [{:keys [input]}]
  (-> (items-by-type* (d/db (get-connection))
                      (-> input json/read-str keyword))
      pp-str))

(defn read-edn
  [input-stream]
  (some-> input-stream io/reader (java.io.PushbackReader.) edn/read))

(defn items-by-type-web*
  "Lambda ion that returns sample database items matching type."
  [{:keys [headers body]}]
  (let [type (some-> body read-edn)]
    (if (keyword? type)
      {:status 200
       :headers {"Content-Type" "application/edn"} 
       :body (-> (items-by-type* (d/db (get-connection)) type)
                 pp-str)}
      {:status 400
       :headers {}
       :body "Expected a request body keyword naming a type"})))

(def items-by-type-web
  "API Gateway web service ion for items-by-type"
  (apigw/ionize items-by-type-web*))

(defn create-item
  "Transaction fn that creates data to make a new item"
  [db sku size color type]
  [{:inv/sku sku
    :inv/color (keyword color)
    :inv/size (keyword size)
    :inv/type (keyword type)}])

(defn add-item
  "Lambda ion that adds an item, returns database t."
  [{:keys [input]}]
  (let [args (-> input json/read-str)
        conn (get-connection)
        tx [(list* 'datomic.ion.starter/create-item args)]
        result (d/transact conn {:tx-data tx})]
    (pp-str {:t (-> result :db-after :t)})))

(defn feature-item?
  "Query ion exmaple. This predicate matches entities that
should be featured in a promotion."
  [db e]
  ;;  While this particular predicate could also be implemented as
  ;; additional clauses in query, your own programs can do anything
  ;; they want here!
  (let [{:keys [inv/color inv/size inv/type]} (d/pull db {:eid e :selector [:inv/color :inv/size :inv/type]})]
    (and (= (:db/ident color) :green)
         (= (:db/ident size) :xlarge)
         (= (:db/ident type) :hat))))

