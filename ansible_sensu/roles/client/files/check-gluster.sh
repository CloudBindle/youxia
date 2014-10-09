#!/bin/sh
#
# Verifies Gluster's peering status

disc=`gluster peer status|grep State|grep Disconnected|wc -l`

if [ $disc -gt 0 ]; then
  echo "Critical: $disc gluster peer(s) in status disconnected!!!"
  exit 2
else
  echo "All gluster peers are connected"
    exit 0
fi
