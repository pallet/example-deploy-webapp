;;; Pallet project configuration file
(require '[pallet.crate.java :as java]
         '[pallet.crate.runit :as runit])

(def webserver
  (group-spec "webserver"
    :extends [(java/server-spec {})
              (runit/server-spec {})]))

(defproject webapp
  :groups [webserver])
