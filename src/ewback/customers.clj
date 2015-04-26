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
        (d/q '[:find [(pull ?e [* {:client/transact
                                   [* {:transaction/entrees
                                       [* {:articleTransaction/article
                                           [*]}]}]}]) ...]
               :in $ ?card-id ?current-date
               :where
               [?e :client/idNFC ?card-id]
               ;[?ev :evenement/clients ?e]
               ;[?ev :evenement/dateDebut ?dd]
               ;[?ev :evenement/dateFin ?df]
               ;[(< ?dd ?current-date)]
               ;[(> ?df ?current-date)]
               ]

             (d/db p/conn) (-> request :query-params :card-id) (Date.))

        :else
        (d/q '[:find (pull ?clients [*])
               :in $ ?ev-id
               :where
               [?ev-id :evenement/clients ?clients]]
             (d/db p/conn) (Long/parseLong ev-id)))

      bootstrap/json-response))
  )

(defn update
  [request]
  (let [request ((body-params/custom-json-parser) request)
        ev-id (-> request :path-params :ev-id)

        resource-iden-key :client/courriel
        updatable-keys #{:client/nom :client/courriel :client/codePostal :client/balance :client/idNFC}

        difference-with-provided-keys (-> request :json-params keys set (clojure.set/difference updatable-keys))]

    (-> (cond
          (-> difference-with-provided-keys empty? not)
          {:error true :message (str "Unknown key(s) " difference-with-provided-keys)}

          :else (let [add-to-id (let [rs (d/q '[:find [?e ...]
                                                 :in $ ?courriel
                                                 :where
                                                 [?e :client/courriel ?courriel]] (d/db p/conn) (-> request :json-params :client/courriel))]
                                  (if (empty? rs)
                                    #db/id[:db.part/user]
                                    (first rs)))
                      query (-> request :json-params
                                (assoc :db/id add-to-id))
                      tx-res @(d/transact p/conn [query])]

                  {:error false})
          )
        bootstrap/json-response)))

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

(defn new-command
  [request]
  (let [request ((body-params/custom-json-parser) request)
        form (reduce #(assoc %1 (Long/parseLong (name (key %2))) (val %2)) {} (-> request :json-params))


        transaction-query {:db/id #db/id[:db.part/user -1]
               :client/_transact [:client/courriel (-> request :path-params :client-courriel)]}

        amount (d/q '[:find ?amount .
                      :in $ ?courriel
                      :where
                      [?client :client/courriel ?courriel]
                      [?client :client/balance ?amount]
                      ] (d/db p/conn) (-> request :path-params :client-courriel))

        articles (d/q '[:find [(pull ?e [:db/id :article/prix]) ...]
                        :in $ ?org-nom
                        :where
                        [?org :organisation/nom ?org-nom]
                        [?org :organisation/articles ?e]]
                      (d/db p/conn) (-> request :path-params :org-name))
        articles (reduce #(assoc %1 (%2 :db/id) (%2 :article/prix)) {} articles)
        cout (reduce #(+ %1 (* (articles (key %2)) (val %2))) 0 form)

        query (reduce #(conj %1 {:db/id #db/id[:db.part/user]
                                 :transaction/_entrees #db/id[:db.part/user -1]
                                 :articleTransaction/article (key %2)
                                 :articleTransaction/quantite (val %2)}) [transaction-query] form)

        query (conj query {:db/id [:client/courriel  (-> request :path-params :client-courriel)]
                           :client/balance (- amount cout)})

        ]

    (d/transact p/conn query)



    (bootstrap/json-response {:error false})))



