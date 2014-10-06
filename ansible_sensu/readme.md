# Introduction

This demonstrates the usage of Ansible to create sensu servers and clients. 

# Usage

Make sure that ports are open between your instances. Rabbitmq uses 5671, sensu uses port 4567, uchiwa uses port 3000. 
Clients and the server will need access to rabbitmq. Uchiwa needs access to sensu. You need access to Uchiwa. 

0. Run the script in ssl (bash ssl/script.sh) to generate a unique set of SSL certificates for your sensu install 
1. Setup your inventory file (production) appropriately.  (For this version, you will need to launch a VM in openstack manually)
1.a There a number of Sensu checks defined in "roles/server/templates/checks.json.j2". The user "sensu" needs write access to the directory that check-fs-writable.rb is checking (hardcoded to "/mnt"). I'll add a task in the client role to fix this once I know exacty what's needed.
1.b The IP address of the Sensu server has to be defined in "/roles/base/vars/main.yml" if you're not pointing to 54.197.75.85

2. Run this playbook via

    ansible-playbook -i inventory site.yml

   or without key checking

    ANSIBLE_HOST_KEY_CHECKING=False  ansible-playbook -i inventory site.yml

If the Sensu server was already provisioned and you want to speed up the installation of a new Sensu client, you can limit the playbook to only a subset of the hosts:

    ansible-playbook -i inventory site.yml --limit worker


