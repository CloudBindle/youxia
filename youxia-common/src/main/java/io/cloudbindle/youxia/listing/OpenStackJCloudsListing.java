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
public class OpenStackJCloudsListing implements InstanceListingInterface {

    @Override
    public Map<String, String> getInstances(boolean liveInstances) {
        String managedTag = ConfigTools.getYouxiaConfig().getString(ConfigTools.YOUXIA_MANAGED_TAG);
        NovaApi novaApi = ConfigTools.getNovaApi();
        Map<String, String> map = Maps.newHashMap();
        for (String zone : novaApi.getConfiguredZones()) {
            Log.info("Looking at zone: " + zone);
            // TODO: figure out what is going on with this weird nested structure
            ServerApi serverApiForZone = novaApi.getServerApiForZone(zone);
            PagedIterable<Server> listInDetail = serverApiForZone.listInDetail();
            ImmutableList<IterableWithMarker<Server>> toList = listInDetail.toList();
            for (IterableWithMarker<Server> iterable : toList) {
                ImmutableList<Server> toList1 = iterable.toList();
                for (Server server : toList1) {
                    for (Entry<String, String> tag : server.getMetadata().entrySet()) {
                        if (tag.getKey().equals(ConfigTools.YOUXIA_MANAGED_TAG) && tag.getValue().equals(managedTag)) {
                            /**
                             * TODO: This is unfortunate, but it looks like Openstack doesn't actually know which ip addresses assigned to
                             * an instance if public or private. On ours, it looks like the second one, but this is totally unreliable.
                             * TODO: this iterator should also return return live instances when liveInstances and all when !
                             **/
                            Iterator<Entry<String, Address>> iterator = server.getAddresses().entries().iterator();
                            iterator.next();
                            map.put(server.getId(), iterator.next().getValue().getAddr());
                        }
                    }
                }
            }
        }

        Log.info("Located " + map.values().size() + " relevant instances on OpenStack");
        return map;
    }

    public static void main(String[] args) {
        OpenStackJCloudsListing lister = new OpenStackJCloudsListing();
        Map<String, String> instances = lister.getInstances(true);
        for (Entry<String, String> instance : instances.entrySet()) {
            System.out.println(instance.getKey() + " " + instance.getValue());
        }
    }

}
