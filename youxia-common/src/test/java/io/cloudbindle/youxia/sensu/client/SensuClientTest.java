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

import io.cloudbindle.youxia.sensu.api.Client;
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
    }

}
