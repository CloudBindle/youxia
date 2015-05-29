#!/bin/sh

#Please set the following
key="/root/george_aws.pem"
sensu_server=54.197.75.85

[ -f ./nodes_to_delete.txt ] && rm ./nodes_to_delete.txt
ssh -i $key ubuntu@$sensu_server 'sudo -H sensu-cli client list| grep "name:"' > nodes_to_delete.txt_temp
cat nodes_to_delete.txt_temp | sed -r "s/\x1B\[([0-9]{1,2}(;[0-9]{1,2})?)?[mGK]//g" | awk -F":" '{print $2}'| sed -e 's/^[ \t]*//' > nodes_to_delete.txt
rm nodes_to_delete.txt_temp

echo "Please check the nodes_to_delete.txt file for a list of Sensu clients that can be deleted."
