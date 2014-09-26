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

package io.cloudbindle.youxia.util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import io.cloudbindle.youxia.listing.InstanceListingInterface;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.BuiltinHelpFormatter;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.jclouds.collect.IterableWithMarker;
import org.jclouds.collect.PagedIterable;
import org.jclouds.openstack.nova.v2_0.NovaApi;
import org.jclouds.openstack.nova.v2_0.domain.Server;
import org.jclouds.openstack.nova.v2_0.features.ServerApi;

/**
 * Weirdly, there seems to be no way to create metadata for instances in OpenStack's web interface. Here's a quick and dirty utility to tag
 * specific instances.
 * 
 * @author dyuen
 */
public class OpenStackTagger implements InstanceListingInterface {
    private final HierarchicalINIConfiguration youxiaConfig;
    private OptionSet options;
    private final ArgumentAcceptingOptionSpec<String> instances;

    public OpenStackTagger(String[] args) {
        // record configuration
        this.youxiaConfig = ConfigTools.getYouxiaConfig();
        // TODO: validate that all required parameters are present
        OptionParser parser = new OptionParser();

        parser.acceptsAll(Arrays.asList("help", "h", "?"), "Provides this help message.");

        this.instances = parser.acceptsAll(Arrays.asList("instance-ids", "i"), "Instance ids to tag").withRequiredArg()
                .ofType(String.class).required().withValuesSeparatedBy(",");

        try {
            this.options = parser.parse(args);
        } catch (OptionException e) {
            try {
                final int helpNumColumns = 160;
                parser.formatHelpWith(new BuiltinHelpFormatter(helpNumColumns, 2));
                parser.printHelpOn(System.out);
                System.exit(-1);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    @Override
    public Map<String, String> getInstances() {
        String managedTag = youxiaConfig.getString(YOUXIA_MANAGED_TAG);
        List<String> valuesOf = options.valuesOf(instances);
        Set<String> instanceSet = Sets.newHashSet(valuesOf);

        NovaApi novaApi = ConfigTools.getNovaApi();
        Map<String, String> map = Maps.newHashMap();
        for (String zone : novaApi.getConfiguredZones()) {
            System.out.println("Looking at zone: " + zone);
            // TODO: figure out what is going on with this weird nested structure
            ServerApi serverApiForZone = novaApi.getServerApiForZone(zone);
            PagedIterable<Server> listInDetail = serverApiForZone.listInDetail();
            ImmutableList<IterableWithMarker<Server>> toList = listInDetail.toList();
            for (IterableWithMarker<Server> iterable : toList) {
                ImmutableList<Server> toList1 = iterable.toList();
                for (Server server : toList1) {
                    if (instanceSet.contains(server.getId())) {
                        ImmutableMap<String, String> metadata = ImmutableMap.of(YOUXIA_MANAGED_TAG, managedTag);
                        serverApiForZone.setMetadata(server.getId(), metadata);
                        map.put(server.getId(), server.getAccessIPv4());
                    }
                }
            }
        }
        return map;
    }

    public static void main(String[] args) {
        OpenStackTagger lister = new OpenStackTagger(args);
        Map<String, String> instances = lister.getInstances();
        System.out.println("Tagged the following instances:");
        for (Entry<String, String> instance : instances.entrySet()) {
            System.out.println(instance.getKey() + " " + instance.getValue());
        }
    }

}
