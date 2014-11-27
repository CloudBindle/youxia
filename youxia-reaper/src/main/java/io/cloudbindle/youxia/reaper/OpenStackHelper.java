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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import io.cloudbindle.youxia.listing.AbstractInstanceListing;
import io.cloudbindle.youxia.listing.ListingFactory;
import io.cloudbindle.youxia.util.ConfigTools;
import io.cloudbindle.youxia.util.Constants;
import io.cloudbindle.youxia.util.Log;
import java.util.Map;
import java.util.Set;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.jclouds.collect.IterableWithMarker;
import org.jclouds.collect.PagedIterable;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.openstack.nova.v2_0.NovaApi;
import org.jclouds.openstack.nova.v2_0.domain.Server;
import org.jclouds.openstack.nova.v2_0.features.ServerApi;

/**
 *
 * @author dyuen
 */
public class OpenStackHelper implements AbstractHelper {

    @Override
    public String identifyOrphanedInstance(Map.Entry<String, String> instance) {
        try (ComputeServiceContext genericOpenStackApi = ConfigTools.getGenericOpenStackApi()) {
            ComputeService computeService = genericOpenStackApi.getComputeService();
            NodeMetadata nodeMetadata = computeService.getNodeMetadata(instance.getKey());
            if (nodeMetadata != null) {
                if (nodeMetadata.getUserMetadata().containsKey(Constants.STATE_TAG)
                        && !nodeMetadata.getUserMetadata().get(Constants.STATE_TAG).equals(Constants.STATE.READY.toString())) {
                    Log.info(instance.getKey() + " is not ready, likely an orphaned VM");
                    return nodeMetadata.getUserMetadata().get(Constants.SENSU_NAME);
                }
            }
        }
        return null;
    }

    @Override
    public AbstractInstanceListing getListing() {
        return ListingFactory.createOpenStackListing();
    }

    @Override
    public void terminateInstances(Set<String> instancesToKill) {
        Log.stdoutWithTime("Marking instances for death " + StringUtils.join(instancesToKill, ","));
        retagInstances(instancesToKill);
        try (ComputeServiceContext genericOpenStackApi = ConfigTools.getGenericOpenStackApi()) {
            ComputeService computeService = genericOpenStackApi.getComputeService();
            for (String instanceToKill : instancesToKill) {
                Log.stdoutWithTime("Terminating " + instanceToKill);
                computeService.destroyNode(instanceToKill);
            }
        }
    }

    /**
     * This code totally sucks. Is there really no way of retagging using the generic API?
     *
     * @param ids
     */
    private void retagInstances(Set<String> ids) {
        // retag instances with finished metadata, cannot see how to do this with the generic api
        // this sucks incredibly bad and is copied from the OpenStackTagger, there should be a way to use the generic api for
        // this
        NovaApi novaApi = ConfigTools.getNovaApi();
        HierarchicalINIConfiguration youxiaConfig = ConfigTools.getYouxiaConfig();
        ServerApi serverApiForZone = novaApi.getServerApiForZone(youxiaConfig.getString(ConfigTools.YOUXIA_OPENSTACK_ZONE));
        PagedIterable<Server> listInDetail = serverApiForZone.listInDetail();
        // what is this crazy nested structure?
        ImmutableList<IterableWithMarker<Server>> toList = listInDetail.toList();
        for (IterableWithMarker<Server> iterable : toList) {
            ImmutableList<Server> toList1 = iterable.toList();
            for (Server server : toList1) {
                // generic api uses region ids, the specific one doesn't. Sigh.
                final String nodeId = youxiaConfig.getString(ConfigTools.YOUXIA_OPENSTACK_ZONE) + "-" + server.getId().replace("/", "-");
                if (ids.contains(nodeId)) {
                    Log.stdoutWithTime("Finishing configuring " + nodeId);
                    Map<String, String> metadata = Maps.newHashMap(server.getMetadata());
                    metadata.put(Constants.STATE_TAG, Constants.STATE.MARKED_FOR_DEATH.toString());
                    serverApiForZone.setMetadata(server.getId(), metadata);
                }
            }
        }
    }

}
