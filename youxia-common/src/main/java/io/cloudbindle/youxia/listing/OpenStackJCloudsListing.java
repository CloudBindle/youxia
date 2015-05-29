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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import io.cloudbindle.youxia.util.ConfigTools;
import io.cloudbindle.youxia.util.Constants;
import io.cloudbindle.youxia.util.Log;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import org.jclouds.collect.IterableWithMarker;
import org.jclouds.collect.PagedIterable;
import org.jclouds.openstack.nova.v2_0.NovaApi;
import org.jclouds.openstack.nova.v2_0.domain.Address;
import org.jclouds.openstack.nova.v2_0.domain.Server;
import org.jclouds.openstack.nova.v2_0.features.ServerApi;

/**
 * This lists instances available on OpenStack.
 *
 *
 * @author dyuen
 */
public class OpenStackJCloudsListing extends AbstractInstanceListing {

    /**
     * This actually returns ids without the zone prefix
     *
     * @return the java.util.Map<java.lang.String,java.lang.String>
     */
    @Override
    public Map<String, InstanceDescriptor> getInstances() {
        String managedTagValue = ConfigTools.getYouxiaConfig().getString(ConfigTools.YOUXIA_MANAGED_TAG);
        NovaApi novaApi = ConfigTools.getNovaApi();
        Map<String, InstanceDescriptor> map = Maps.newHashMap();
        for (String zone : novaApi.getConfiguredZones()) {
            Log.info("Looking at zone: " + zone);
            // TODO: figure out what is going on with this weird nested structure
            ServerApi serverApiForZone = novaApi.getServerApiForZone(zone);
            PagedIterable<Server> listInDetail = serverApiForZone.listInDetail();
            ImmutableList<IterableWithMarker<Server>> toList = listInDetail.toList();
            for (IterableWithMarker<Server> iterable : toList) {
                ImmutableList<Server> toList1 = iterable.toList();
                for (Server server : toList1) {
                    String managedTag = null;
                    String managedState = null;
                    for (Entry<String, String> tag : server.getMetadata().entrySet()) {
                        if (tag.getKey().equals(ConfigTools.YOUXIA_MANAGED_TAG) && tag.getValue().equals(managedTagValue)) {
                            managedTag = tag.getValue();
                        }
                        if (tag.getKey().equals(Constants.STATE_TAG)) {
                            managedState = tag.getValue();
                        }
                    }
                    /**
                     * TODO: This is unfortunate, but it looks like Openstack doesn't actually know which ip addresses assigned to an
                     * instance if public or private. On ours, it looks like the second one, but this is totally unreliable. TODO: this
                     * iterator should also return return live instances when liveInstances and all when not
                     **/
                    Iterator<Entry<String, Address>> iterator = server.getAddresses().entries().iterator();
                    // match generic api with namespaces ids using the zone
                    String id = zone + "/" + server.getId();
                    String address = null;
                    if (iterator.hasNext()) {
                        address = iterator.next().getValue().getAddr();
                    }
                    String secondAddress = null;
                    if (iterator.hasNext()) {
                        secondAddress = iterator.next().getValue().getAddr();
                    }
                    handleMapping(managedTag, managedState, id, new InstanceDescriptor(address, secondAddress), map);
                }
            }
        }

        Log.info("Located " + map.values().size() + " relevant instances on OpenStack");
        return map;
    }

    public static void main(String[] args) {
        OpenStackJCloudsListing lister = new OpenStackJCloudsListing();
        Map<String, InstanceDescriptor> instances = lister.getInstances();
        for (Entry<String, InstanceDescriptor> instance : instances.entrySet()) {
            System.out.println(instance.getKey() + " " + instance.getValue());
        }
    }

}
