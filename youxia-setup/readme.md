# Introduction

This deploys all the bindle deployment components.

# Usage:

0. Spin up a ubuntu instance (I used a m3.xlarge with a 64GB root partition)
1. Copy a pem private key to the ssh directory. This will be used to connect to instances that Youxia will spin up automatically. 
2. Fill out an aws config file in aws_config directory
3. Fill out an youxia config file in youxia_config directory
4. Customize the inventory file with the desired target host where you want to deploy. Obviously, make sure that your SSH key and ports are correct. 
5. Run this playbook via

        ansible-playbook -i inventory site.yml

   or without key checking and overriding the version of youxia used

        ANSIBLE_HOST_KEY_CHECKING=False  ansible-playbook -i inventory site.yml --extra-vars '{"youxia_version":"1.1.0-alpha.4"}'

