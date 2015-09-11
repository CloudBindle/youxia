/*
 * Copyright (C) 2014 CloudBindle
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.cloudbindle.youxia.listing;

import io.cloudbindle.youxia.util.Constants;
import io.cloudbindle.youxia.util.Log;
import java.util.Map;

/**
 * This is an interface for classes that list available instances on AWS, Openstack, vCloud, etc.
 *
 * @author dyuen
 */
public abstract class AbstractInstanceListing {

    /**
     * Retrieves a map between a unique identifier and public ip address
     *
     * @return the java.util.Map<java.lang.String,java.lang.String>
     */
    public abstract Map<String, InstanceDescriptor> getInstances();

    /**
     * If the managedTag and managedState are appropriate, this will add the ip address to the provided map of instances
     *
     * @param managedTag
     * @param managedState
     * @param instanceId
     * @param instanceDescriptor
     * @param map
     */
    public static void handleMapping(String managedTag, String managedState, String instanceId, InstanceDescriptor instanceDescriptor,
            Map<String, InstanceDescriptor> map) {
        if (managedTag != null && managedState != null) {
            if (!(managedState.equals(Constants.STATE.READY.toString()) || managedState.equals(Constants.STATE.SETTING_UP.toString()))) {
                return;
            }
            if (instanceDescriptor.getIpAddress() == null) {
                Log.info("Node " + instanceId + " had no public ip address, skipping");
                return;
            }

            map.put(instanceId, instanceDescriptor);
        }
    }

    public static class InstanceDescriptor {
        private final String name;
        private final String ipAddress;
        private final boolean spotInstance;
        private final String privateIpAddress;
        private final String flavour;

        public InstanceDescriptor(String name, String ipAddress) {
            this(name, ipAddress, null);
        }

        public InstanceDescriptor(String name, String ipAddress, String privateIpAddress) {
            this(name, ipAddress, false);
        }

        public InstanceDescriptor(String name, String ipAddress, boolean spotInstance) {
            this(name, ipAddress, null, spotInstance, null);
        }

        public InstanceDescriptor(String name, String ipAddress, String privateIpAddress, boolean spotInstance, String flavour) {
            this.privateIpAddress = privateIpAddress;
            this.name = name;
            this.ipAddress = ipAddress;
            this.spotInstance = spotInstance;
            this.flavour = flavour;
        }

        /**
         *
         * @return a public ip address
         */
        public String getIpAddress() {
            return ipAddress;
        }

        public String getPrivateIpAddress() {
            return privateIpAddress;
        }

        public boolean isSpotInstance() {
            return spotInstance;
        }

        @Override
        public String toString() {
            return ipAddress + " spot: " + spotInstance;
        }

        /**
         * @return the name
         */
        public String getName() {
            return name;
        }

        public String getFlavour() {
            return flavour;
        }
    }
}
