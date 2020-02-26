;; Copyright (c) Cognitect, Inc.
;; All rights reserved.

(ns datomic.ion.starter.lambdas
  (:require
   [clojure.data.json :as json]
   [datomic.client.api :as d]
   [datomic.ion.starter :as starter]
   [datomic.ion.starter.edn :as edn]))

(defn get-schema
  "Lambda ion that returns database schema."
  [_]
  (-> (starter/get-db)
      starter/get-schema
      edn/write-str))

(defn get-items-by-type
  "Lambda ion that returns items matching type."
  [{:keys [input]}]
  (-> (starter/get-db)
      (starter/get-items-by-type (-> input json/read-str keyword)
                             [:inv/sku :inv/size :inv/color])
      edn/write-str))

(defn ensure-sample-dataset
  "Lambda ion that creates database and transacts sample data."
  [_]
  (-> (starter/ensure-sample-dataset)
      edn/write-str))