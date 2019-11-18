;; Copyright (c) Cognitect, Inc.
;; All rights reserved.

(ns datomic.ion.starter.attributes)

(defn valid-sku?
  [s]
  (boolean (re-matches #"SKU-(\d+)" s)))

