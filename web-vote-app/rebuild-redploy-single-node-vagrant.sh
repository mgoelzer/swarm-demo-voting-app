#!/bin/sh

docker stop web01 web02 ; docker rm web01 web02
docker -H tcp://192.168.33.20:2375 rmi web-vote-app
docker -H tcp://192.168.33.21:2375 rmi web-vote-app

docker -H tcp://192.168.33.20:2375 build -t web-vote-app .
docker -H tcp://192.168.33.21:2375 build -t web-vote-app .

docker run --restart=unless-stopped --env="constraint:node==frontend01" -d -p 5000:80 -e QUEUE_HOSTNAME="redis01" -e DEBUG_SELF_NAME="web01" --name web01 --net mynet --hostname votingapp.local web-vote-app
docker run --restart=unless-stopped --env="constraint:node==frontend02" -d -p 5000:80 -e QUEUE_HOSTNAME="redis02" -e DEBUG_SELF_NAME="web02" --name web02 --net mynet --hostname votingapp.local web-vote-app
