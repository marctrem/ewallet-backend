(ns ewback.customers
  (:require [ring.util.response :as ring-resp]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http :as bootstrap]
            [datomic.api :as d]

            [ewback.peer :as p])
  (:import (java.util Date)))


(defn list
  [request]
  (let [ev-id (-> request :path-params :ev-id)]

    (->
      (cond
        (-> request :query-params (contains? :card-id))
        (d/q '[:find [(pull ?e [*]) ...]
               :in $ ?card-id ?current-date
               :where
               [?e :client/idNFC ?card-id]
               [?ev :evenement/clients ?e]
               [?ev :evenement/dateDebut ?dd]
               [?ev :evenement/dateFin ?df]
               [(< ?dd ?current-date)]
               [(> ?df ?current-date)]]

             (d/db p/conn) (-> request :query-params :card-id) (Date.))

        :else
        (d/q '[:find (pull ?clients [*])
               :in $ ?ev-id
               :where
               [?ev-id :evenement/clients ?clients]]
             (d/db p/conn) (Long/parseLong ev-id)))

      bootstrap/json-response))
  )


(defn create
  [request]

  (let [request ((body-params/custom-json-parser) request)

        required-keys #{:organisation/nom :organisation/actif}
        optional-keys #{}
        accepted-keys (clojure.set/union required-keys optional-keys)
        filtered-form (-> request :json-params (select-keys accepted-keys))
        missing-keys (-> required-keys (clojure.set/difference (keys filtered-form)))]

    (cond
      (-> missing-keys empty? not) (ring-resp/response (str "Missing keys " missing-keys))
      :else (let [new-org (-> filtered-form
                              (assoc :db/id #db/id[:db.part/user]))

                  query [new-org]

                  tx (d/transact p/conn query)
                  tx-res (try @tx
                              (catch Exception ise (-> ise .getCause .getMessage)))]

              (ring-resp/response (with-out-str (clojure.pprint/pprint tx-res)))))))


(defn display
  [request]

  (let [org-name (-> request :path-params :org-name)]
    (bootstrap/json-response
      (d/q '[:find (pull ?e [:organisation/nom
                             :organisation/actif]) .
             :in $ ?org-name
             :where [?e :organisation/nom ?org-name]]
           (d/db p/conn) org-name))))

(defn display-by-wallet-id
  [request]

  (let [org-name (-> request :path-params :org-name)
        wallet-id (-> request :path-params :wallet-id)])

  )


