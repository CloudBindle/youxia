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

package io.cloudbindle.youxia.util;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.auth.profile.ProfilesConfigFile;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.simpledb.AmazonSimpleDBClient;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Module;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.windowsazure.Configuration;
import com.microsoft.windowsazure.core.utils.KeyStoreType;
import com.microsoft.windowsazure.management.ManagementClient;
import com.microsoft.windowsazure.management.ManagementService;
import com.microsoft.windowsazure.management.compute.ComputeManagementClient;
import com.microsoft.windowsazure.management.compute.ComputeManagementService;
import com.microsoft.windowsazure.management.configuration.ManagementConfiguration;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.jclouds.ContextBuilder;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.openstack.nova.v2_0.NovaApi;
import org.jclouds.sshj.config.SshjSshClientModule;

/**
 *
 * @author dyuen
 */
public class ConfigTools {

    public static final String YOUXIA_SENSU_IP_ADDRESS = "youxia.sensu_ip_address";
    public static final String YOUXIA_AWS_SSH_KEY = "youxia.aws_ssh_key";
    public static final String YOUXIA_SENSU_PORT = "youxia.sensu_port";
    public static final String YOUXIA_SENSU_PASSWORD = "youxia.sensu_password";
    public static final String YOUXIA_SENSU_USERNAME = "youxia.sensu_username";
    public static final String YOUXIA_REGION = "youxia.region";
    public static final String YOUXIA_ZONE = "youxia.zone";
    public static final String YOUXIA_AWS_KEY_NAME = "youxia.aws_key_name";
    public static final String YOUXIA_OPENSTACK_KEY_NAME = "youxia.openstack_key_name";
    public static final String YOUXIA_OPENSTACK_SSH_KEY = "youxia.openstack_ssh_key";
    public static final String YOUXIA_OPENSTACK_USERNAME = "youxia.openstack_username";
    public static final String YOUXIA_OPENSTACK_PASSWORD = "youxia.openstack_password";
    public static final String YOUXIA_OPENSTACK_ENDPOINT = "youxia.openstack_endpoint";
    public static final String YOUXIA_OPENSTACK_REGION = "youxia.openstack_region";
    public static final String YOUXIA_OPENSTACK_ZONE = "youxia.openstack_zone";
    public static final String YOUXIA_MANAGED_TAG = "youxia.managed_tag";
    public static final String SEQWARE_REST_USER = "seqware.rest_user";
    public static final String SEQWARE_REST_PORT = "seqware.rest_port";
    public static final String SEQWARE_REST_ROOT = "seqware.rest_root";
    public static final String SEQWARE_REST_PASS = "seqware.rest_pass";
    public static final String YOUXIA_AZURE_SUBSCRIPTION_ID = "youxia.azure_subscription_id";
    public static final String YOUXIA_AZURE_KEYSTORE_LOCATION = "youxia.azure_keystore_location";
    public static final String YOUXIA_AZURE_KEYSTORE_PASSWORD = "youxia.azure_keystore_password";
    public static final String YOUXIA_AZURE_STORAGE_ACCOUNT_NAME = "youxia.azure_storage_account_name";
    public static final String YOUXIA_AZURE_STORAGE_ACCOUNT_KEY = "youxia.azure_storage_account_key";
    public static final String YOUXIA_AZURE_SSH_KEY = "youxia.azure_ssh_key";

    public static HierarchicalINIConfiguration getYouxiaConfig() {
        File configFile = new File(System.getProperty("user.home"), ".youxia/config");
        try {
            HierarchicalINIConfiguration config = new HierarchicalINIConfiguration(configFile);
            return config;
        } catch (ConfigurationException ex) {
            throw new RuntimeException("Could not read ~/.youxia/config");
        }
    }

    /**
     * The only information needed to create a client are security credentials - your AWS Access Key ID and Secret Access Key. All other
     * configuration, such as the service endpoints have defaults provided.
     *
     * Additional client parameters, such as proxy configuration, can be specified in an optional ClientConfiguration object when
     * constructing a client.
     *
     * @see com.amazonaws.auth.BasicAWSCredentials
     * @see com.amazonaws.auth.PropertiesCredentials
     * @see com.amazonaws.ClientConfiguration
     */
    private static AWSCredentialsProvider getAWSCredentialProvider() {
        /*
         * ProfileCredentialsProvider loads AWS security credentials from a .aws/config file in your home directory.
         * 
         * These same credentials are used when working with the AWS CLI.
         * 
         * You can find more information on the AWS profiles config file here:
         * http://docs.aws.amazon.com/cli/latest/userguide/cli-chap-getting-started.html
         */
        File configFile = new File(System.getProperty("user.home"), ".aws/config");
        AWSCredentialsProvider credentialsProvider = new ProfileCredentialsProvider(new ProfilesConfigFile(configFile), "default");

        if (credentialsProvider.getCredentials() == null) {
            throw new RuntimeException("No AWS security credentials found:\n" + "Make sure you've configured your credentials in: "
                    + configFile.getAbsolutePath() + "\n" + "For more information on configuring your credentials, see "
                    + "http://docs.aws.amazon.com/cli/latest/userguide/cli-chap-getting-started.html");
        }

        return credentialsProvider;
    }

