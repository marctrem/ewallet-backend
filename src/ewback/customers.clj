(ns ewback.customers
  (:require [ring.util.response :as ring-resp]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http :as bootstrap]
            [datomic.api :as d]

            [ewback.peer :as p]))


(defn list
  [request]

  (bootstrap/json-response
    (d/q '[:find [?e ...]
           :in $ ?ev
           :where
           [?e :client/nom _]
           [?ev :evenement/clients ?e]

           ]
         (d/db p/conn)))
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


