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
package io.cloudbindle.youxia.sensu.client;

import com.google.common.collect.Lists;
import io.cloudbindle.youxia.sensu.api.Client;
import io.cloudbindle.youxia.sensu.api.ClientHistory;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.localserver.LocalTestServer;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.modules.junit4.PowerMockRunner;

/**
 * 
 * @author dyuen
 */
@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.net.ssl.*")
public class SensuClientTest {
    private LocalTestServer server;

    @Before
    public void setUp() throws Exception {
        this.server = new LocalTestServer(null, null);

        // handle clients
        HttpRequestHandler handler = new HttpRequestHandler() {
            @Override
            public void handle(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException, IOException {
                BasicHttpEntity entity = new BasicHttpEntity();
                response.setEntity(entity);
                InputStream resourceAsStream = SensuClient.class.getResourceAsStream("clients.json");
                entity.setContent(resourceAsStream);
            }
        };
        server.register("/clients", handler);

        // handle client history
        HttpRequestHandler handler2 = new HttpRequestHandler() {
            @Override
            public void handle(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException, IOException {
                BasicHttpEntity entity = new BasicHttpEntity();
                response.setEntity(entity);
                InputStream resourceAsStream = SensuClient.class.getResourceAsStream("client_history.json");
                entity.setContent(resourceAsStream);
            }
        };
        server.register("/clients/random_name/history", handler2);

        server.start();
    }

    @After
    public void tearDown() throws Exception {
        server.stop();
    }

    /**
     * Test of getClients method, of class SensuClient.
     */
    @Test
    public void testGetClients() {
        System.out.println("getClients");
        SensuClient instance = new SensuClient(server.getServiceAddress().getHostName(), server.getServiceAddress().getPort(), "blah",
                "blah");
        List<Client> result = instance.getClients();
        Assert.assertTrue("no clients found", result.size() == 2);

        Client client1 = new Client();
        client1.setName("client_1");
        client1.setAddress("192.168.0.2");
        client1.setSubscriptions(new String[] { "chef-client", "sensu-server" });
        client1.setTimestamp(1324674972);

        Client client2 = new Client();
        client2.setName("client_2");
        client2.setAddress("192.168.0.3");
        client2.setSubscriptions(new String[] { "chef-client", "webserver", "memcached" });
        client2.setTimestamp(1324674956);

        List<Client> newArrayList = Lists.newArrayList(client1, client2);
        Assert.assertEquals(newArrayList, result);
    }

    /**
     * Test of getClientHistory method, of class SensuClient.
     */
    @Test
    public void testGetClientHistory() {
        System.out.println("getClientHistory");
        SensuClient instance = new SensuClient(server.getServiceAddress().getHostName(), server.getServiceAddress().getPort(), "blah",
                "blah");
        List<ClientHistory> result = instance.getClientHistory("random_name");
        Assert.assertTrue("incorrect history found", result.size() == 2);

        ClientHistory h1 = new ClientHistory();
        h1.setCheck("chef_client_process");
        h1.setHistory(new int[] { 0, 1 });
        h1.setLastExecution(1370725352L);
        h1.setLastStatus(1);

        ClientHistory h2 = new ClientHistory();
        h2.setCheck("keepalive");
        h2.setHistory(new int[] { 0, 0, 0 });
        h2.setLastExecution(1370725351L);
        h2.setLastStatus(0);

        List<ClientHistory> newArrayList = Lists.newArrayList(h1, h2);
        Assert.assertEquals(newArrayList, result);
    }

}
