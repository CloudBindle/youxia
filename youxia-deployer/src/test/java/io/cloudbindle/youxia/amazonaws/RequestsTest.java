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
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.RequestSpotInstancesRequest;
import com.amazonaws.services.ec2.model.RequestSpotInstancesResult;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.SpotInstanceRequest;
import com.google.common.collect.Lists;
import io.cloudbindle.youxia.util.ConfigTools;
import java.util.Date;
import java.util.List;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import static org.easymock.EasyMock.expect;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
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
        mockStatic(ConfigTools.class);
        this.mockClient = mock(AmazonEC2Client.class);
        HierarchicalINIConfiguration mockConfig = mock(HierarchicalINIConfiguration.class);
        expect(ConfigTools.getYouxiaConfig()).andReturn(mockConfig);
        expect(ConfigTools.getEC2Client()).andReturn(mockClient);
        when(mockConfig.getString(ConfigTools.YOUXIA_MANAGED_TAG)).thenReturn("dummy_tag");
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of submitRequests method, of class Requests.
     * 
     * @throws java.lang.Exception
     */
    @Test
    public void testSubmitRequests() throws Exception {
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

    /**
     * Test of launchOnDemand method, of class Requests.
     * 
     * @throws java.lang.Exception
     */
    @Test
    public void testLaunchOnDemand() throws Exception {
        RunInstancesResult runResult = mock(RunInstancesResult.class);
        when(mockClient.runInstances(isNotNull(RunInstancesRequest.class))).thenReturn(runResult);
        Reservation reservation = mock(Reservation.class);
        when(runResult.getReservation()).thenReturn(reservation);
        Instance instance = mock(Instance.class);
        when(reservation.getInstances()).thenReturn(Lists.newArrayList(instance));
        when(instance.getInstanceId()).thenReturn("42");

        replayAll();

        System.out.println("launchOnDemand");
        Requests requests = new Requests("instanceType", "amiId", "1.0", "securityGroup", 5, "keyName");
        requests.launchOnDemand();

        verifyAll();

        Assert.assertTrue("instance id is incorrect", requests.getInstanceIds().equals(Lists.newArrayList("42")));
    }

    //
    // /**
    // * Test of areAnyOpen method, of class Requests.
    // */
    // @Test
    // public void testAreAnyOpen() {
    // System.out.println("areAnyOpen");
    // Requests requests = new Requests("instanceType", "amiId", "1.0", "securityGroup", 5, "keyName");
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
    // Requests requests = new Requests("instanceType", "amiId", "1.0", "securityGroup", 5, "keyName");
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
    // Requests requests = new Requests("instanceType", "amiId", "1.0", "securityGroup", 5, "keyName");
    // instance.tagRequests(tags);
    // // TODO review the generated test code and remove the default call to fail.
    // fail("The test case is a prototype.");
    // }
    //
    /**
     * Test of cleanup method, of class Requests.
     * 
     * @throws java.lang.Exception
     */
    @Test
    public void testCleanup() throws Exception {
        System.out.println("cleanup");
        Requests requests = new Requests("instanceType", "amiId", "1.0", "securityGroup", 5, "keyName");
        requests.cleanup();
    }

    /**
     * Test of setRequestType method, of class Requests.
     * 
     * @throws java.lang.Exception
     */
    @Test
    public void testSetRequestType() throws Exception {
        System.out.println("setRequestType");
        String type = "";
        Requests requests = new Requests("instanceType", "amiId", "1.0", "securityGroup", 5, "keyName");
        requests.setRequestType(type);

    }

    /**
     * Test of setValidPeriod method, of class Requests.
     * 
     * @throws java.lang.Exception
     */
    @Test
    public void testSetValidPeriod() throws Exception {
        System.out.println("setValidPeriod");
        Date from = new Date();
        Date to = new Date();
        Requests requests = new Requests("instanceType", "amiId", "1.0", "securityGroup", 5, "keyName");
        requests.setValidPeriod(from, to);

    }

    /**
     * Test of setLaunchGroup method, of class Requests.
     * 
     * @throws java.lang.Exception
     */
    @Test
    public void testSetLaunchGroup() throws Exception {
        System.out.println("setLaunchGroup");
        String launchGroup = "";
        Requests requests = new Requests("instanceType", "amiId", "1.0", "securityGroup", 5, "keyName");
        requests.setLaunchGroup(launchGroup);
    }

    /**
     * Test of setAvailabilityZoneGroup method, of class Requests.
     * 
     * @throws java.lang.Exception
     */
    @Test
    public void testSetAvailabilityZoneGroup() throws Exception {
        System.out.println("setAvailabilityZoneGroup");
        String azGroup = "";
        Requests requests = new Requests("instanceType", "amiId", "1.0", "securityGroup", 5, "keyName");
        requests.setAvailabilityZoneGroup(azGroup);

    }

    /**
     * Test of setAvailabilityZone method, of class Requests.
     * 
     * @throws java.lang.Exception
     */
    @Test
    public void testSetAvailabilityZone() throws Exception {
        System.out.println("setAvailabilityZone");
        String az = "";
        Requests requests = new Requests("instanceType", "amiId", "1.0", "securityGroup", 5, "keyName");
        requests.setAvailabilityZone(az);
    }

    /**
     * Test of setPlacementGroup method, of class Requests.
     * 
     * @throws java.lang.Exception
     */
    @Test
    public void testSetPlacementGroup() throws Exception {
        System.out.println("setPlacementGroup");
        String pg = "";
        Requests requests = new Requests("instanceType", "amiId", "1.0", "securityGroup", 5, "keyName");
        requests.setPlacementGroup(pg);
    }

    /**
     * Test of setDeleteOnTermination method, of class Requests.
     * 
     * @throws java.lang.Exception
     */
    @Test
    public void testSetDeleteOnTermination() throws Exception {
        System.out.println("setDeleteOnTermination");
        boolean terminate = false;
        Requests requests = new Requests("instanceType", "amiId", "1.0", "securityGroup", 5, "keyName");
        requests.setDeleteOnTermination(terminate);
    }

    /**
     * Test of getNumInstances method, of class Requests.
     * 
     * @throws java.lang.Exception
     */
    @Test
    public void testGetNumInstances() throws Exception {
        System.out.println("getNumInstances");
        Requests requests = new Requests("instanceType", "amiId", "1.0", "securityGroup", 5, "keyName");
        int expResult = 0;
        int result = requests.getNumInstances();
    }

    /**
     * Test of setNumInstances method, of class Requests.
     * 
     * @throws java.lang.Exception
     */
    @Test
    public void testSetNumInstances() throws Exception {
        System.out.println("setNumInstances");
        int numInstances = 0;
        Requests requests = new Requests("instanceType", "amiId", "1.0", "securityGroup", 5, "keyName");
        requests.setNumInstances(numInstances);
    }

    /**
     * Test of getInstanceIds method, of class Requests.
     * 
     * @throws java.lang.Exception
     */
    @Test
    public void testGetInstanceIds() throws Exception {
        System.out.println("getInstanceIds");
        Requests requests = new Requests("instanceType", "amiId", "1.0", "securityGroup", 5, "keyName");
        List<String> expResult = Lists.newArrayList();
        List<String> result = requests.getInstanceIds();
        assertEquals(expResult, result);
    }

    /**
     * Test of setInstanceIds method, of class Requests.
     * 
     * @throws java.lang.Exception
     */
    @Test
    public void testSetInstanceIds() throws Exception {
        System.out.println("setInstanceIds");
        List<String> instanceIds = Lists.newArrayList();
        Requests requests = new Requests("instanceType", "amiId", "1.0", "securityGroup", 5, "keyName");
        requests.setInstanceIds(instanceIds);
    }

}
