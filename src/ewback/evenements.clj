(ns ewback.evenements
  (:require [ring.util.response :as ring-resp]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http :as bootstrap]
            [io.pedestal.log :as log]
            [clj-time.format :as tf]

            [clojure.walk :as walk]
            [datomic.api :as d]

            [ewback.peer :as p])
  (:import (java.util Date)
           (java.text SimpleDateFormat)))


(defn list
  [request]

  (bootstrap/json-response
    (d/q '[:find [(pull ?e [*]) ...]
           :in $ ?org-name
           :where
           [?o :organisation/evenements ?e]
           [?o :organisation/nom ?org-name]]
         (d/db p/conn) (-> request :path-params :org-name)))
  )


(defn create
  [request]



  (let [request ((body-params/custom-json-parser) request)



        required-keys #{:evenement/nom :evenement/dateDebut :evenement/dateFin}
        optional-keys #{}
        accepted-keys (clojure.set/union required-keys optional-keys)

        filtered-form (-> request :json-params (select-keys accepted-keys))

        ; Todo: Have a better json parser that can convert upon key.
        filtered-form (-> filtered-form
                          (assoc :evenement/dateDebut (.toDate (tf/parse (tf/formatters :year-month-day) (:evenement/dateDebut filtered-form))))
                          (assoc :evenement/dateFin (.toDate (tf/parse (tf/formatters :year-month-day) (:evenement/dateFin filtered-form)))))

        missing-keys (-> required-keys (clojure.set/difference (keys filtered-form)))]

    (cond
      (-> missing-keys empty? not) (ring-resp/response (str "Missing keys " missing-keys))
      :else (let [new-user (-> filtered-form
                               (assoc :db/id #db/id[:db.part/user])
                               (assoc :organisation/_evenements [:organisation/nom (-> request :path-params :org-name)]))

                  query [new-user]

                  tx (d/transact p/conn query)
                  tx-res (try @tx
                              (catch Exception ise (-> ise .getMessage)))]

              (ring-resp/response (with-out-str (clojure.pprint/pprint tx-res)))))




    ))

(defn display
  [request]

  (let [org-name (-> request :path-params :org-name)]
    (bootstrap/json-response
      (d/q '[:find (pull ?e [:organisation/nom
                             :organisation/actif]) .
             :in $ ?org-name
             :where [?e :organisation/nom ?org-name]]
           (d/db p/conn) org-name))))


(defn current-event
  [request]
  (bootstrap/json-response {:status :ok}))