;; The cheapest and least secure login that will ever be!

(ns ewback.login
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



(defn verify-creds
  [request]


  (let [request (body-params/form-parser request)

        required-keys #{"email" "pass"}
        optional-keys #{}
        accepted-keys (clojure.set/union required-keys optional-keys)

        filtered-form (-> request :form-params (select-keys accepted-keys))

        missing-keys (-> required-keys (clojure.set/difference (keys filtered-form)))]

    (cond
      (-> missing-keys empty? not) (ring-resp/response (str "Missing keys " missing-keys))
      :else (let [rs (d/q '[:find (pull ?e [* {:user/categorie [:db/ident]}]) .
                                    :in $ ?nom ?mdp
                                    :where
                                    [?e :user/courriel ?nom]
                                    [?e :user/motdepasse ?mdp]] (d/db p/conn) (filtered-form "email") (filtered-form "pass"))]

              (if (nil? rs)
                (bootstrap/json-response {:error :user-not-found})
                (bootstrap/json-response  rs))))




    ))

