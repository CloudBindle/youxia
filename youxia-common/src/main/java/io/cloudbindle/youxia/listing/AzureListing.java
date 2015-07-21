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
                        InstanceDescriptor descriptor = new InstanceDescriptor(deployment.getVirtualIPAddresses().get(0).getAddress()
                                .getHostAddress(), null, false);
                        map.put(deployment.getName(), descriptor);
                    }
                }
            }

            // this should be able to update the label
            // ArrayList<Role> rolelist = deployment.getRoles();
            //
            // for (Role role : rolelist) {
            // // try to record role info
            // String serviceName = hostedService.getServiceName();
            // String deploymentName = deployment.getName();
            // String vmName = role.getRoleName();
            // VirtualMachineUpdateParameters param = new VirtualMachineUpdateParameters();
            // param.setLabel("test");
            // param.setOSVirtualHardDisk(role.getOSVirtualHardDisk());
            // param.setRoleName(role.getRoleName());
            // param.setRoleSize(role.getRoleSize());
            // param.setResourceExtensionReferences(role.getResourceExtensionReferences());
            // param.setProvisionGuestAgent(role.isProvisionGuestAgent());
            // param.setDataVirtualHardDisks(role.getDataVirtualHardDisks());
            // param.setConfigurationSets(role.getConfigurationSets());
            // param.setAvailabilitySetName(role.getAvailabilitySetName());
            //
            // try {
            // OperationResponse beginUpdating = computeManagementClient.getVirtualMachinesOperations().beginUpdating(
            // serviceName, deploymentName, vmName, param);
            // String response = beginUpdating.toString();
            // } catch (TransformerException ex) {
            // throw new RuntimeException(ex);
            // }
            //
            // if ((role.getRoleType() != null)
            // && (role.getRoleType().equalsIgnoreCase(VirtualMachineRoleType.PersistentVMRole.toString()))) {
            // vmlist.add(role);
            // }
            // }

            // AmazonEC2Client ec2 = ConfigTools.getEC2Client();
            // Map<String, InstanceDescriptor> map = Maps.newHashMap();
            // // TODO: we can probably constrain instance listing to one region or zone
            // DescribeInstancesResult describeInstancesResult = ec2.describeInstances();
            // for (Reservation reservation : describeInstancesResult.getReservations()) {
            // for (Instance instance : reservation.getInstances()) {
            // String managedTag = null;
            // String managedState = null;
            // for (Tag tag : instance.getTags()) {
            // if (tag.getKey().equals(ConfigTools.YOUXIA_MANAGED_TAG) && tag.getValue().equals(managedTagValue)) {
            // managedTag = tag.getValue();
            // }
            // if (tag.getKey().equals(Constants.STATE_TAG)) {
            // managedState = tag.getValue();
            // }
            // }
            // handleMapping(
            // managedTag,
            // managedState,
            // instance.getInstanceId(),
            // new InstanceDescriptor(instance.getPublicIpAddress(), instance.getPrivateIpAddress(), Objects.equal(
            // instance.getInstanceLifecycle(), (InstanceLifecycleType.Spot.toString()))), map);
            // }
            // }
            // Log.info("Located " + map.values().size() + " relevant instances on Azure");
            // return map;

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
