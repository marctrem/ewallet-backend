(ns ewback.users
  (:require [ring.util.response :as ring-resp]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http :as bootstrap]
            [clojure.walk :as walk]
            [datomic.api :as d]

            [ewback.peer :as p]))


(defn tuplify
  "Returns a vector of the vals at keys ks in map."
  [m ks]
  (mapv #(get m %) ks))

(defn list
  [request]

  (let [org-name (-> request :path-params :org-name)]

    (bootstrap/json-response
      (d/q '[:find [(pull ?e  [:db/id :user/nom :user/courriel {:user/categorie [:db/ident]}]) ...]
             :in $ ?org
             :where [?org-id :organisation/users ?e]]
           (d/db p/conn) org-name))))


(defn create
  [request]

  (let [request ((body-params/custom-json-parser) request)



        required-keys #{:user/courriel :user/nom :user/motdepasse :user/categorie :user/actif}
        optional-keys #{}
        accepted-keys (clojure.set/union required-keys optional-keys)

        filtered-form (-> request :json-params (select-keys accepted-keys))

        missing-keys (-> required-keys (clojure.set/difference (keys filtered-form)))]

    (cond
      (-> missing-keys empty? not) (ring-resp/response (str "Missing keys " missing-keys))
      :else (let [new-user (assoc filtered-form :db/id #db/id[:db.part/user])

                  new-user (-> filtered-form
                               (assoc :db/id #db/id[:db.part/user])
                               (assoc :organisation/_users [:organisation/nom (-> request :path-params :org-name)]))

                  query [new-user]

                  tx (d/transact p/conn query)
                  tx-res (try @tx
                              (catch Exception ise (-> ise .getCause .getMessage)))]

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

