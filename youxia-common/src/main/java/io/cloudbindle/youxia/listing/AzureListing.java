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

import com.google.common.collect.Maps;
import com.microsoft.windowsazure.exception.ServiceException;
import com.microsoft.windowsazure.management.compute.ComputeManagementClient;
import com.microsoft.windowsazure.management.compute.models.HostedServiceGetDetailedResponse;
import com.microsoft.windowsazure.management.compute.models.HostedServiceListResponse;
import io.cloudbindle.youxia.util.ConfigTools;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.xml.sax.SAXException;

/**
 * This lists instances available on Azure.
 *
 * @author dyuen
 */
public class AzureListing extends AbstractInstanceListing {

    @Override
    public Map<String, InstanceDescriptor> getInstances() {
        Map<String, InstanceDescriptor> map = Maps.newHashMap();
        try {
            HierarchicalINIConfiguration youxiaConfig = ConfigTools.getYouxiaConfig();
            String managedTagValue = youxiaConfig.getString(ConfigTools.YOUXIA_MANAGED_TAG);

            ComputeManagementClient computeManagementClient = ConfigTools.getAzureComputeClient();

            HostedServiceListResponse hostedServiceListResponse = computeManagementClient.getHostedServicesOperations().list();
            ArrayList<HostedServiceListResponse.HostedService> hostedServicelist = hostedServiceListResponse.getHostedServices();

            for (HostedServiceListResponse.HostedService hostedService : hostedServicelist) {
                HostedServiceGetDetailedResponse hostedServiceGetDetailedResponse = computeManagementClient.getHostedServicesOperations()
                        .getDetailed(hostedService.getServiceName());

                ArrayList<HostedServiceGetDetailedResponse.Deployment> deploymentlist = hostedServiceGetDetailedResponse.getDeployments();

                for (HostedServiceGetDetailedResponse.Deployment deployment : deploymentlist) {
                    if (deployment.getName().startsWith(managedTagValue)) {
                        // this is ridiculous, the Azure API doesn't return private ip addresses?
                        // also, not sure how to get flavor
                        InstanceDescriptor descriptor = new InstanceDescriptor(deployment.getName(), deployment.getVirtualIPAddresses()
                                .get(0).getAddress().getHostAddress(), null, false, "unknown");
                        map.put(deployment.getName(), descriptor);
                    }
                }
            }

        } catch (IOException | ServiceException | ParserConfigurationException | SAXException | URISyntaxException ex) {
            throw new RuntimeException(ex);
        }
        return map;
    }

    public static void main(String[] args) {
        AzureListing lister = new AzureListing();
        Map<String, InstanceDescriptor> instances = lister.getInstances();
        for (Entry<String, InstanceDescriptor> instance : instances.entrySet()) {
            System.out.println(instance.getKey() + " " + instance.getValue());
        }
    }

}
