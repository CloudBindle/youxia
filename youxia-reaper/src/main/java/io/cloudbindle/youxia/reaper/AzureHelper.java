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
import io.cloudbindle.youxia.listing.AbstractInstanceListing;
import io.cloudbindle.youxia.listing.AbstractInstanceListing.InstanceDescriptor;
import io.cloudbindle.youxia.listing.ListingFactory;
import io.cloudbindle.youxia.util.ConfigTools;
import io.cloudbindle.youxia.util.Log;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author dyuen
 */
public class AzureHelper implements AbstractHelper {

    @Override
    public boolean identifyOrphanedInstance(Map.Entry<String, InstanceDescriptor> instance) {
        // without tagging, we have no ability to detect orphans
        return false;
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
                client.getHostedServicesOperations().deleteAll(instance);
            } catch (InterruptedException | ExecutionException | ServiceException | IOException ex) {
                throw new RuntimeException("Unable to delete " + instance, ex);
            }
        }

    }
}
