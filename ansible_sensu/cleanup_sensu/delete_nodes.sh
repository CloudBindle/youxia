#!/bin/sh

# Please set the following
key="/root/george_aws.pem"
sensu_server=54.197.75.85

echo "The following Sensu nodes will be removed:\n"
cat nodes_to_delete.txt
echo -n "Do you agree with this? [y or n]: "
read answer
case $answer in

        [yY] )
                echo "Proceeding to delete."
                ;;

        [nN] )
                echo "Ok, correct the list and try again";
                exit 1
                ;;
        *) echo "Invalid input"
                exit 1
            ;;
esac

scp -i $key nodes_to_delete.txt ubuntu@$sensu_server:/tmp
scp -i $key remote_delete.sh ubuntu@$sensu_server:/tmp


ssh -i $key ubuntu@$sensu_server "chmod +x /tmp/remote_delete.sh; /tmp/remote_delete.sh"

scp -i $key ubuntu@$sensu_server:/tmp/active_nodes_* .

ssh -i $key ubuntu@$sensu_server "rm /tmp/active_nodes_at* /tmp/nodes_to_delete.txt /tmp/remote_delete.sh"

echo "These are the existing active Sensu nodes:\n"
cat active_nodes*.txt
