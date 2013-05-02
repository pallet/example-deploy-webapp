# Deploy a Ring Based WebApp with an Embedded Jetty Server using Pallet

Running a web application is perhaps the most common reason for wanting to
deploy a server.  Using the `app-deploy-crate`, we can achieve this simply using
pallet.

## Creating a Web Server
[project.clj](step-1-webapp/project.clj) &#xb7;
[core.clj](step-1-webapp/src/webapp/core.clj)

To create our simple web server, we'll run `lein new webapp`, and edit the
generated `project.clj` file to add the `ring-jetty-adapter`, `compojure` and
the `lein-ring` plugin.

```clj
(defproject webapp "0.1.0-SNAPSHOT"
  :description "Example of deploying a web app with pallet"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [ring/ring-jetty-adapter "1.1.6"]
                 [compojure "1.1.3"]]
  :plugins [[lein-ring "0.8.5"]]
  :ring {:handler webapp.core/routes})
```

We'll create a simple "Hello World" page in `src/webapp/core.clj`.

```clj
(ns webapp.core
  (:use [compojure.core :only (defroutes GET)]
        [ring.adapter.jetty :as ring]))

(defroutes routes
  (GET "/" [] "<h2>Hello World</h2>"))

(defn -main []
  (run-jetty routes {:port 8080 :join? false}))
```

We can check this works by running `lein ring server`.

## Adding Pallet
[project.clj](step-2-with-pallet/project.clj) &#xb7;
[pallet.clj](step-2-with-pallet/pallet.clj) &#xb7;

To deploy using pallet, we'll add the pallet plugin for leiningen, and the
pallet dependencies.  The pallet dependencies are added via the `pallet`
profile, so they do not interfere with the base project dependencies.

```clj
(defproject webapp "0.1.0-SNAPSHOT"
  :description "Example of deploying a web app with pallet"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [ring/ring-jetty-adapter "1.1.6"]
                 [compojure "1.1.3"]]
  :plugins [[lein-ring "0.8.5"]
            [com.palletops/pallet-lein "0.6.0-beta.9"]]
  :ring {:handler webapp.core/routes}
  :profiles {:pallet {:dependencies
                      [[com.palletops/pallet "0.8.0-beta.9"]
                       [com.palletops/java-crate "0.8.0-beta.4"]
                       [com.palletops/runit-crate "0.8.0-alpha.1"]
                       [com.palletops/app-deploy-crate "0.1.0-SNAPSHOT"]
                       [ch.qos.logback/logback-classic "1.0.9"]
                       [org.slf4j/jcl-over-slf4j "1.7.3"]]}
             :exclusions [commons-logging]})
```

Here we have added dependencies on pallet, and three of its crates, and some
logging libraries.

Now we have the dependencies in place, we can add a `pallet.clj` file (at the
same directory level as the leiningen `project.clj` file).  We'll start by just
creating a configuration to install java and runit, to supervise our webapp
server process.

```clj
;;; Pallet project configuration file
(require '[pallet.crate.java :as java]
         '[pallet.crate.runit :as runit])

(def webserver
  (group-spec "webserver"
    :extends [(java/server-spec {})
              (runit/server-spec {})]))

(defproject webapp
  :groups [webserver])
```

Here we have defined a single `group-spec`, which is the specification for a
virtual machine, which will have java and runit installed on it, and we define
our webapp project as being mad up of this single `group-spec`.

At this point we have a simple definition of a virtual machine, which we'll now
run.

## Running on AWS EC2
[project.clj](step-3-with-ec2/project.clj) &#xb7;
[pallet.clj](step-3-with-ec2/pallet.clj) &#xb7;

To run this on Amazon EC2, we'll need to add some dependencies and tell pallet
our EC2 credentials.

The dependencies we need are for the pallet ec2 provider, and the jclouds
providers that it uses.

The extra dependencies for the `:pallet` profile are:

```clj
[org.cloudhoist/pallet-jclouds "1.5.2"]
[org.jclouds/jclouds-all "1.5.5"]
[org.jclouds.provider/jclouds-slf4j "1.5.5"]
[org.jclouds.provider/jclouds-sshj "1.5.5"]
```

We also need to tell pallet what sort of nodes we want.  We can do this in the
`pallet.clj` file.

```clj
(defproject webapp
  :provider {:aws-ec2
             {:node-spec
              {:image {:os-family :ubuntu
                       :os-version-matches "12.04"
                       :os-64-bit true
                       :image-id "us-east-1/ami-e2861d8b"}
               :network {:incoming-ports [22 3000]}}}}
  :groups [webserver])
```

To tell pallet the credentials, we can run the add-service task in the lein
pallet plugin:

```
lein pallet add-service aws aws-ec2 YOUR-ACCESS-KEY YOUR-SECRET-KEY
```

This will create a `~/.pallet/services/aws.clj` file containing the keys.

We can now start a virtual machine:

```
lein pallet up --phases install
```

## Adding a Deploy from Leiningen Built Jars
[project.clj](step-4-with-deploy-from-lein/project.clj) &#xb7;
[pallet.clj](step-4-with-deploy-from-lein/pallet.clj) &#xb7;

To actually deploy our webapp, we now add in the `app-deploy-crate` into the
`pallet.clj` configuration.

```clj
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
```

This tells pallet that we want to deploy a single artifact from the leiningen
built target `target/webapp-%s-standalone.jar` file, where the "%s" will be
replaced by the project version, to `/opt/my-webapp/webapp.jar`, and create a
`runit` service to run it.

We can now deploy using:

```
lein do uberjar, pallet up --phases configure,deploy
```

## Adding a Deploy from a Maven Repository
[project.clj](step-5-with-deploy-from-maven-repo/project.clj) &#xb7;
[pallet.clj](step-5-with-deploy-from-maven-repo/pallet.clj) &#xb7;

Deploying from our local build directory is useful in development, but in
production we may wish to deploy from released, tested jars in a maven
repository.

We can do this by adding a `:from-maven-repo` resolver to our app-deploy
configuration.

```clj
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
                   :path "webapp.jar"}]
                 :from-maven-repo
                 [{:coord '[webapp "0.1.0" :classifier "standalone"]
                   :path "example.jar"}]}
                :run-command "java -jar /opt/my-webapp/webapp.jar"}
               :instance-id :my-webapp)]))
```

Assuming we have a "0.1.0" release, deployed with `lein deploy`, we can use that
artifact with:

```
lein pallet up --phases deploy :from-maven-repo
```

By default, the deploy will use the `:repositories` defined in your leiningen
configuration to resolve the artifacts.
