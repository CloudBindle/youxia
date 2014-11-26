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
package io.cloudbindle.youxia.reaper;

import io.cloudbindle.youxia.listing.AbstractInstanceListing;
import io.cloudbindle.youxia.listing.ListingFactory;
import io.cloudbindle.youxia.util.ConfigTools;
import io.cloudbindle.youxia.util.Constants;
import io.cloudbindle.youxia.util.Log;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.domain.NodeMetadata;

/**
 *
 * @author dyuen
 */
public class OpenStackHelper implements AbstractHelper {

    @Override
    public boolean identifyOrphanedInstance(Map.Entry<String, String> instance) {
        try (ComputeServiceContext genericOpenStackApi = ConfigTools.getGenericOpenStackApi()) {
            ComputeService computeService = genericOpenStackApi.getComputeService();
            NodeMetadata nodeMetadata = computeService.getNodeMetadata(instance.getKey());
            if (nodeMetadata != null) {
                if (nodeMetadata.getUserMetadata().containsKey(Constants.STATE_TAG)
                        && !nodeMetadata.getUserMetadata().get(Constants.STATE_TAG).equals(Constants.STATE.READY.toString())) {
                    Log.info(instance.getKey() + " is not ready, likely an orphaned VM");
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public AbstractInstanceListing getListing() {
        return ListingFactory.createOpenStackListing();
    }

    @Override
    public void terminateInstances(Set<String> instancesToKill) {
        Log.stdoutWithTime("Marking instances for death " + StringUtils.join(instancesToKill, ","));

        try (ComputeServiceContext genericOpenStackApi = ConfigTools.getGenericOpenStackApi()) {
            ComputeService computeService = genericOpenStackApi.getComputeService();
            for (String instanceToKill : instancesToKill) {
                Log.stdoutWithTime("Terminating " + instanceToKill);
                computeService.destroyNode(instanceToKill);
            }
        }
    }

}
