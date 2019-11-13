;; Copyright (c) Cognitect, Inc.
;; All rights reserved.

(ns datomic.ion.starter.http
  (:require
   [clojure.java.io :as io]
   [datomic.ion.starter :as starter]
   [datomic.ion.starter.edn :as edn]
   [datomic.ion.lambda.api-gateway :as apigw]))

(defn edn-response
  [body]
  {:status 200
   :headers {"Content-Type" "application/edn"}
   :body body})

(defn get-items-by-type
  "Web handler that returns info about items matching type."
  [{:keys [headers body]}]
  (let [type (some-> body edn/read)]
    (if (keyword? type)
      (-> (starter/get-db)
          (starter/get-items-by-type type [:inv/sku :inv/size :inv/color])
          edn/write-str
          edn-response)
      {:status 400
       :headers {}
       :body "Expected a request body keyword naming a type"})))

(def get-items-by-type-lambda-proxy
  (apigw/ionize get-items-by-type))


