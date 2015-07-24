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

import com.microsoft.windowsazure.exception.ServiceException;
import com.microsoft.windowsazure.management.compute.ComputeManagementClient;
import io.cloudbindle.youxia.azure.resourceManagerWrapper.AzureResourceManagerClient;
import io.cloudbindle.youxia.azure.resourceManagerWrapper.ResourceGroup;
import io.cloudbindle.youxia.listing.AbstractInstanceListing;
import io.cloudbindle.youxia.listing.AbstractInstanceListing.InstanceDescriptor;
import io.cloudbindle.youxia.listing.ListingFactory;
import io.cloudbindle.youxia.util.ConfigTools;
import io.cloudbindle.youxia.util.Constants;
import io.cloudbindle.youxia.util.Log;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author dyuen
 */
public class AzureHelper implements AbstractHelper {

    private final AzureResourceManagerClient azureResourceManagerClient = ConfigTools.getAzureResourceManagerClient();

    @Override
    public boolean identifyOrphanedInstance(Map.Entry<String, InstanceDescriptor> instance) {
        try {
            ResourceGroup resourceGroup = azureResourceManagerClient.getResourceGroup(instance.getKey());
            Map<String, String> tags = resourceGroup.getTags();
            if (tags == null || !tags.containsKey(Constants.STATE_TAG)) {
                Log.info(instance.getKey() + " is missing state tag, likely an orphaned VM");
                return true;
            }
            if (tags.containsKey(Constants.STATE_TAG) && !tags.get(Constants.STATE_TAG).equals(Constants.STATE.READY.toString())) {
                Log.info(instance.getKey() + " is not ready, likely an orphaned VM");
                return true;
            }
            return false;
        } catch (Exception ex) {
            ex.printStackTrace();
            System.err.println("Unable to detect tag state of " + instance.getKey());
            return false;
        }
    }

    @Override
    public String translateCloudIDToSensuName(String cloudID) {
        // cloud is is the same as sensu name
        return cloudID;
    }

    @Override
    public AbstractInstanceListing getListing() {
        return ListingFactory.createAzureListing();
    }

    @Override
    public void terminateInstances(Set<String> instancesToKill) {
        if (instancesToKill.isEmpty()) {
            return;
        }
        ComputeManagementClient client = ConfigTools.getAzureComputeClient();
        // first, mark instances for death
        Log.stdoutWithTime("Marking instances for death " + StringUtils.join(instancesToKill, ","));
        // should retag instances here
        for (String instance : instancesToKill) {
            try {
                ResourceGroup resourceGroup = azureResourceManagerClient.getResourceGroup(instance);
                Map<String, String> tags = new HashMap<>();
                tags.putAll(resourceGroup.getTags());
                tags.put(Constants.STATE_TAG, Constants.STATE.MARKED_FOR_DEATH.toString());
                azureResourceManagerClient.patchResourceGroup(instance, tags);
            } catch (Exception ex) {
                Log.error("Unable to update resource manager " + instance, ex);
            }

            try {
                client.getHostedServicesOperations().deleteAll(instance);
            } catch (InterruptedException | ExecutionException | ServiceException | IOException ex) {
                throw new RuntimeException("Unable to delete " + instance, ex);
            }
        }

    }
}
