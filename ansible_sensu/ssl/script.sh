#!/usr/bin/env bash
set -o errexit
set -o pipefail

wget http://sensuapp.org/docs/0.13/tools/ssl_certs.tar
tar -xvf ssl_certs.tar
cd ssl_certs
./ssl_certs.sh generate
