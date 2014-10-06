#!/bin/sh
while read line
do curl --user "admin:seqware" -X DELETE http://localhost:4567/clients/$line;
done < "/tmp/nodes_to_delete.txt"
sleep 10

time=`date +%Y_%m_%d_%H-%M`
# Get a fresh list
sudo -H sensu-cli client list| grep "name:" > /tmp/active_nodes_at_$time.txt

