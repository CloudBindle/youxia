# Introduction

This demonstrates the usage of Ansible to create sensu servers and clients. 

# Usage

Make sure that ports are open between your instances. Rabbitmq uses 5671, sensu uses port 4567, uchiwa uses port 3000. 
Clients and the server will need access to rabbitmq. Uchiwa needs access to sensu. You need access to Uchiwa. 

0. Populate the various file directories appropriately using SSL certificate, see [link](http://sensuapp.org/docs/latest/certificates)
1. Setup your inventory file (production) appropriately.  (For this version, you will need to launch a VM in openstack manually)
2. Run this playbook via

    ansible-playbook -i inventory site.yml

   or without key checking

    ANSIBLE_HOST_KEY_CHECKING=False  ansible-playbook -i inventory site.yml


