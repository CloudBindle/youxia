/*
 * Copyright (C) 2015 CloudBindle
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
package io.cloudbindle.youxia.azure.resourceManagerWrapper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.microsoft.aad.adal4j.AuthenticationContext;
import com.microsoft.aad.adal4j.AuthenticationResult;
import io.cloudbindle.youxia.util.ConfigTools;
import static io.cloudbindle.youxia.util.ConfigTools.YOUXIA_AZURE_SUBSCRIPTION_ID;
import io.cloudbindle.youxia.util.Log;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.naming.ServiceUnavailableException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;

/**
 * This wraps calls to the REST-based Resource Manager service on Azure.
 *
 * @author dyuen
 */
public class AzureResourceManagerClient {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    public static final String AZURE_LOGIN_AUTHORITY = "login.windows.net";
    public static final String AZURE_MANAGEMENT_DOMAIN = "management.azure.com";
    public static final String AZURE_API_VERSION = "2015-01-01";

    private AuthenticationResult getAccessTokenFromUserCredentials() throws Exception {
        // this user is an "organization user" microsoft accounts do not work
        // see http://blog.baslijten.com/create-an-organizational-account-to-administrate-azure-when-having-a-microsoft-account/
        // the password is set by logging in as the organization user (which will prompt you to set the password)
        HierarchicalINIConfiguration youxiaConfig = ConfigTools.getYouxiaConfig();
        String username = youxiaConfig.getString(ConfigTools.YOUXIA_AZURE_ACTIVE_DIRECTORY_USERNAME);
        String password = youxiaConfig.getString(ConfigTools.YOUXIA_AZURE_ACTIVE_DIRECTORY_PASSWORD);
        // the end part of this is from the URL of the active directory page, seriously
        String authority = "https://" + AZURE_LOGIN_AUTHORITY + "/"
                + youxiaConfig.getString(ConfigTools.YOUXIA_AZURE_ACTIVE_DIRECTORY_TENANT_ID);
        // this is listed under the applications tab and is listed as CLIENT ID
        String clientID = youxiaConfig.getString(ConfigTools.YOUXIA_AZURE_ACTIVE_DIRECTORY_CLIENT_ID);

        AuthenticationContext context;
        AuthenticationResult result = null;
        ExecutorService service = null;
        try {
            service = Executors.newFixedThreadPool(1);
            context = new AuthenticationContext(authority, false, service);
            Future<AuthenticationResult> future = context.acquireToken("https://" + AZURE_MANAGEMENT_DOMAIN + "/", clientID, username,
                    password, null);
            result = future.get();
        } finally {
            service.shutdown();
        }

        if (result == null) {
            throw new ServiceUnavailableException("authentication result was null");
        }
        return result;
    }

    private String getEndpoint(String endpoint) throws Exception {
        String accessToken;
        HierarchicalINIConfiguration youxiaConfig = ConfigTools.getYouxiaConfig();
        AuthenticationResult accessTokenFromUserCredentials = getAccessTokenFromUserCredentials();
        accessToken = accessTokenFromUserCredentials.getAccessToken();

        HttpClient client = new DefaultHttpClient();
        HttpGet request = new HttpGet("https://" + AZURE_MANAGEMENT_DOMAIN + "/subscriptions/"
                + youxiaConfig.getString(YOUXIA_AZURE_SUBSCRIPTION_ID) + "/" + endpoint + "?api-version=" + AZURE_API_VERSION);
        request.addHeader("Authorization", "Bearer " + accessToken);
        HttpResponse response = client.execute(request);
        if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            throw new RuntimeException("status code : " + response.getStatusLine().getStatusCode());
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = rd.readLine()) != null) {
                builder.append(line);
            }

        }
        return builder.toString();
    }

    private String patchEndpoint(String endpoint, String payload) throws IOException {
        String accessToken;
        HierarchicalINIConfiguration youxiaConfig = ConfigTools.getYouxiaConfig();
        try {
            AuthenticationResult accessTokenFromUserCredentials = getAccessTokenFromUserCredentials();
            accessToken = accessTokenFromUserCredentials.getAccessToken();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        HttpClient client = new DefaultHttpClient();
        HttpPatch request = new HttpPatch("https://" + AZURE_MANAGEMENT_DOMAIN + "/subscriptions/"
                + youxiaConfig.getString(YOUXIA_AZURE_SUBSCRIPTION_ID) + "/" + endpoint + "?api-version=" + AZURE_API_VERSION);
        request.setHeader("Content-Type", ContentType.APPLICATION_JSON.toString());
        request.setEntity(new StringEntity(payload, ContentType.APPLICATION_JSON));
        request.addHeader("Authorization", "Bearer " + accessToken);
        HttpResponse response = client.execute(request);
        if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            throw new RuntimeException("status code : " + response.getStatusLine().getStatusCode() + " "
                    + response.getStatusLine().getReasonPhrase());
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = rd.readLine()) != null) {
                builder.append(line);
            }

        }
        return builder.toString();
    }

    public ResourceGroupList getResourceGroupList() throws Exception {
        String endpoint = getEndpoint("resourcegroups");
        ResourceGroupList fromJson = gson.fromJson(endpoint, ResourceGroupList.class);
        return fromJson;
    }

    public ResourceGroup getResourceGroup(String name) throws Exception {
        String endpoint = getEndpoint("resourcegroups/" + name);
        ResourceGroup fromJson = gson.fromJson(endpoint, ResourceGroup.class);
        return fromJson;
    }

    public ResourceGroup patchResourceGroup(String name, Map<String, String> newTags) throws Exception {
        TagPatch patch = new TagPatch(newTags);
        String endpoint = patchEndpoint("resourcegroups/" + name, gson.toJson(patch));
        ResourceGroup fromJson = gson.fromJson(endpoint, ResourceGroup.class);
        return fromJson;
    }

    public static void main(String[] args) {
        String targetName = "iron-head-4bf54097-1561-445e-b494-ee1d0f7035d0";
        AzureResourceManagerClient client = new AzureResourceManagerClient();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            Log.info("List of resource groups");
            ResourceGroupList resourceGroupList = client.getResourceGroupList();
            String json = gson.toJson(resourceGroupList);
            Log.info(json);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        try {
            Log.info("Specific resource groups");
            ResourceGroup fromJson = client.getResourceGroup(targetName);
            String json = gson.toJson(fromJson);
            Log.info(json);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        try {
            Log.info("Update specific resource group");
            ResourceGroup initialGroup = client.getResourceGroup(targetName);
            Map<String, String> tags = initialGroup.getTags();
            tags.put(String.valueOf(System.currentTimeMillis()), "funky_new_tag");
            ResourceGroup updateResourceGroup = client.patchResourceGroup(targetName, tags);
            String json = gson.toJson(updateResourceGroup);
            Log.info(json);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
