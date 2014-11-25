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
     * @param liveInstances
     *            return live instances if true, dead instances if false
     * @return
     */
    public abstract Map<String, String> getInstances(boolean liveInstances);

    /**
     * If the managedTag and managedState are appropriate, this will add the ip address to the provided map of instances
     *
     * @param managedTag
     * @param managedState
     * @param liveInstances
     * @param instanceId
     * @param ipAddress
     * @param map
     */
    public static void handleMapping(String managedTag, String managedState, boolean liveInstances, String instanceId, String ipAddress,
            Map<String, String> map) {
        if (managedTag != null && managedState != null) {
            if (liveInstances) {
                if (!(managedState.equals(Constants.STATE.READY.toString()) || managedState.equals(Constants.STATE.SETTING_UP.toString()))) {
                    return;
                }
                if (ipAddress == null) {
                    Log.info("Node " + instanceId + " had no public ip address, skipping");
                    return;
                }
            } else {
                if (!managedState.equals(Constants.STATE.MARKED_FOR_DEATH.toString())) {
                    return;
                }
            }
            map.put(instanceId, ipAddress);
        }
    }
}
