#!/bin/sh

##
## Commands to create the Swarm layer containers
##

docker -H=tcp://192.168.33.11:2375 run --restart=unless-stopped -d -p 8500:8500 -p 8400:8400 -p 8600:53/udp --name consul -h consul progrium/consul -server -bootstrap -ui-dir /ui
# web ui on:  http://192.168.33.11:8500/ui/

##
## No joins required in Swarm 1.0+
##
#  docker -H=tcp://192.168.33.20:2375 run --restart=unless-stopped -d swarm join --advertise=192.168.33.20:2375 consul://192.168.33.11:8500/
#  docker -H=tcp://192.168.33.21:2375 run --restart=unless-stopped -d swarm join --advertise=192.168.33.21:2375 consul://192.168.33.11:8500/
#  [et cetera]
#
#  docker -H=tcp://192.168.33.200:2375 run --restart=unless-stopped -d swarm join --advertise=192.168.33.200:2375 consul://192.168.33.11:8500/
#  docker -H=tcp://192.168.33.201:2375 run --restart=unless-stopped -d swarm join --advertise=192.168.33.201:2375 consul://192.168.33.11:8500/
#  [et cetera]
#
#  docker -H=tcp://192.168.33.250:2375 run --restart=unless-stopped -d swarm join --advertise=192.168.33.250:2375 consul://192.168.33.11:8500/

docker -H=tcp://192.168.33.11:2375 run --restart=unless-stopped -d -p 3375:2375 swarm manage --discovery-opt="kv.path=docker/nodes" consul://192.168.33.11:8500/

export DOCKER_HOST="tcp://192.168.33.11:3375"
docker network create --driver overlay mynet

##
## Put registrator agent on every node
##
arr=("web01" "web02" "worker01" "pg" "master" "interlock") ; \
for NODE in "${arr[@]}"; do                          \
	docker run -d -e constraint:node==$NODE --net=mynet --volume=/var/run/docker.sock:/tmp/docker.sock gliderlabs/registrator:latest consul://192.168.33.11:8500/ ;                           \
done

##
## OPTIONAL:  Commands to test the cluster
##
docker info
docker run -d --name web --net mynet nginx
docker run -itd --name shell1 --net mynet alpine /bin/sh
docker attach shell1
$ ping web
docker stop web shell1 ; docker rm web shell1