    public static ComputeServiceContext getAmazonComputeContext() {
        AWSCredentialsProvider provider = ConfigTools.getAWSCredentialProvider();
        // get a context with ec2 that offers the portable ComputeService API
        ComputeServiceContext context = ContextBuilder.newBuilder("aws-ec2")
                .credentials(provider.getCredentials().getAWSAccessKeyId(), provider.getCredentials().getAWSSecretKey())
                .modules(ImmutableSet.<Module> of(new SLF4JLoggingModule(), new SshjSshClientModule()))
                .buildView(ComputeServiceContext.class);
        return context;
    }

    /**
     * This was used in some code since the current JClouds OpenStack tutorial refers to NovaApi instead of using a generic context. Not
     * sure what is going on there.
     *
     * @return
     */
    public static NovaApi getNovaApi() {
        HierarchicalINIConfiguration youxiaConfig = ConfigTools.getYouxiaConfig();
        NovaApi api = ContextBuilder.newBuilder("openstack-nova").endpoint(youxiaConfig.getString(YOUXIA_OPENSTACK_ENDPOINT))
                .credentials(youxiaConfig.getString(YOUXIA_OPENSTACK_USERNAME), youxiaConfig.getString(YOUXIA_OPENSTACK_PASSWORD))
                .modules(ImmutableSet.<Module> of(new SLF4JLoggingModule(), new SshjSshClientModule())).buildApi(NovaApi.class);
        return api;
    }

    public static ComputeServiceContext getGenericOpenStackApi() {
        HierarchicalINIConfiguration youxiaConfig = ConfigTools.getYouxiaConfig();
        // get a context with OpenStack that offers the portable ComputeService API
        ComputeServiceContext context = ContextBuilder.newBuilder("openstack-nova")
                .endpoint(youxiaConfig.getString(YOUXIA_OPENSTACK_ENDPOINT))
                .credentials(youxiaConfig.getString(YOUXIA_OPENSTACK_USERNAME), youxiaConfig.getString(YOUXIA_OPENSTACK_PASSWORD))
                .modules(ImmutableSet.<Module> of(new SLF4JLoggingModule(), new SshjSshClientModule()))
                .buildView(ComputeServiceContext.class);
        return context;

    }

    public static AmazonSimpleDBClient getSimpleDBClient() {
        Regions region = getRegion();
        return Region.getRegion(region).createClient(AmazonSimpleDBClient.class, getAWSCredentialProvider(), null);
    }

    private static Regions getRegion() {
        HierarchicalINIConfiguration youxiaConfig = ConfigTools.getYouxiaConfig();
        Regions region = Regions.fromName(youxiaConfig.getString(YOUXIA_REGION));
        return region;
    }

    public static AmazonEC2Client getEC2Client() {
        Regions region = getRegion();
        return Region.getRegion(region).createClient(AmazonEC2Client.class, getAWSCredentialProvider(), null);
    }

    public static AmazonS3Client getS3Client() {
        Regions region = getRegion();
        return Region.getRegion(region).createClient(AmazonS3Client.class, getAWSCredentialProvider(), null);
    }

    public static ComputeManagementClient getAzureComputeClient() {
        try {
            Configuration config = getAzureConfig();
            // create a management client to call the API
            ComputeManagementClient client = ComputeManagementService.create(config);
            return client;
        } catch (URISyntaxException | IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static ManagementClient getAzureManagementClient() {
        try {
            Configuration config = getAzureConfig();
            // create a management client to call the API
            return ManagementService.create(config);
        } catch (URISyntaxException | IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static Configuration getAzureConfig() throws IOException, URISyntaxException {
        String uri = "https://management.core.windows.net/";
        HierarchicalINIConfiguration youxiaConfig = ConfigTools.getYouxiaConfig();
        String subscriptionID = youxiaConfig.getString(YOUXIA_AZURE_SUBSCRIPTION_ID);
        String keystoreLocation = youxiaConfig.getString(YOUXIA_AZURE_KEYSTORE_LOCATION);
        String keystorePassword = youxiaConfig.getString(YOUXIA_AZURE_KEYSTORE_PASSWORD);
        com.microsoft.windowsazure.Configuration config = ManagementConfiguration.configure(new URI(uri), subscriptionID, keystoreLocation,
                keystorePassword, KeyStoreType.jks);
        return config;
    }

    public static CloudStorageAccount getAzureStorage() throws URISyntaxException, InvalidKeyException {
        HierarchicalINIConfiguration youxiaConfig = ConfigTools.getYouxiaConfig();
        String storageAccount = youxiaConfig.getString(YOUXIA_AZURE_STORAGE_ACCOUNT_NAME);
        String storageKey = youxiaConfig.getString(YOUXIA_AZURE_STORAGE_ACCOUNT_KEY);
        String storageConnectionString = "DefaultEndpointsProtocol=http;AccountName=" + storageAccount + ";AccountKey=" + storageKey;
        // Retrieve storage account from connection-string.
        CloudStorageAccount storageAccountClient = CloudStorageAccount.parse(storageConnectionString);
        return storageAccountClient;
    }

}
