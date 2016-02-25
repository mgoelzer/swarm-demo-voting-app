#!/bin/sh

# Kills all containers scheduled by manager 

export DOCKER_HOST="tcp://192.168.33.11:3375"

docker stop pg
docker stop results-app
docker stop worker01
docker stop web01
docker stop web02
docker stop redis01
docker stop redis02
docker stop interlock

docker rm pg
docker rm results-app
docker rm worker01
docker rm web01
docker rm web02
docker rm redis01
docker rm redis02
docker rm interlock

