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
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.s3.AmazonS3Client;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Module;
import java.io.File;
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
    public static final String YOUXIA_SENSU_PASSWORD = "youxia.sensu_password";
    public static final String YOUXIA_SENSU_USERNAME = "youxia.sensu_username";
    public static final String YOUXIA_ZONE = "youxia.zone";
    public static final String YOUXIA_AWS_KEY_NAME = "youxia.aws_key_name";
    public static final String YOUXIA_OPENSTACK_USERNAME = "youxia.openstack_username";
    public static final String YOUXIA_OPENSTACK_PASSWORD = "youxia.openstack_password";
    public static final String YOUXIA_OPENSTACK_ENDPOINT = "youxia.openstack_endpoint";

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
     * Why doesn't jclouds have a ComputeServiceContext for OpenStack?.
     * 
     * @return
     */
    public static NovaApi getNovaApi() {
        HierarchicalINIConfiguration youxiaConfig = ConfigTools.getYouxiaConfig();
        // get a context with OpenStack that offers the portable ComputeService API
        NovaApi api = ContextBuilder.newBuilder("openstack-nova").endpoint(youxiaConfig.getString(YOUXIA_OPENSTACK_ENDPOINT))
                .credentials(youxiaConfig.getString(YOUXIA_OPENSTACK_USERNAME), youxiaConfig.getString(YOUXIA_OPENSTACK_PASSWORD))
                .modules(ImmutableSet.<Module> of(new SLF4JLoggingModule())).buildApi(NovaApi.class);
        return api;
    }

    public static AmazonEC2Client getEC2Client() {
        return new AmazonEC2Client(getAWSCredentialProvider());
    }

    public static AmazonS3Client getS3Client() {
        return new AmazonS3Client(getAWSCredentialProvider());
    }

}
