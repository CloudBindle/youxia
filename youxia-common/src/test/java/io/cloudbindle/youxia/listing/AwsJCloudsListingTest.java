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

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import io.cloudbindle.youxia.util.ConfigTools;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import static org.easymock.EasyMock.expect;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.domain.ComputeType;
import org.jclouds.compute.domain.Hardware;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.OperatingSystem;
import org.jclouds.domain.Location;
import org.jclouds.domain.LoginCredentials;
import org.jclouds.domain.ResourceMetadata;
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
@PrepareForTest({ ConfigTools.class, HierarchicalINIConfiguration.class, AwsJCloudsListing.class })
public class AwsJCloudsListingTest {

    /**
     * Test of getInstances method, of class AwsListing.
     * 
     * @throws java.lang.Exception
     */
    @Test
    public void testNoInstances() throws Exception {
        mockStatic(ConfigTools.class);
        ComputeServiceContext mockClient = setupMocks();

        Set computeSet = Sets.newHashSet();
        ComputeService mockService = mock(ComputeService.class);
        when(mockClient.getComputeService()).thenReturn(mockService);
        when(mockService.listNodes()).thenReturn(computeSet);

        replayAll();

        AwsJCloudsListing instance = new AwsJCloudsListing();
        Map<String, String> result = instance.getInstances(true);
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
        ComputeServiceContext mockClient = setupMocks();

        setupReservationsWithTag(mockClient, "incorrect_tag");

        replayAll();

        AwsJCloudsListing awListing = new AwsJCloudsListing();
        Map<String, String> result = awListing.getInstances(true);
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
        ComputeServiceContext mockClient = setupMocks();

        setupReservationsWithTag(mockClient, "dummy_tag");

        replayAll();

        AwsJCloudsListing awListing = new AwsJCloudsListing();
        Map<String, String> result = awListing.getInstances(true);
        Assert.assertTrue("result should not be empty", !result.isEmpty());

        verifyAll();
    }

    private ComputeServiceContext setupMocks() {
        ComputeServiceContext mockClient = mock(ComputeServiceContext.class);
        HierarchicalINIConfiguration mockConfig = mock(HierarchicalINIConfiguration.class);
        expect(ConfigTools.getYouxiaConfig()).andReturn(mockConfig);
        expect(ConfigTools.getAmazonComputeContext()).andReturn(mockClient);
        when(mockConfig.getString(ConfigTools.YOUXIA_MANAGED_TAG)).thenReturn("dummy_tag");
        return mockClient;
    }

    private void setupReservationsWithTag(ComputeServiceContext mockClient, final String tagname) {
        NodeMetadata metadata = new NodeMetadata() {

            @Override
            public ComputeType getType() {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public String getProviderId() {
                return "randomProviderId";
            }

            @Override
            public String getName() {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public String getId() {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public Set<String> getTags() {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public Location getLocation() {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public URI getUri() {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public Map<String, String> getUserMetadata() {
                Map<String, String> tags = Maps.newHashMap();
                tags.put(ConfigTools.YOUXIA_MANAGED_TAG, tagname);
                return tags;
            }

            @Override
            public int compareTo(ResourceMetadata<ComputeType> o) {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public String getHostname() {
                throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods, choose Tools |
                                                                               // Templates.
            }

            @Override
            public String getGroup() {
                throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods, choose Tools |
                                                                               // Templates.
            }

            @Override
            public Hardware getHardware() {
                throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods, choose Tools |
                                                                               // Templates.
            }

            @Override
            public String getImageId() {
                throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods, choose Tools |
                                                                               // Templates.
            }

            @Override
            public OperatingSystem getOperatingSystem() {
                throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods, choose Tools |
                                                                               // Templates.
            }

            @Override
            public int getLoginPort() {
                throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods, choose Tools |
                                                                               // Templates.
            }

            @Override
            public LoginCredentials getCredentials() {
                throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods, choose Tools |
                                                                               // Templates.
            }

            @Override
            public Set<String> getPublicAddresses() {
                return Sets.newHashSet("123.123.123.123");
            }

            @Override
            public Set<String> getPrivateAddresses() {
                throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods, choose Tools |
                                                                               // Templates.
            }

            @Override
            public NodeMetadata.Status getStatus() {
                throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods, choose Tools |
                                                                               // Templates.
            }

            @Override
            public String getBackendStatus() {
                throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods, choose Tools |
                                                                               // Templates.
            }
        };
        Set nodes = Sets.newHashSet();
        nodes.add(metadata);

        ComputeService mockService = mock(ComputeService.class);
        when(mockClient.getComputeService()).thenReturn(mockService);
        when(mockService.listNodes()).thenReturn(nodes);
    }

}
