#!/bin/sh

docker stop worker01
docker rm worker01
docker rmi vote-worker
docker -H tcp://192.168.33.200:2375 build -t vote-worker .

`which echo` -n "Run? (y/N) "
read N
echo
if [ "$N" == "y" ] ; then
  #docker logs -f 
  docker run --restart=unless-stopped --env="constraint:node==worker01" -d -e WORKER_NUMBER="01" -e REDIS_PREFIX="redis" --name worker01 --net mynet vote-worker
fi
