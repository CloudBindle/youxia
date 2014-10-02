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
package io.cloudbindle.youxia.amazonaws;

import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.RequestSpotInstancesRequest;
import com.amazonaws.services.ec2.model.RequestSpotInstancesResult;
import com.amazonaws.services.ec2.model.SpotInstanceRequest;
import com.amazonaws.services.ec2.model.Tag;
import com.google.common.collect.Lists;
import io.cloudbindle.youxia.util.ConfigTools;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.isA;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.isNotNull;
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
@PrepareForTest({ ConfigTools.class, Requests.class, AmazonEC2Client.class })
public class RequestsTest {
    private AmazonEC2Client mockClient;

    public RequestsTest() {
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
     * Test of submitRequests method, of class Requests.
     */
    @Test
    public void testSubmitRequests() throws Exception {

        mockStatic(ConfigTools.class);
        this.mockClient = mock(AmazonEC2Client.class);
        HierarchicalINIConfiguration mockConfig = mock(HierarchicalINIConfiguration.class);
        expect(ConfigTools.getYouxiaConfig()).andReturn(mockConfig);
        expect(ConfigTools.getEC2Client()).andReturn(mockClient);
        when(mockConfig.getString(ConfigTools.YOUXIA_MANAGED_TAG)).thenReturn("dummy_tag");
        RequestSpotInstancesResult result = mock(RequestSpotInstancesResult.class);
        SpotInstanceRequest resultRequest = mock(SpotInstanceRequest.class);
        when(mockClient.requestSpotInstances(isNotNull(RequestSpotInstancesRequest.class))).thenReturn(result);
        List<SpotInstanceRequest> list = Lists.newArrayList();
        list.add(resultRequest);
        when(result.getSpotInstanceRequests()).thenReturn(list);
        when(resultRequest.getSpotInstanceRequestId()).thenReturn("funkyId");

        replayAll();

        System.out.println("submitRequests");
        Requests instance = new Requests("instanceType", "amiId", "1.0", "securityGroup", 5, "keyName");
        instance.submitRequests();

        verifyAll();
    }

    // /**
    // * Test of launchOnDemand method, of class Requests.
    // */
    // @Test
    // public void testLaunchOnDemand() {
    // System.out.println("launchOnDemand");
    // Requests instance = null;
    // instance.launchOnDemand();
    // // TODO review the generated test code and remove the default call to fail.
    // fail("The test case is a prototype.");
    // }
    //
    // /**
    // * Test of areAnyOpen method, of class Requests.
    // */
    // @Test
    // public void testAreAnyOpen() {
    // System.out.println("areAnyOpen");
    // Requests instance = null;
    // boolean expResult = false;
    // boolean result = instance.areAnyOpen();
    // assertEquals(expResult, result);
    // // TODO review the generated test code and remove the default call to fail.
    // fail("The test case is a prototype.");
    // }
    //
    // /**
    // * Test of tagInstances method, of class Requests.
    // */
    // @Test
    // public void testTagInstances() {
    // System.out.println("tagInstances");
    // List<Tag> tags = null;
    // Requests instance = null;
    // instance.tagInstances(tags);
    // // TODO review the generated test code and remove the default call to fail.
    // fail("The test case is a prototype.");
    // }
    //
    // /**
    // * Test of tagRequests method, of class Requests.
    // */
    // @Test
    // public void testTagRequests() {
    // System.out.println("tagRequests");
    // List<Tag> tags = null;
    // Requests instance = null;
    // instance.tagRequests(tags);
    // // TODO review the generated test code and remove the default call to fail.
    // fail("The test case is a prototype.");
    // }
    //
    // /**
    // * Test of cleanup method, of class Requests.
    // */
    // @Test
    // public void testCleanup() {
    // System.out.println("cleanup");
    // Requests instance = null;
    // instance.cleanup();
    // // TODO review the generated test code and remove the default call to fail.
    // fail("The test case is a prototype.");
    // }
    //
    // /**
    // * Test of setRequestType method, of class Requests.
    // */
    // @Test
    // public void testSetRequestType() {
    // System.out.println("setRequestType");
    // String type = "";
    // Requests instance = null;
    // instance.setRequestType(type);
    // // TODO review the generated test code and remove the default call to fail.
    // fail("The test case is a prototype.");
    // }
    //
    // /**
    // * Test of setValidPeriod method, of class Requests.
    // */
    // @Test
    // public void testSetValidPeriod() {
    // System.out.println("setValidPeriod");
    // Date from = null;
    // Date to = null;
    // Requests instance = null;
    // instance.setValidPeriod(from, to);
    // // TODO review the generated test code and remove the default call to fail.
    // fail("The test case is a prototype.");
    // }
    //
    // /**
    // * Test of setLaunchGroup method, of class Requests.
    // */
    // @Test
    // public void testSetLaunchGroup() {
    // System.out.println("setLaunchGroup");
    // String launchGroup = "";
    // Requests instance = null;
    // instance.setLaunchGroup(launchGroup);
    // // TODO review the generated test code and remove the default call to fail.
    // fail("The test case is a prototype.");
    // }
    //
    // /**
    // * Test of setAvailabilityZoneGroup method, of class Requests.
    // */
    // @Test
    // public void testSetAvailabilityZoneGroup() {
    // System.out.println("setAvailabilityZoneGroup");
    // String azGroup = "";
    // Requests instance = null;
    // instance.setAvailabilityZoneGroup(azGroup);
    // // TODO review the generated test code and remove the default call to fail.
    // fail("The test case is a prototype.");
    // }
    //
    // /**
    // * Test of setAvailabilityZone method, of class Requests.
    // */
    // @Test
    // public void testSetAvailabilityZone() {
    // System.out.println("setAvailabilityZone");
    // String az = "";
    // Requests instance = null;
    // instance.setAvailabilityZone(az);
    // // TODO review the generated test code and remove the default call to fail.
    // fail("The test case is a prototype.");
    // }
    //
    // /**
    // * Test of setPlacementGroup method, of class Requests.
    // */
    // @Test
    // public void testSetPlacementGroup() {
    // System.out.println("setPlacementGroup");
    // String pg = "";
    // Requests instance = null;
    // instance.setPlacementGroup(pg);
    // // TODO review the generated test code and remove the default call to fail.
    // fail("The test case is a prototype.");
    // }
    //
    // /**
    // * Test of setDeleteOnTermination method, of class Requests.
    // */
    // @Test
    // public void testSetDeleteOnTermination() {
    // System.out.println("setDeleteOnTermination");
    // boolean terminate = false;
    // Requests instance = null;
    // instance.setDeleteOnTermination(terminate);
    // // TODO review the generated test code and remove the default call to fail.
    // fail("The test case is a prototype.");
    // }
    //
    // /**
    // * Test of getNumInstances method, of class Requests.
    // */
    // @Test
    // public void testGetNumInstances() {
    // System.out.println("getNumInstances");
    // Requests instance = null;
    // int expResult = 0;
    // int result = instance.getNumInstances();
    // assertEquals(expResult, result);
    // // TODO review the generated test code and remove the default call to fail.
    // fail("The test case is a prototype.");
    // }
    //
    // /**
    // * Test of setNumInstances method, of class Requests.
    // */
    // @Test
    // public void testSetNumInstances() {
    // System.out.println("setNumInstances");
    // int numInstances = 0;
    // Requests instance = null;
    // instance.setNumInstances(numInstances);
    // // TODO review the generated test code and remove the default call to fail.
    // fail("The test case is a prototype.");
    // }
    //
    // /**
    // * Test of getInstanceIds method, of class Requests.
    // */
    // @Test
    // public void testGetInstanceIds() {
    // System.out.println("getInstanceIds");
    // Requests instance = null;
    // ArrayList<String> expResult = null;
    // ArrayList<String> result = instance.getInstanceIds();
    // assertEquals(expResult, result);
    // // TODO review the generated test code and remove the default call to fail.
    // fail("The test case is a prototype.");
    // }
    //
    // /**
    // * Test of setInstanceIds method, of class Requests.
    // */
    // @Test
    // public void testSetInstanceIds() {
    // System.out.println("setInstanceIds");
    // ArrayList<String> instanceIds = null;
    // Requests instance = null;
    // instance.setInstanceIds(instanceIds);
    // // TODO review the generated test code and remove the default call to fail.
    // fail("The test case is a prototype.");
    // }

}
