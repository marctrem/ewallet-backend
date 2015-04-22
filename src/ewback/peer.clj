(ns ewback.peer
  (:require [datomic.api :as d])

  (:gen-class))

(def uri "datomic:free://172.17.0.2:4334/ew")
(def schema-tx (-> "resources/ew-schema.edn" slurp read-string))
(def data-tx (-> "resources/ew-seed-data.edn" slurp read-string))

(d/create-database uri)
(def conn  (d/connect uri))

(defn create-db []
  (d/create-database uri)
  @(d/transact conn schema-tx))
(defn insert-demo-data [] @(d/transact conn data-tx))




