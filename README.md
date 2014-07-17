# spray-basic-authentication-cluster-singleton
Spray-basic-authentication-cluster-singleton, a lot of words, secures a spray service with Basic Authentication. 
The example secures the '/secure' context, and not the '/api' context. Application state, the allowed users that may 
access the '/secure' context, is stored as Actor state. Actor state is made persistent using the akka extension
[akka-persistence-cassandra](https://github.com/krasserm/akka-persistence-cassandra) extension by [Martin Krasser](https://github.com/krasserm).
Actor state will therefore be managed by [Apache Cassandra](http://cassandra.apache.org/), a distributed high-performance 
and high-availablility datastore.
 
This example, will, in contrast to [spray-basic-authentication-cassandra](https://github.com/dnvriend/spray-basic-authentication-cassandra)
use akka-cluster to store application state using the [Cluster Singleton](http://doc.akka.io/docs/akka/2.3.4/contrib/cluster-singleton.html#cluster-singleton)
Pattern. Using this pattern, there will be only one instance of the SecurityService and SecurityServiceView in the cluster. 
                                   
# Docker
This example can be run using [Docker](http://docker.io) and I would strongly advice using Docker and just take a few 
hours to work through the guide on how to use this *great* piece of software.

## Run the example
The example is highly 'clusterized', meaning we must run a small cassandra cluster and the example as a cluster. Using scripts, this will become
much easier. The configuration I have based upon 
[Michael Hamrah's blog post - Running an Akka Cluster with Docker Containers](http://blog.michaelhamrah.com/2014/03/running-an-akka-cluster-with-docker-containers/).

We must first run the cassandra cluster and then the example as a cluster using scripts

## Launching cassandra
To launch cassandra use the following script:

    $ scripts/docker-cassandra-run.sh
    
This will launch three cassandra instances:

    $ sudo docker ps
    
    CONTAINER ID        IMAGE                     COMMAND             CREATED             STATUS              PORTS                                                                           NAMES
    942a0caa77a1        poklet/cassandra:latest   start 172.17.0.2    3 seconds ago       Up 3 seconds        22/tcp, 61621/tcp, 7000/tcp, 7001/tcp, 7199/tcp, 8012/tcp, 9042/tcp, 9160/tcp   cass3
    3e12b2e68c87        poklet/cassandra:latest   start 172.17.0.2    3 seconds ago       Up 3 seconds        22/tcp, 61621/tcp, 7000/tcp, 7001/tcp, 7199/tcp, 8012/tcp, 9042/tcp, 9160/tcp   cass2
    7a7235b8b693        poklet/cassandra:latest   start               3 seconds ago       Up 3 seconds        22/tcp, 61621/tcp, 7000/tcp, 7001/tcp, 7199/tcp, 8012/tcp, 9042/tcp, 9160/tcp   cass1

## Launching the example cluster

    $ scripts/docker-run.sh
    
This will launch three instances of the spray-basic-authentication cluster singleton
    
    $ sudo docker ps   
     
    CONTAINER ID        IMAGE                                COMMAND                CREATED              STATUS              PORTS                                                                           NAMES
    6c77eb740306        dnvriend/spray-ba-singleton:latest   /appl/start /bin/bas   40 seconds ago       Up 39 seconds       0.0.0.0:49237->2552/tcp, 0.0.0.0:49238->8080/tcp                                node2
    b5f568874bd3        dnvriend/spray-ba-singleton:latest   /appl/start /bin/bas   40 seconds ago       Up 39 seconds       0.0.0.0:49235->2552/tcp, 0.0.0.0:49236->8080/tcp                                node1
    966205878d56        dnvriend/spray-ba-singleton:latest   /appl/start /bin/bas   41 seconds ago       Up 40 seconds       0.0.0.0:49233->2552/tcp, 0.0.0.0:49234->8080/tcp                                node1/seed,node2/seed,seed
    942a0caa77a1        poklet/cassandra:latest              start 172.17.0.2       About a minute ago   Up About a minute   22/tcp, 61621/tcp, 7000/tcp, 7001/tcp, 7199/tcp, 8012/tcp, 9042/tcp, 9160/tcp   cass3,node1/cas3,node1/seed/cas3,node2/cas3,node2/seed/cas3,seed/cas3
    3e12b2e68c87        poklet/cassandra:latest              start 172.17.0.2       About a minute ago   Up About a minute   22/tcp, 61621/tcp, 7000/tcp, 7001/tcp, 7199/tcp, 8012/tcp, 9042/tcp, 9160/tcp   cass2,node1/cas2,node1/seed/cas2,node2/cas2,node2/seed/cas2,seed/cas2
    7a7235b8b693        poklet/cassandra:latest              start                  About a minute ago   Up About a minute   22/tcp, 61621/tcp, 7000/tcp, 7001/tcp, 7199/tcp, 8012/tcp, 9042/tcp, 9160/tcp   cass1,node1/cas1,node1/seed/cas1,node2/cas1,node2/seed/cas1,seed/cas1

## Seeds and nodes
The oldest node is called the master of the cluster, and that is where the Cluster Singleton Actor will be run. When that node  
will crash or there is no communication, another node will be master of the cluster and that is where the Cluster Singleton will be
re-created. Because we are using Akka persistence and Apache Cassandra, the Actor state will be preserved and the application will
keep running, pretty cool! 

Seeds are just nodes that know about the cluster. New nodes will use seed to get information about the cluster. All nodes of the cluster are
just that, instances that work together in the cluster. The oldest node is called the 'leader' and has special rights in the cluster. For more
information read the [Akka Cluster Specification](http://doc.akka.io/docs/akka/2.3.4/common/cluster.html)

## Log output
To view the log output:

    $ scripts/docker-log-seed.sh
    $ scripts/docker-log-node1.sh

## The AngularJS admin screen
In this example, there are multiple instances of the admin application, but the state is the cluster singleton. On which
node the singleton runs is to no concern of ours, only that we can access the state. Akka-cluster takes care of that for
us. Looking at the 'sudo docker ps' command above, you'll see that in this example, I have multiple options to access the 
admin screen. The ports may be different on your system.

Point the browser to one of the following url (change the port to your mapped port):

    http://192.168.99.99:49234/web/index.html    
    http://192.168.99.99:49236/web/index.html    
    http://192.168.99.99:49238/web/index.html    
    
The Basic Authentication secured context is:
    
    http://192.168.99.99:49234/secure
    http://192.168.99.99:49236/secure
    http://192.168.99.99:49238/secure
 
## Stopping and removing all the images    
To stop and remove all the images from your system, use the following scripts:

    $ scripts/docker-remove.sh
    $ scripts/docker-cassandra-remove.sh
    
# Akka Cluster Magic
Lets see some cluster magic in action! 

## Single Actor in the cluster 
Access the admin screen using the first URL above and create a user. Next navigate
to the second and third URL and ensure that the user is already there! See, the nodes know where to find the cluster
singleton instance!

## Moving the Cluster Singleton
Next lets move the cluster singleton to another node. This can only be done by killing the node where the cluster singleton runs.
Most likely it will be the node called 'seed', the first node. To find out if that is the case run the following command:

    $ sudo docker logs seed | grep -i "singleton manager"
    
    [INFO] [07/17/2014 16:35:35.982] [ClusterSystem-akka.actor.default-dispatcher-5] [akka.tcp://ClusterSystem@172.17.0.5:2552/user/singletonSecurityService] Singleton manager [akka.tcp://ClusterSystem@172.17.0.5:2552] starting singleton actor
    [INFO] [07/17/2014 16:35:35.999] [ClusterSystem-akka.actor.default-dispatcher-4] [akka.tcp://ClusterSystem@172.17.0.5:2552/user/singletonSecurityServiceView] Singleton manager [akka.tcp://ClusterSystem@172.17.0.5:2552] starting singleton actor    

In my case, this is the node that holds the Singleton instances. Lets kill the node :-)
    
    $ sudo docker rm -f seed

Lets see which containers are running:
    
    $ sudo docker ps
    
    CONTAINER ID        IMAGE                                COMMAND                CREATED             STATUS              PORTS                                                                           NAMES
    6c77eb740306        dnvriend/spray-ba-singleton:latest   /appl/start /bin/bas   19 minutes ago      Up 19 minutes       0.0.0.0:49237->2552/tcp, 0.0.0.0:49238->8080/tcp                                node2
    b5f568874bd3        dnvriend/spray-ba-singleton:latest   /appl/start /bin/bas   19 minutes ago      Up 19 minutes       0.0.0.0:49235->2552/tcp, 0.0.0.0:49236->8080/tcp                                node1
    942a0caa77a1        poklet/cassandra:latest              start 172.17.0.2       20 minutes ago      Up 20 minutes       22/tcp, 61621/tcp, 7000/tcp, 7001/tcp, 7199/tcp, 8012/tcp, 9042/tcp, 9160/tcp   cass3,node1/cas3,node2/cas3
    3e12b2e68c87        poklet/cassandra:latest              start 172.17.0.2       20 minutes ago      Up 20 minutes       22/tcp, 61621/tcp, 7000/tcp, 7001/tcp, 7199/tcp, 8012/tcp, 9042/tcp, 9160/tcp   cass2,node1/cas2,node2/cas2
    7a7235b8b693        poklet/cassandra:latest              start                  20 minutes ago      Up 20 minutes       22/tcp, 61621/tcp, 7000/tcp, 7001/tcp, 7199/tcp, 8012/tcp, 9042/tcp, 9160/tcp   cass1,node1/cas1,node2/cas1

As we can see, the node called 'seed' is now dead and gone. Only node1 and node2 are running. Let's see on which node the
Cluster Singleton has been re-created:
    
    $ sudo docker logs node1 | grep -i "singleton manager"
    
    [INFO] [07/17/2014 16:54:43.328] [ClusterSystem-akka.actor.default-dispatcher-5] [akka.tcp://ClusterSystem@172.17.0.6:2552/user/singletonSecurityService] Singleton manager [akka.tcp://ClusterSystem@172.17.0.6:2552] starting singleton actor
    [INFO] [07/17/2014 16:54:43.333] [ClusterSystem-akka.actor.default-dispatcher-3] [akka.tcp://ClusterSystem@172.17.0.6:2552/user/singletonSecurityServiceView] Singleton manager [akka.tcp://ClusterSystem@172.17.0.6:2552] starting singleton actor

Let's verify that the state is still there by logging into the following two URLS:

    http://192.168.99.99:49236/web/index.html    
    http://192.168.99.99:49238/web/index.html    

Also check whether you can still login to the secure context:

    http://192.168.99.99:49236/secure    
    http://192.168.99.99:49238/secure    

## Viewing the journal/snapshot data
The other nodes, most likely are 172.17.0.2 (cass1) 172.17.0.3 (cass2) and 172.17.0.4 (cass3). To verify that the data
is stored on all the nodes, we should query them: 

    $ sudo docker run -ti poklet/cassandra /bin/bash

Then type:
    
    bash-4.1# cqlsh 172.17.0.2
    Connected to Test Cluster at 172.17.0.2:9160.
    [cqlsh 4.1.1 | Cassandra 2.0.6 | CQL spec 3.1.1 | Thrift protocol 19.39.0]
    Use HELP for help.
    cqlsh>

Alternatively you  could also run cqlsh directly:

    $ sudo docker run -ti poklet/cassandra cqlsh $CASS1_I
    
or
    
    $ sudo docker run -ti poklet/cassandra cqlsh 172.17.0.3

By default, the journal messages are stored in the keyspace 'akka' and in the table 'messages':
    
    cqlsh> select * from akka.messages;

By default, snapshots are stored in the keyspace 'akka_snapshot' and in the table 'snapshots':
    
    cqlsh> select * from akka_snapshot.snapshots;

Login to all the nodes, and verify the data can be resolved.
    
# Httpie
We will use the *great* tool [httpie](https://github.com/jakubroztocil/httpie), so please install it:

# REST API
## Getting a list of users
        
    $ http http://localhost:8080/api/users
    
## Get a user by name
The username has been encoded in the path
    
    $ http http://localhost:8080/api/users/foo
    
## Adding or updating users

    $ http PUT http://localhost:8080/api/users username="foo" password="bar"
    
## Deleting a user
The username has been encoded in the path

    $ http DELETE http://localhost:8080/api/users/foo
    
# The secured resource
The resource that has been secured for us is in the /secure context

## No authentication

    $ http http://localhost:8080/secure

## Basic Authentication
    
    $ http -a foo:bar http://localhost:8080/secure
    
# User Credentials CRUD Interface
The application should automatically launch your browser and show the CRUD screen, but if it doesn't:

    http://localhost:8080/web/index.html
        
Have fun!