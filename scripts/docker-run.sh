#!/bin/bash
sudo docker run -d -P --link cass1:cas1 --link cass2:cas2 --link cass3:cas3 --name seed dnvriend/spray-ba-singleton
sudo docker run -d -P --link cass1:cas1 --link cass2:cas2 --link cass3:cas3 --link seed:seed --name node1 dnvriend/spray-ba-singleton
sudo docker run -d -P --link cass1:cas1 --link cass2:cas2 --link cass3:cas3 --link seed:seed --name node2 dnvriend/spray-ba-singleton

