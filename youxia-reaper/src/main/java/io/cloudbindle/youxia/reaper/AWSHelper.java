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

import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.google.common.collect.Lists;
import io.cloudbindle.youxia.listing.AbstractInstanceListing;
import io.cloudbindle.youxia.listing.ListingFactory;
import io.cloudbindle.youxia.util.ConfigTools;
import io.cloudbindle.youxia.util.Constants;
import io.cloudbindle.youxia.util.Log;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author dyuen
 */
public class AWSHelper implements AbstractHelper {

    @Override
    public String identifyOrphanedInstance(Map.Entry<String, String> instance) {
        AmazonEC2Client eC2Client = ConfigTools.getEC2Client();
        // terminate instances that did not finish deployment
        DescribeInstancesResult describeInstances = eC2Client.describeInstances(new DescribeInstancesRequest().withInstanceIds(instance
                .getKey()));
        for (Reservation r : describeInstances.getReservations()) {
            for (Instance i : r.getInstances()) {
                for (Tag tag : i.getTags()) {
                    if (tag.getKey().equals(Constants.STATE_TAG) && !tag.getValue().equals(Constants.STATE.READY.toString())) {
                        Log.info(instance.getKey() + " is not ready, likely an orphaned VM");
                        return instance.getKey();
                    }
                }
            }
        }
        return null;
    }

    @Override
    public String translateCloudIDToSensuName(String cloudID) {
        // cloud is is the same as sensu name
        return cloudID;
    }

    @Override
    public AbstractInstanceListing getListing() {
        return ListingFactory.createAWSListing();
    }

    @Override
    public void terminateInstances(Set<String> instancesToKill) {
        AmazonEC2Client client = ConfigTools.getEC2Client();

        // first, mark instances for death
        Log.stdoutWithTime("Marking instances for death " + StringUtils.join(instancesToKill, ","));
        client.createTags(new CreateTagsRequest().withResources(instancesToKill).withTags(
                new Tag(Constants.STATE_TAG, Constants.STATE.MARKED_FOR_DEATH.toString())));

        TerminateInstancesRequest request = new TerminateInstancesRequest(Lists.newArrayList(instancesToKill));
        client.terminateInstances(request);
    }
}
