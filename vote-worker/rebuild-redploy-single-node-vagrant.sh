#!/bin/sh

docker stop worker01
docker rm worker01
docker rmi vote-worker
docker -H tcp://192.168.33.200:2375 build -t vote-worker .

# OLD (before registrator):
#docker run --restart=unless-stopped --env="constraint:node==worker01" -d -e WORKER_NUMBER='01' -e FROM_REDIS_HOST=1 -e TO_REDIS_HOST=1 --name worker01 --net mynet vote-worker

# NEW (after registrator tracking all redis's):
docker run --restart=unless-stopped --env="constraint:node==worker01" -d -e WORKER_NUMBER="01" -e REDIS_CATALOG="http://192.168.33.11:8500/v1/catalog/service/redis" --name worker01 --net mynet vote-worker

