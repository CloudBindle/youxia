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

import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceState;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.TerminateInstancesResult;
import com.amazonaws.services.simpledb.AmazonSimpleDBClient;
import com.amazonaws.services.simpledb.model.ListDomainsResult;
import com.amazonaws.services.simpledb.model.SelectRequest;
import com.amazonaws.services.simpledb.model.SelectResult;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.cloudbindle.youxia.listing.AbstractInstanceListing.InstanceDescriptor;
import io.cloudbindle.youxia.listing.AwsListing;
import io.cloudbindle.youxia.listing.ListingFactory;
import io.cloudbindle.youxia.listing.OpenStackJCloudsListing;
import io.cloudbindle.youxia.util.ConfigTools;
import io.cloudbindle.youxia.util.Constants;
import io.seqware.common.model.WorkflowRunStatus;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.sourceforge.seqware.common.metadata.MetadataFactory;
import net.sourceforge.seqware.common.metadata.MetadataWS;
import net.sourceforge.seqware.common.model.WorkflowRun;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.localserver.LocalTestServer;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.isA;
import org.jclouds.collect.IterableWithMarker;
import org.jclouds.collect.PagedIterable;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.openstack.nova.v2_0.NovaApi;
import org.jclouds.openstack.nova.v2_0.domain.Server;
import org.jclouds.openstack.nova.v2_0.features.ServerApi;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.Matchers.isNotNull;
import static org.powermock.api.easymock.PowerMock.createMock;
import static org.powermock.api.easymock.PowerMock.createMockAndExpectNew;
import static org.powermock.api.easymock.PowerMock.mockStatic;
import static org.powermock.api.easymock.PowerMock.replayAll;
import static org.powermock.api.easymock.PowerMock.verifyAll;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

/**
 *
 * @author dyuen
 */
@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.net.ssl.*")
@PrepareForTest({ ConfigTools.class, Reaper.class, MetadataFactory.class, ListingFactory.class })
public class ReaperTest {
    private LocalTestServer server;

