# Introduction

This deploys all the bindle deployment components.

# Usage:

0. Spin up a ubuntu instance
1. Setup a deployer.pem in the ssh directory
2. Fill out a aws config file in aws_config
3. Fill out a youxia config file in youxia_config

4. Run this playbook via

    ansible-playbook -i inventory site.yml

   or without key checking

    ANSIBLE_HOST_KEY_CHECKING=False  ansible-playbook -i inventory site.yml

