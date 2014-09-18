# Introduction

This demonstrates the usage of Ansible to create sensu servers and clients. 

TO-DO: Setup seqware-vagrant (Bindle) without hard-coding floating IP addresses so that jenkins capacity can scale as needed.

# Usage

1. Setup your inventory file (production) appropriately.  (For this version, you will need to launch a VM in openstack manually)
2. Run this playbook via

    ansible-playbook -i inventory site.yml
