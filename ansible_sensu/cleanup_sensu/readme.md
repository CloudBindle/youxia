# Introduction

This provides a way to remove deleted nodes from the Sensu database so they are not polluting the dashboard anymore.

# Usage

0. Setup the get_nodes.sh with the IP address of the Sensu master node and the correct SSH key allowing you access to that server.
1. Run the script "get_nodes.sh" to generate a list of existing Sensu clients => this will generate "nodes_to_delete.txt".
2. Edit "nodes_to_delete.txt" and leave only the Sensu name of the client(s) you want to delete
3. Run the "delete_nodes.sh" script that will:
		- scp the file "nodes_to_delete.txt" and "remote_delete.sh" to the Sensu server in "/tmp"
		- use API calls to delete the nodes declared in "nodes_to_delete.txt"
		- sleep 10 sec and then generate a fresh list of active nodes from the Sensu API stored in "/tmp/active_nodes.txt"
		- copy the "active_nodes.txt" back to this server  and then clean up "/tmp" on the Sensu server
		- present the list of active nodes to the user


