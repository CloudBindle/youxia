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

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceLifecycleType;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.Tag;
import com.google.common.collect.Lists;
import io.cloudbindle.youxia.listing.AbstractInstanceListing.InstanceDescriptor;
import io.cloudbindle.youxia.util.ConfigTools;
import io.cloudbindle.youxia.util.Constants;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import static org.easymock.EasyMock.expect;
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
@PrepareForTest({ ConfigTools.class, HierarchicalINIConfiguration.class, AwsListing.class })
public class AwsListingTest {

    /**
     * Test of getInstances method, of class AwsListing.
     *
     * @throws java.lang.Exception
     */
    @Test
    public void testNoInstances() throws Exception {
        mockStatic(ConfigTools.class);
        AmazonEC2Client mockClient = mock(AmazonEC2Client.class);
        HierarchicalINIConfiguration mockConfig = mock(HierarchicalINIConfiguration.class);
        expect(ConfigTools.getYouxiaConfig()).andReturn(mockConfig);
        expect(ConfigTools.getEC2Client()).andReturn(mockClient);
        when(mockConfig.getString(ConfigTools.YOUXIA_MANAGED_TAG)).thenReturn("dummy_tag");
        when(mockClient.describeInstances()).thenReturn(new DescribeInstancesResult());

        replayAll();

        AwsListing instance = new AwsListing();
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
        AmazonEC2Client mockClient = mock(AmazonEC2Client.class);
        HierarchicalINIConfiguration mockConfig = mock(HierarchicalINIConfiguration.class);
        expect(ConfigTools.getYouxiaConfig()).andReturn(mockConfig);
        expect(ConfigTools.getEC2Client()).andReturn(mockClient);
        when(mockConfig.getString(ConfigTools.YOUXIA_MANAGED_TAG)).thenReturn("dummy_tag");

        setupReservationsWithTag(mockClient, "incorrect_tag");

        replayAll();

        AwsListing awListing = new AwsListing();
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
        AmazonEC2Client mockClient = mock(AmazonEC2Client.class);
        HierarchicalINIConfiguration mockConfig = mock(HierarchicalINIConfiguration.class);
        expect(ConfigTools.getYouxiaConfig()).andReturn(mockConfig);
        expect(ConfigTools.getEC2Client()).andReturn(mockClient);
        when(mockConfig.getString(ConfigTools.YOUXIA_MANAGED_TAG)).thenReturn("dummy_tag");

        setupReservationsWithTag(mockClient, "dummy_tag");

        replayAll();

        AwsListing awListing = new AwsListing();
        Map<String, InstanceDescriptor> result = awListing.getInstances();
        Assert.assertTrue("result size incorrect " + result.size(), result.size() == 2);
        int[] typesSeen = new int[2];
        Arrays.fill(typesSeen, 0);
        for (InstanceDescriptor desc : result.values()) {
            if (desc.isSpotInstance()) {
                typesSeen[0] += 1;
            } else {
                typesSeen[1] += 1;
            }
        }
        Assert.assertTrue("did not see proper numbers of spot and on-demand instances", typesSeen[0] == 1 && typesSeen[1] == 1);

        verifyAll();
    }

    private void setupReservationsWithTag(AmazonEC2Client mockClient, String tagname) throws AmazonClientException {
        Reservation reservation = new Reservation();

        Instance instance0 = new Instance();
        instance0.setInstanceId("randomID0");
        instance0.setInstanceLifecycle(InstanceLifecycleType.Spot);
        List<Tag> tags = Lists.newArrayList();
        tags.add(new Tag(ConfigTools.YOUXIA_MANAGED_TAG, tagname));
        tags.add(new Tag(Constants.STATE_TAG, Constants.STATE.READY.toString()));
        instance0.setTags(tags);
        instance0.setPublicIpAddress("123.123.123.123");

        Instance instance1 = new Instance();
        instance1.setInstanceId("randomID1");
        List<Tag> tags1 = Lists.newArrayList();
        tags1.add(new Tag(ConfigTools.YOUXIA_MANAGED_TAG, tagname));
        tags1.add(new Tag(Constants.STATE_TAG, Constants.STATE.READY.toString()));
        instance1.setTags(tags1);
        instance1.setPublicIpAddress("123.123.123.124");

        Instance instance2 = new Instance();
        instance0.setInstanceId("randomID2");
        List<Tag> tags2 = Lists.newArrayList();
        tags2.add(new Tag(ConfigTools.YOUXIA_MANAGED_TAG, tagname));
        tags2.add(new Tag(Constants.STATE_TAG, Constants.STATE.MARKED_FOR_DEATH.toString()));
        instance2.setTags(tags2);
        instance2.setPublicIpAddress("123.123.123.125");

        List<Instance> instances = Lists.newArrayList();
        instances.add(instance0);
        instances.add(instance1);
        instances.add(instance2);
        reservation.setInstances(instances);
        when(mockClient.describeInstances()).thenReturn(new DescribeInstancesResult().withReservations(reservation));
    }

}
