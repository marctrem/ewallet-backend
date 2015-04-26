(ns ewback.articles
  (:require [ring.util.response :as ring-resp]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http :as bootstrap]
            [datomic.api :as d]

            [ewback.peer :as p])
  (:import (java.util Date)))


(defn list
  [request]
  (let []
    (->
        (d/q '[:find [(pull ?e [*]) ...]
               :in $ ?org-nom
               :where
               [?org :organisation/nom ?org-nom]
               [?org :organisation/articles ?e]
               ]
             (d/db p/conn) (-> request :path-params :org-name))


      bootstrap/json-response)))


