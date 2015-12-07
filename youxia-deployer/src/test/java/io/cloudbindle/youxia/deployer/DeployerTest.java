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
package io.cloudbindle.youxia.deployer;

import java.util.Map;

import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.amazonaws.services.ec2.AmazonEC2Client;
import com.google.common.collect.Maps;

import io.cloudbindle.youxia.listing.AbstractInstanceListing.InstanceDescriptor;
import io.cloudbindle.youxia.listing.AwsListing;
import io.cloudbindle.youxia.listing.ListingFactory;
import io.cloudbindle.youxia.listing.OpenStackJCloudsListing;
import io.cloudbindle.youxia.util.ConfigTools;

import static org.easymock.EasyMock.expect;
import static org.powermock.api.easymock.PowerMock.createMockAndExpectNew;
import static org.powermock.api.easymock.PowerMock.expectNew;
import static org.powermock.api.easymock.PowerMock.mockStatic;
import static org.powermock.api.easymock.PowerMock.replay;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 *
 * @author dyuen
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ ConfigTools.class, Deployer.class, ListingFactory.class })
public class DeployerTest {

    public DeployerTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of main method, of class Deployer.
     *
     * @throws java.lang.Exception
     */
    @Test(expected = RuntimeException.class)
    public void testHelp() throws Exception {
        System.out.println("help");
        String[] args = { "--help" };
        Deployer.main(args);
    }

    /**
     * Do not provision due to maximum number of nodes reached.
     *
     * @throws Exception
     */
    @Test
    public void testListingNoProvision() throws Exception {
        mockStatic(ConfigTools.class);
        AmazonEC2Client mockClient = mock(AmazonEC2Client.class);
        AwsListing listing = createMockAndExpectNew(AwsListing.class);
        HierarchicalINIConfiguration mockConfig = mock(HierarchicalINIConfiguration.class);
        when(mockConfig.containsKey(Deployer.DEPLOYER_INSTANCE_TYPE)).thenReturn(true, true);
        when(mockConfig.getString(Deployer.DEPLOYER_INSTANCE_TYPE)).thenReturn("m1.xlarge", "m1.xlarge");
        expect(ConfigTools.getYouxiaConfig()).andReturn(mockConfig).anyTimes();
        expect(ConfigTools.getEC2Client()).andReturn(mockClient);
        expectNew(AwsListing.class).andReturn(listing);
        Map<String, InstanceDescriptor> map = Maps.newTreeMap();
        map.put("key1", new InstanceDescriptor("n1", "value1", false));
        map.put("key2", new InstanceDescriptor("n2", "value2", false));
        expect(listing.getInstances()).andReturn(map);
        String[] args = { "--total-nodes-num", "2", "--max-spot-price", "2", "--batch-size", "5", "--ansible-playbook", "test-book.yml" };

        replay(HierarchicalINIConfiguration.class);
        replay(ConfigTools.class);
        replay(listing, AwsListing.class);

        Deployer.main(args);
    }

    @Test
    public void testListingNoProvisionOpenStack() throws Exception {
        mockStatic(ConfigTools.class);
        AmazonEC2Client mockClient = mock(AmazonEC2Client.class);
        OpenStackJCloudsListing listing = createMockAndExpectNew(OpenStackJCloudsListing.class);
        HierarchicalINIConfiguration mockConfig = mock(HierarchicalINIConfiguration.class);
        when(mockConfig.containsKey(Deployer.DEPLOYER_OPENSTACK_FLAVOR)).thenReturn(true, true);
        when(mockConfig.getString(Deployer.DEPLOYER_OPENSTACK_FLAVOR)).thenReturn("m1.xlarge", "m1.xlarge");
        expect(ConfigTools.getYouxiaConfig()).andReturn(mockConfig).anyTimes();
        expect(ConfigTools.getEC2Client()).andReturn(mockClient);
        expectNew(OpenStackJCloudsListing.class).andReturn(listing);
        Map<String, InstanceDescriptor> map = Maps.newTreeMap();
        map.put("key1", new InstanceDescriptor("n1", "value1", false));
        map.put("key2", new InstanceDescriptor("n2", "value2", false));
        expect(listing.getInstances()).andReturn(map);
        String[] args = { "--openstack", "--total-nodes-num", "2", "--max-spot-price", "2", "--batch-size", "5", "--ansible-playbook",
                "test-book.yml" };

        replay(HierarchicalINIConfiguration.class);
        replay(ConfigTools.class);
        replay(listing, OpenStackJCloudsListing.class);

        Deployer.main(args);
    }

    /**
     * Do not provision since no spot instances available and on-demand instances reached.
     *
     * @throws Exception
     */
    @Test
    public void testListingNoProvisionDueToCapacityReached() throws Exception {
        mockStatic(ConfigTools.class);
        AmazonEC2Client mockClient = mock(AmazonEC2Client.class);
        AwsListing listing = createMockAndExpectNew(AwsListing.class);
        HierarchicalINIConfiguration mockConfig = mock(HierarchicalINIConfiguration.class);
        when(mockConfig.containsKey(Deployer.DEPLOYER_OPENSTACK_FLAVOR)).thenReturn(true, true);
        when(mockConfig.getString(Deployer.DEPLOYER_OPENSTACK_FLAVOR)).thenReturn("m1.xlarge", "m1.xlarge");
        expect(ConfigTools.getYouxiaConfig()).andReturn(mockConfig).anyTimes();
        expect(ConfigTools.getEC2Client()).andReturn(mockClient).anyTimes();
        expectNew(AwsListing.class).andReturn(listing).anyTimes();
        when(mockConfig.getStringArray(ConfigTools.YOUXIA_ZONE)).thenReturn(new String[] {});

        Map<String, InstanceDescriptor> map = Maps.newTreeMap();
        map.put("key1", new InstanceDescriptor("n1", "value1", false));
        map.put("key2", new InstanceDescriptor("n2", "value2", false));
        expect(listing.getInstances()).andReturn(map).anyTimes();
        String[] args = { "--total-nodes-num", "4", "--max-spot-price", "2", "--batch-size", "5", "--ansible-playbook", "test-book.yml",
                "--max-on-demand", "2" };

        replay(HierarchicalINIConfiguration.class);
        replay(ConfigTools.class);
        replay(listing, AwsListing.class);

        Deployer.main(args);
    }

}
