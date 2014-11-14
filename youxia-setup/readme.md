# Introduction

This deploys all the bindle deployment components.

# Usage:

0. Spin up a ubuntu instance (I used a m3.xlarge with a 64GB root partition)
1. Copy a pem private key to the ssh directory
2. Fill out a aws config file in aws_config
3. Fill out a youxia config file in youxia_config

4. Run this playbook via

        ansible-playbook -i inventory site.yml

   or without key checking and overriding the version of youxia used

        ANSIBLE_HOST_KEY_CHECKING=False  ansible-playbook -i inventory site.yml --extra-vars '{"youxia_version":"1.1.0-alpha.4"}'

