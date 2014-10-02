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
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.cloudbindle.youxia.sensu.api.Client;
import io.cloudbindle.youxia.sensu.api.ClientHistory;
import io.cloudbindle.youxia.util.Log;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

/**
 * 
 * @author dyuen
 */
public class SensuClient {
    private final CloseableHttpClient httpclient;
    private final HttpClientContext context;
    private final URIBuilder builder;

    public SensuClient(String host, Integer port, String username, String password) {
        this.httpclient = HttpClients.createDefault();
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(new AuthScope(host, port), new UsernamePasswordCredentials(username, password));

        this.context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);
        this.builder = new URIBuilder().setScheme("http").setPort(port).setHost(host);
    }

    public List<Client> getClients() {
        Object xs = getXs("clients", Client[].class);
        return Lists.newArrayList((Client[]) xs);
    }

    public boolean deleteClient(String name) {
        return deleteX("clients/" + name);
    }

    public List<ClientHistory> getClientHistory(String name) {
        Object xs = getXs("clients/" + name + "/history", ClientHistory[].class);
        return Lists.newArrayList((ClientHistory[]) xs);
    }

    private boolean deleteX(String path) {
        URI uri;
        try {
            uri = builder.setPath("/" + path).build();
        } catch (URISyntaxException ex) {
            throw new RuntimeException(ex);
        }
        HttpDelete httpdelete = new HttpDelete(uri);
        Log.info("Deleting at " + httpdelete.toString());
        try (CloseableHttpResponse response = httpclient.execute(httpdelete, context)) {
            Log.info(response.toString());
            final int sensuSuccess = 202;
            return response.getStatusLine().getStatusCode() == sensuSuccess;
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Get an arbitrary class from the sensu api *
     * 
     * @param path
     * @param targetClass
     * @return
     */
    private Object getXs(String path, Class targetClass) {
        URI uri;
        try {
            uri = builder.setPath("/" + path).build();
        } catch (URISyntaxException ex) {
            throw new RuntimeException(ex);
        }
        HttpGet httpget = new HttpGet(uri);
        Log.info("Looking at " + httpget.toString());
        try (CloseableHttpResponse response = httpclient.execute(httpget, context)) {
            Log.info(response.toString());
            try (InputStreamReader reader = new InputStreamReader(response.getEntity().getContent(), StandardCharsets.UTF_8)) {
                Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).setPrettyPrinting()
                        .create();
                return gson.fromJson(reader, targetClass);
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

}
