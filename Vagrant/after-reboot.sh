#!/bin/sh

##
## Commands to re-create the Swarm cluster layer
##

docker -H=tcp://192.168.33.11:2375 run --restart=unless-stopped -d -p 8500:8500 -p 8400:8400 -p 8600:53/udp --name consul -h consul progrium/consul -server -bootstrap -ui-dir /ui
# web ui on:  http://192.168.33.11:8500/ui/

docker -H=tcp://192.168.33.11:2375 run --restart=unless-stopped -d -p 3375:2375 swarm manage --discovery-opt="kv.path=docker/nodes" consul://192.168.33.11:8500/

export DOCKER_HOST="tcp://192.168.33.11:3375"
docker network create --driver overlay mynet

##
## Put registrator agent on every node
##
arr=("web01" "web02" "worker01" "pg" "master" "interlock") ; \
for NODE in "${arr[@]}"; do                                  \
	docker run -d -e constraint:node==$NODE --net=mynet --volume=/var/run/docker.sock:/tmp/docker.sock gliderlabs/registrator:latest consul://192.168.33.11:8500/ ;                      \
done
