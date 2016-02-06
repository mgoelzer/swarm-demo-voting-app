#!/bin/sh

# Kills all containers on all nodes listed in 'arr' below

# Per node, doing the equivalent of:
#   IP="192.168.33.11" sh -c 'docker -H tcp://$IP:2375 ps'
#   CID=ca476182988c IP="192.168.33.11" sh -c 'docker -$ tcp://$IP:2375 stop $CID ; docker rm $CID'

arr=("192.168.33.11" "192.168.33.12" "192.168.33.20" "192.168.33.21" "192.168.33.200" "192.168.33.251")

for IP in "${arr[@]}"; do
  echo "Clearing $IP"
  cids=( $(docker -H tcp://$IP:2375 ps -q -a) )
  for CID in ${cids[@]}; do
    echo "  - Removing $CID [on $IP]"
    docker -H tcp://$IP:2375 stop $CID
    docker -H tcp://$IP:2375 rm $CID
  done
done
