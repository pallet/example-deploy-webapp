(defproject webapp "0.1.0-SNAPSHOT"
  :description "Example of deploying a web app with pallet"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [ring/ring-jetty-adapter "1.1.6"]
                 [compojure "1.1.3"]]
  :plugins [[lein-ring "0.8.5"]]
  :ring {:handler webapp.core/routes})
