;;; Pallet project configuration file
(require '[pallet.crate.java :as java]
         '[pallet.crate.runit :as runit]
         '[pallet.crate.app-deploy :as app-deploy])

(def webserver
  (group-spec "webserver"
    :extends [(java/server-spec {})
              (runit/server-spec {})
              (app-deploy/server-spec
               {:artifacts
                {:from-lein
                 [{:project-path "target/webapp-%s-standalone.jar"
                   :path "webapp.jar"}]}
                :run-command "java -jar /opt/my-webapp/webapp.jar"}
               :instance-id :my-webapp)]))

(defproject webapp
  :provider {:aws-ec2
             {:node-spec
              {:image {:os-family :ubuntu
                       :os-version-matches "12.04"
                       :os-64-bit true
                       :image-id "us-east-1/ami-e2861d8b"}
               :network {:incoming-ports [22 3000]}}}}
  :groups [webserver])
