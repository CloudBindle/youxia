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

import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceLifecycleType;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.Tag;
import com.google.common.base.Objects;
import com.google.common.collect.Maps;
import io.cloudbindle.youxia.util.ConfigTools;
import io.cloudbindle.youxia.util.Constants;
import io.cloudbindle.youxia.util.Log;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.commons.configuration.HierarchicalINIConfiguration;

/**
 * This lists instances available on AWS.
 *
 * @author dyuen
 */
public class AwsListing extends AbstractInstanceListing {

    @Override
    public Map<String, InstanceDescriptor> getInstances() {
        HierarchicalINIConfiguration youxiaConfig = ConfigTools.getYouxiaConfig();
        String managedTagValue = youxiaConfig.getString(ConfigTools.YOUXIA_MANAGED_TAG);
        AmazonEC2Client ec2 = ConfigTools.getEC2Client();
        Map<String, InstanceDescriptor> map = Maps.newHashMap();
        // TODO: we can probably constrain instance listing to one region or zone
        DescribeInstancesResult describeInstancesResult = ec2.describeInstances();
        for (Reservation reservation : describeInstancesResult.getReservations()) {
            for (Instance instance : reservation.getInstances()) {
                String managedTag = null;
                String managedState = null;
                for (Tag tag : instance.getTags()) {
                    if (tag.getKey().equals(ConfigTools.YOUXIA_MANAGED_TAG) && tag.getValue().equals(managedTagValue)) {
                        managedTag = tag.getValue();
                    }
                    if (tag.getKey().equals(Constants.STATE_TAG)) {
                        managedState = tag.getValue();
                    }
                }
                handleMapping(managedTag, managedState, instance.getInstanceId(),
                        new InstanceDescriptor(instance.getInstanceId(), instance.getPublicIpAddress(), instance.getPrivateIpAddress(),
                                Objects.equal(instance.getInstanceLifecycle(), (InstanceLifecycleType.Spot.toString())), instance.getInstanceType()), map);
            }
        }
        Log.info("Located " + map.values().size() + " relevant instances on AWS");
        return map;
    }

    public static void main(String[] args) {
        AwsListing lister = new AwsListing();
        Map<String, InstanceDescriptor> instances = lister.getInstances();
        for (Entry<String, InstanceDescriptor> instance : instances.entrySet()) {
            System.out.println(instance.getKey() + " " + instance.getValue());
        }
    }

}
