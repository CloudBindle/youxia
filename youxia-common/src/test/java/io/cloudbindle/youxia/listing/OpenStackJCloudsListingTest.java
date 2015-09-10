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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import io.cloudbindle.youxia.listing.AbstractInstanceListing.InstanceDescriptor;
import io.cloudbindle.youxia.util.ConfigTools;
import io.cloudbindle.youxia.util.Constants;
import java.util.Iterator;
import java.util.Map;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import static org.easymock.EasyMock.expect;
import org.jclouds.collect.IterableWithMarker;
import org.jclouds.collect.PagedIterable;
import org.jclouds.openstack.nova.v2_0.NovaApi;
import org.jclouds.openstack.nova.v2_0.domain.Address;
import org.jclouds.openstack.nova.v2_0.domain.Flavor;
import org.jclouds.openstack.nova.v2_0.domain.Server;
import org.jclouds.openstack.nova.v2_0.features.ServerApi;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.powermock.api.easymock.PowerMock.mockStatic;
import static org.powermock.api.easymock.PowerMock.replayAll;
import static org.powermock.api.easymock.PowerMock.verifyAll;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

/**
 *
 * @author dyuen
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ ConfigTools.class, HierarchicalINIConfiguration.class, OpenStackJCloudsListing.class, PagedIterable.class })
public class OpenStackJCloudsListingTest {

    /**
     * Test of getInstances method, of class AwsListing.
     *
     * @throws java.lang.Exception
     */
    @Test
    public void testNoInstances() throws Exception {
        mockStatic(ConfigTools.class);
        NovaApi mockClient = setupMocks();

        ServerApi serverApi = mock(ServerApi.class);
        when(mockClient.getConfiguredZones()).thenReturn(Sets.newHashSet("Shaolin Soccer zone"));
        when(mockClient.getServerApiForZone("Shaolin Soccer zone")).thenReturn(serverApi);
        when(serverApi.listInDetail()).thenReturn(new PagedIterable() {
            @Override
            public Iterator iterator() {
                return new Iterator() {

                    @Override
                    public boolean hasNext() {
                        return false;
                    }

                    @Override
                    public Object next() {
                        throw new UnsupportedOperationException("Not supported yet.");
                    }

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException("Not supported yet.");
                    }
                };
            }
        });

        replayAll();

        OpenStackJCloudsListing instance = new OpenStackJCloudsListing();
        Map<String, InstanceDescriptor> result = instance.getInstances();
        Assert.assertTrue("result should be empty", result.isEmpty());

        verifyAll();
    }

    /**
     * Test of getInstances method, of class AwsListing.
     *
     * @throws java.lang.Exception
     */
    @Test
    public void testNoValidInstances() throws Exception {
        mockStatic(ConfigTools.class);
        NovaApi mockClient = setupMocks();

        setupReservationsWithTag(mockClient, "incorrect_tag");

        replayAll();

        OpenStackJCloudsListing awListing = new OpenStackJCloudsListing();
        Map<String, InstanceDescriptor> result = awListing.getInstances();
        Assert.assertTrue("result should be empty", result.isEmpty());

        verifyAll();
    }

    /**
     * Test of getInstances method, of class AwsListing.
     *
     * @throws java.lang.Exception
     */
    @Test
    public void testValidInstances() throws Exception {
        mockStatic(ConfigTools.class);
        NovaApi mockClient = setupMocks();

        setupReservationsWithTag(mockClient, "dummy_tag");

        replayAll();

        OpenStackJCloudsListing awListing = new OpenStackJCloudsListing();
        Map<String, InstanceDescriptor> result = awListing.getInstances();
        Assert.assertTrue("result should not be empty", !result.isEmpty());

        verifyAll();
    }

    private NovaApi setupMocks() {
        NovaApi mockClient = mock(NovaApi.class);
        HierarchicalINIConfiguration mockConfig = mock(HierarchicalINIConfiguration.class);
        expect(ConfigTools.getYouxiaConfig()).andReturn(mockConfig).anyTimes();
        expect(ConfigTools.getNovaApi()).andReturn(mockClient);
        when(mockConfig.getString(ConfigTools.YOUXIA_MANAGED_TAG)).thenReturn("dummy_tag");
        return mockClient;
    }

    private void setupReservationsWithTag(NovaApi mockClient, final String tagname) {
        ServerApi serverApi = mock(ServerApi.class);
        when(mockClient.getConfiguredZones()).thenReturn(Sets.newHashSet("Shaolin Soccer zone"));
        when(mockClient.getServerApiForZone("Shaolin Soccer zone")).thenReturn(serverApi);

        Server server = mock(Server.class);
        Map<String, String> tags = Maps.newHashMap();
        tags.put(ConfigTools.YOUXIA_MANAGED_TAG, tagname);
        tags.put(Constants.STATE_TAG, Constants.STATE.READY.toString());
        Multimap<String, Address> multiMap = ArrayListMultimap.create();
        multiMap.put("blah1", Address.createV4("123.123.123.123"));
        multiMap.put("blah2", Address.createV4("123.123.123.123"));
        when(server.getFlavor()).thenReturn(Flavor.builder().name("m1.xlarge").id("id").ram(4).disk(1000).vcpus(4).build());
        when(server.getAddresses()).thenReturn(multiMap);
        when(server.getMetadata()).thenReturn(tags);
        ImmutableList<Server> serverList = ImmutableList.of(server);
        IterableWithMarker<Server> marker = mock(IterableWithMarker.class);
        when(marker.toList()).thenReturn(serverList);
        ImmutableList<IterableWithMarker<Server>> immutableList = ImmutableList.of(marker);
        PagedIterable<Server> pagedIter = mock(PagedIterable.class);
        when(pagedIter.toList()).thenReturn(immutableList);

        when(serverApi.listInDetail()).thenReturn(pagedIter);
    }

}