    public ReaperTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() throws Exception {
        mockStatic(ConfigTools.class);
        mockStatic(MetadataFactory.class);
        this.server = new LocalTestServer(null, null);
        // handle clients
        HttpRequestHandler handler = new HttpRequestHandler() {
            @Override
            public void handle(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException, IOException {
                BasicHttpEntity entity = new BasicHttpEntity();
                response.setEntity(entity);
                InputStream resourceAsStream = Reaper.class.getResourceAsStream("clients.json");
                entity.setContent(resourceAsStream);
            }
        };
        server.register("/clients", handler);
        server.start();
    }

    @After
    public void tearDown() throws Exception {
        server.stop();
    }

    /**
     * Test of main method, of class Reaper.
     *
     * @throws java.lang.Exception
     */
    @Test(expected = RuntimeException.class)
    public void testHelp() throws Exception {
        System.out.println("help");
        String[] args = { "--help" };
        Reaper.main(args);
    }

    @Test
    public void testNoReapingOpenStack() throws Exception {
        String[] args = { "--kill-limit", "1", "--openstack" };
        mockOutConfig();
        NovaApi novaApi = mock(NovaApi.class);
        expect(ConfigTools.getNovaApi()).andReturn(novaApi).anyTimes();

        replayAll();
        Reaper.main(args);
        verifyAll();
    }

    @Test
    public void testNoReaping() throws Exception {
        String[] args = { "--kill-limit", "1" };
        mockOutConfig();
        AwsListing listing1 = createMockAndExpectNew(AwsListing.class);
        Map<String, InstanceDescriptor> result1 = Maps.newHashMap();
        expect(listing1.getInstances()).andReturn(result1);

        replayAll();
        Reaper.main(args);
        verifyAll();
    }

    @Test
    public void testReaping() throws Exception {
        String[] args = { "--kill-limit", "1" };
        AmazonEC2Client client = mockOutConfig();
        AwsListing listing1 = createMockAndExpectNew(AwsListing.class);
        Map<String, InstanceDescriptor> result1 = Maps.newHashMap();
        result1.put("funky_id", new InstanceDescriptor(server.getServiceAddress().getHostName()));
        expect(listing1.getInstances()).andReturn(result1);
        Reservation reservation = new Reservation();
        Instance instance = new Instance();
        instance.setInstanceId("randomID");
        List<Tag> tags = Lists.newArrayList();
        tags.add(new Tag(ConfigTools.YOUXIA_MANAGED_TAG, "shaolin"));
        instance.setTags(tags);
        instance.setPublicIpAddress(server.getServiceAddress().getHostName());
        List<Instance> instances = Lists.newArrayList();
        instances.add(instance);
        reservation.setInstances(instances);
        when(client.describeInstances(isNotNull(DescribeInstancesRequest.class))).thenReturn(
                new DescribeInstancesResult().withReservations(reservation));
        MetadataWS metadata = createMock(MetadataWS.class);
        expect(MetadataFactory.getWS(isA(Map.class))).andReturn(metadata);
        List<WorkflowRun> list = Lists.newArrayList();
        list.add(new WorkflowRun());
        expect(metadata.getWorkflowRunsByStatus(isA(WorkflowRunStatus.class))).andReturn(list).anyTimes();
        TerminateInstancesResult terminateInstancesResult = mock(TerminateInstancesResult.class);
        when(client.terminateInstances(isNotNull(TerminateInstancesRequest.class))).thenReturn(terminateInstancesResult);
        // here we describe terminated instances
        InstanceState state = new InstanceState();
        state.withName("terminated");
        instance.setState(state);
        when(client.describeInstances(isNotNull(DescribeInstancesRequest.class))).thenReturn(
                new DescribeInstancesResult().withReservations(reservation));

        replayAll();
        Reaper.main(args);
        verifyAll();
    }

    @Test
    public void testReapingOpenStack() throws Exception {
        String[] args = { "--kill-limit", "1", "--openstack" };
        AmazonEC2Client client = mockOutConfig();
        OpenStackJCloudsListing listing1 = createMockAndExpectNew(OpenStackJCloudsListing.class);
        ComputeServiceContext genericContext = mock(ComputeServiceContext.class);
        expect(ConfigTools.getGenericOpenStackApi()).andReturn(genericContext).anyTimes();
        ComputeService computeService = mock(ComputeService.class);
        when(genericContext.getComputeService()).thenReturn(computeService);
        NodeMetadata nodeMetadata = mock(NodeMetadata.class);
        when(computeService.getNodeMetadata(isNotNull(String.class))).thenReturn(nodeMetadata);
        Map<String, String> map = new HashMap<>();
        map.put(Constants.STATE_TAG, Constants.STATE.SETTING_UP.toString());
        when(nodeMetadata.getUserMetadata()).thenReturn(map);
        NovaApi api = mock(NovaApi.class);
        expect(ConfigTools.getNovaApi()).andReturn(api);
        ServerApi zoneApi = mock(ServerApi.class);
        when(api.getServerApiForZone(isNotNull(String.class))).thenReturn(zoneApi);
        PagedIterable<Server> pagedIterable = new PagedIterable<Server>() {
            @Override
            public Iterator<IterableWithMarker<Server>> iterator() {
                List<IterableWithMarker<Server>> list = new ArrayList<>();
                return list.iterator();
            }
        };
        when(zoneApi.listInDetail()).thenReturn(pagedIterable);

        Map<String, InstanceDescriptor> result1 = Maps.newHashMap();
        result1.put("funky_id", new InstanceDescriptor(server.getServiceAddress().getHostName()));
        expect(listing1.getInstances()).andReturn(result1);
        Reservation reservation = new Reservation();
        Instance instance = new Instance();
        instance.setInstanceId("randomID");
        List<Tag> tags = Lists.newArrayList();
        tags.add(new Tag(ConfigTools.YOUXIA_MANAGED_TAG, "shaolin"));
        instance.setTags(tags);
        instance.setPublicIpAddress(server.getServiceAddress().getHostName());
        List<Instance> instances = Lists.newArrayList();
        instances.add(instance);
        reservation.setInstances(instances);
        when(client.describeInstances(isNotNull(DescribeInstancesRequest.class))).thenReturn(
                new DescribeInstancesResult().withReservations(reservation));
        MetadataWS metadata = createMock(MetadataWS.class);
        // expect(MetadataFactory.getWS(isA(Map.class))).andReturn(metadata);
        List<WorkflowRun> list = Lists.newArrayList();
        list.add(new WorkflowRun());
        expect(metadata.getWorkflowRunsByStatus(isA(WorkflowRunStatus.class))).andReturn(list).anyTimes();
        TerminateInstancesResult terminateInstancesResult = mock(TerminateInstancesResult.class);
        when(client.terminateInstances(isNotNull(TerminateInstancesRequest.class))).thenReturn(terminateInstancesResult);
        // here we describe terminated instances
        InstanceState state = new InstanceState();
        state.withName("terminated");
        instance.setState(state);
        when(client.describeInstances(isNotNull(DescribeInstancesRequest.class))).thenReturn(
                new DescribeInstancesResult().withReservations(reservation));

        replayAll();
        Reaper.main(args);
        verifyAll();
    }

    private AmazonEC2Client mockOutConfig() {
        HierarchicalINIConfiguration mockConfig = mock(HierarchicalINIConfiguration.class);
        AmazonEC2Client client = mock(AmazonEC2Client.class);
        AmazonSimpleDBClient dbClient = mock(AmazonSimpleDBClient.class);
        expect(ConfigTools.getEC2Client()).andReturn(client).anyTimes();
        expect(ConfigTools.getSimpleDBClient()).andReturn(dbClient).anyTimes();
        // for now, we will return empty select requests
        when(dbClient.select(isNotNull(SelectRequest.class))).thenReturn(new SelectResult());
        when(dbClient.listDomains()).thenReturn(new ListDomainsResult());

        when(client.describeRegions()).thenReturn(null);
        expect(ConfigTools.getYouxiaConfig()).andReturn(mockConfig).anyTimes();
        when(mockConfig.getString(ConfigTools.YOUXIA_MANAGED_TAG)).thenReturn("shaolin");
        when(mockConfig.getString(ConfigTools.SEQWARE_REST_PASS)).thenReturn("password");
        when(mockConfig.getString(ConfigTools.SEQWARE_REST_USER)).thenReturn("username");
        when(mockConfig.getString(ConfigTools.SEQWARE_REST_PORT)).thenReturn(String.valueOf(server.getServiceAddress().getPort()));
        when(mockConfig.getString(ConfigTools.YOUXIA_SENSU_IP_ADDRESS))
                .thenReturn(String.valueOf(server.getServiceAddress().getHostName()));
        when(mockConfig.getInt(ConfigTools.YOUXIA_SENSU_PORT)).thenReturn(server.getServiceAddress().getPort());
        when(mockConfig.getString(ConfigTools.YOUXIA_SENSU_USERNAME)).thenReturn("username");
        when(mockConfig.getString(ConfigTools.YOUXIA_SENSU_PASSWORD)).thenReturn("password");
        when(mockConfig.getString(ConfigTools.YOUXIA_OPENSTACK_ZONE)).thenReturn("dance_dance_revolution_zone");
        return client;
    }
}
