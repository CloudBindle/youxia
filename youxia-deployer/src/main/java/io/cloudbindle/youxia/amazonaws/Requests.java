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

package io.cloudbindle.youxia.amazonaws;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.BlockDeviceMapping;
import com.amazonaws.services.ec2.model.CancelSpotInstanceRequestsRequest;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.DescribeImagesRequest;
import com.amazonaws.services.ec2.model.DescribeImagesResult;
import com.amazonaws.services.ec2.model.DescribeSpotInstanceRequestsRequest;
import com.amazonaws.services.ec2.model.DescribeSpotInstanceRequestsResult;
import com.amazonaws.services.ec2.model.EbsBlockDevice;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.LaunchSpecification;
import com.amazonaws.services.ec2.model.RequestSpotInstancesRequest;
import com.amazonaws.services.ec2.model.RequestSpotInstancesResult;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.SpotInstanceRequest;
import com.amazonaws.services.ec2.model.SpotPlacement;
import com.amazonaws.services.ec2.model.Tag;
import com.google.common.collect.Lists;
import io.cloudbindle.youxia.util.ConfigTools;
import io.cloudbindle.youxia.util.Log;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * A forked version of Amazon AWS's sample client.
 *
 * @author dyuen
 */
public class Requests {
    private AmazonEC2 ec2;
    private List<String> instanceIds = Lists.newArrayList();
    private List<String> spotInstanceRequestIds = Lists.newArrayList();
    private String instanceType;
    private String amiID;
    private String bidPrice;
    private String securityGroup;
    private String placementGroupName;
    private boolean deleteOnTermination;
    private String availabilityZoneName;
    private String availabilityZoneGroupName;
    private String launchGroupName;
    private Date validFrom;
    private Date validTo;
    private String requestType;
    private int numInstances;
    private String keyName;

    /**
     * Public constructor.
     *
     * @param instanceType
     * @param amiID
     * @param securityGroup
     * @param bidPrice
     * @param numInstances
     * @param keyName
     * @throws Exception
     */
    public Requests(String instanceType, String amiID, String bidPrice, String securityGroup, int numInstances, String keyName)
            throws Exception {
        init(instanceType, amiID, bidPrice, securityGroup, numInstances, keyName);
    }

    /**
     * The only information needed to create a client are security credentials consisting of the AWS Access Key ID and Secret Access Key.
     * All other configuration, such as the service endpoints, are performed automatically. Client parameters, such as proxies, can be
     * specified in an optional ClientConfiguration object when constructing a client.
     *
     * @see com.amazonaws.auth.BasicAWSCredentials
     * @see com.amazonaws.auth.PropertiesCredentials
     * @see com.amazonaws.ClientConfiguration
     */
    private void init(String instanceType, String amiID, String bidPrice, String securityGroup, int numInstances, String keyName)
            throws Exception {
        this.instanceType = instanceType;
        this.amiID = amiID;
        this.bidPrice = bidPrice;
        this.securityGroup = securityGroup;
        this.deleteOnTermination = true;
        this.placementGroupName = null;
        this.numInstances = numInstances;
        this.keyName = keyName;

        this.ec2 = ConfigTools.getEC2Client();
    }

    /**
     * The submit method will create 1 x one-time t1.micro request with a maximum bid price of $0.03 using the Amazon Linux AMI.
     *
     * Note the AMI id may change after the release of this code sample, and it is important to use the latest. You can find the latest
     * version by logging into the AWS Management console, and attempting to perform a launch. You will be presented with AMI options, one
     * of which will be Amazon Linux. Simply use that AMI id.
     *
     */
    public void submitRequests() {
        // ==========================================================================//
        // ================= Submit a Spot Instance Request =====================//
        // ==========================================================================//

        // Initializes a Spot Instance Request
        RequestSpotInstancesRequest requestRequest = new RequestSpotInstancesRequest();

        // Request 1 x t1.micro instance with a bid price of $0.03.
        requestRequest.setSpotPrice(bidPrice);
        requestRequest.setInstanceCount(this.getNumInstances());

        // Setup the specifications of the launch. This includes the instance type (e.g. t1.micro)
        // and the latest Amazon Linux AMI id available. Note, you should always use the latest
        // Amazon Linux AMI id or another of your choosing.
        LaunchSpecification launchSpecification = new LaunchSpecification();
        launchSpecification.setImageId(amiID);

        List<BlockDeviceMapping> blockDeviceMappings = getBlockMappings();
        launchSpecification.setBlockDeviceMappings(blockDeviceMappings);

        launchSpecification.setInstanceType(instanceType);
        launchSpecification.setKeyName(keyName);

        // Add the security group to the request.
        ArrayList<String> securityGroups = new ArrayList<>();
        securityGroups.add(securityGroup);
        launchSpecification.setSecurityGroups(securityGroups);

        // If a placement group has been set, then we will use it in the request.
        if (placementGroupName != null && !placementGroupName.equals("")) {
            // Setup the placement group to use with whatever name you desire.
            SpotPlacement placement = new SpotPlacement();
            placement.setGroupName(placementGroupName);
            launchSpecification.setPlacement(placement);
        }

        // Check to see if we need to set the availability zone name.
        if (availabilityZoneName != null && !availabilityZoneName.equals("")) {
            // Setup the availability zone to use. Note we could retrieve the availability
            // zones using the ec2.describeAvailabilityZones() API.
            SpotPlacement placement = new SpotPlacement(availabilityZoneName);
            launchSpecification.setPlacement(placement);
        }

        if (availabilityZoneGroupName != null && !availabilityZoneGroupName.equals("")) {
            // Set the availability zone group.
            requestRequest.setAvailabilityZoneGroup(availabilityZoneGroupName);
        }

        // Check to see if we need to set the launch group.
        if (launchGroupName != null && !launchGroupName.equals("")) {
            // Set the availability launch group.
            requestRequest.setLaunchGroup(launchGroupName);
        }

        // Check to see if we need to set the valid from option.
        if (validFrom != null) {
            requestRequest.setValidFrom(validFrom);
        }

        // Check to see if we need to set the valid until option.
        if (validTo != null) {
            requestRequest.setValidUntil(validFrom);
        }

        // Check to see if we need to set the request type.
        if (requestType != null && !requestType.equals("")) {
            // Set the type of the bid.
            requestRequest.setType(requestType);
        }

        // If we should delete the EBS boot partition on termination.
        if (!deleteOnTermination) {
            Log.error("Disabling delete on termination is incompatible with BlockDeviceMapping passthrough");
            // Create the block device mapping to describe the root partition.
            BlockDeviceMapping blockDeviceMapping = new BlockDeviceMapping();
            blockDeviceMapping.setDeviceName("/dev/sda1");

            // Set the delete on termination flag to false.
            EbsBlockDevice ebs = new EbsBlockDevice();
            ebs.setDeleteOnTermination(Boolean.FALSE);
            blockDeviceMapping.setEbs(ebs);

            // Add the block device mapping to the block list.
            ArrayList<BlockDeviceMapping> blockList = new ArrayList<>();
            blockList.add(blockDeviceMapping);

            // Set the block device mapping configuration in the launch specifications.
            launchSpecification.setBlockDeviceMappings(blockList);
        }

        // Add the launch specifications to the request.
        requestRequest.setLaunchSpecification(launchSpecification);

        // Call the RequestSpotInstance API.
        RequestSpotInstancesResult requestResult = ec2.requestSpotInstances(requestRequest);
        List<SpotInstanceRequest> requestResponses = requestResult.getSpotInstanceRequests();

        // Setup an arraylist to collect all of the request ids we want to watch hit the running
        // state.
        spotInstanceRequestIds = new ArrayList<>();

        // Add all of the request ids to the hashset, so we can determine when they hit the
        // active state.
        for (SpotInstanceRequest requestResponse : requestResponses) {
            Log.stdoutWithTime("Created Spot Request: " + requestResponse.getSpotInstanceRequestId());
            spotInstanceRequestIds.add(requestResponse.getSpotInstanceRequestId());
        }
    }

    private List<BlockDeviceMapping> getBlockMappings() {
        // copy block mapping from image to new launch specification to ensure that devices make it through
        DescribeImagesRequest ir = new DescribeImagesRequest();
        ir.setImageIds(Lists.newArrayList(amiID));
        DescribeImagesResult describeImagesResult = ec2.describeImages(ir);
        List<BlockDeviceMapping> blockDeviceMappings = describeImagesResult.getImages().get(0).getBlockDeviceMappings();
        // turn off encrypted flag, not sure why it might be specified for snapshots but it seems tto crash at runtime with
        // "Exception in thread "main" java.lang.RuntimeException: com.amazonaws.AmazonServiceException: Parameter encrypted is invalid. You cannot specify the encrypted flag if specifying a snapshot id in a block device mapping."
        for (BlockDeviceMapping mapping : blockDeviceMappings) {
            if (mapping.getEbs() != null) {
                mapping.getEbs().setEncrypted(null);
            }
        }
        return blockDeviceMappings;
    }

    public void launchOnDemand() {
        // ============================================================================================//
        // ====================================== Launch an On-Demand Instance ========================//
        // ====================================== If we Didn't Get a Spot Instance ====================//
        // ============================================================================================//

        // Setup the request for 1 x t1.micro using the same security group and
        // AMI id as the Spot request.
        RunInstancesRequest runInstancesRequest = new RunInstancesRequest();
        runInstancesRequest.setInstanceType(instanceType);
        runInstancesRequest.setImageId(amiID);

        // pass through block mappings from image since amazon weirdly overwrites config here
        List<BlockDeviceMapping> blockDeviceMappings = getBlockMappings();
        runInstancesRequest.setBlockDeviceMappings(blockDeviceMappings);

        runInstancesRequest.setMinCount(this.getNumInstances());
        runInstancesRequest.setMaxCount(this.getNumInstances());
        runInstancesRequest.setKeyName(keyName);

        // Add the security group to the request.
        ArrayList<String> securityGroups = new ArrayList<>();
        securityGroups.add(securityGroup);
        runInstancesRequest.setSecurityGroups(securityGroups);

        // Launch the instance.
        RunInstancesResult runResult = ec2.runInstances(runInstancesRequest);

        // Add the instance id into the instance id list, so we can potentially later
        // terminate that list.
        for (Instance instance : runResult.getReservation().getInstances()) {
            Log.stdoutWithTime("Launched On-Demand Instance: " + instance.getInstanceId());
            instanceIds.add(instance.getInstanceId());
        }
    }

    /**
     * The areOpen method will determine if any of the requests that were started are still in the open state. If all of them have
     * transitioned to either active, cancelled, or closed, then this will return false.
     *
     * @return
     */
    public boolean areAnyOpen() {
        // ==========================================================================//
        // ============== Describe Spot Instance Requests to determine =============//
        // ==========================================================================//

        // Create the describeRequest with tall of the request id to monitor (e.g. that we started).
        DescribeSpotInstanceRequestsRequest describeRequest = new DescribeSpotInstanceRequestsRequest();
        describeRequest.setSpotInstanceRequestIds(spotInstanceRequestIds);

        Log.info("Checking to determine if Spot Bids have reached the active state...");

        // Initialize variables.
        instanceIds = new ArrayList<>();

        try {
            // Retrieve all of the requests we want to monitor.
            DescribeSpotInstanceRequestsResult describeResult = ec2.describeSpotInstanceRequests(describeRequest);
            List<SpotInstanceRequest> describeResponses = describeResult.getSpotInstanceRequests();

            // Look through each request and determine if they are all in the active state.
            for (SpotInstanceRequest describeResponse : describeResponses) {
                Log.info(" " + describeResponse.getSpotInstanceRequestId() + " is in the " + describeResponse.getState() + " state.");

                // If the state is open, it hasn't changed since we attempted to request it.
                // There is the potential for it to transition almost immediately to closed or
                // cancelled so we compare against open instead of active.
                if (describeResponse.getState().equals("open")) {
                    return true;
                }

                // Add the instance id to the list we will eventually terminate.
                instanceIds.add(describeResponse.getInstanceId());
            }
        } catch (AmazonServiceException e) {
            // Print out the error.
            Log.error("Error when calling describeSpotInstances");
            Log.error("Caught Exception: " + e.getMessage());
            Log.error("Reponse Status Code: " + e.getStatusCode());
            Log.error("Error Code: " + e.getErrorCode());
            Log.error("Request ID: " + e.getRequestId());

            // If we have an exception, ensure we don't break out of the loop.
            // This prevents the scenario where there was blip on the wire.
            return true;
        }

        return false;
    }

    /**
     * Tag any of the resources we specify.
     *
     * @param resources
     * @param tags
     */
    private void tagResources(List<String> resources, List<Tag> tags) {
        // Create a tag request.
        CreateTagsRequest createTagsRequest = new CreateTagsRequest();
        createTagsRequest.setResources(resources);
        createTagsRequest.setTags(tags);

        // Try to tag the Spot request submitted.
        try {
            ec2.createTags(createTagsRequest);
        } catch (AmazonServiceException e) {
            // Write out any exceptions that may have occurred.
            Log.error("Caught Exception: " + e.getMessage());
            Log.error("Reponse Status Code: " + e.getStatusCode());
            Log.error("Error Code: " + e.getErrorCode());
            Log.error("Request ID: " + e.getRequestId());
        }

    }

    /**
     * Tags all of the instances started with this object with the tags specified.
     *
     * @param tags
     */
    public void tagInstances(List<Tag> tags) {
        tagResources(instanceIds, tags);
    }

    /**
     * Tags all of the requests started with this object with the tags specified.
     *
     * @param tags
     */
    public void tagRequests(List<Tag> tags) {
        tagResources(spotInstanceRequestIds, tags);
    }

    /**
     * The cleanup method will cancel any active requests and terminate any running instances that were created using this object.
     */
    public void cleanup() {
        // ==========================================================================//
        // ================= Cancel/Terminate Your Spot Request =====================//
        // ==========================================================================//
        if (this.spotInstanceRequestIds.size() > 0) {
            try {
                // Cancel requests.
                Log.info("Cancelling requests.");
                CancelSpotInstanceRequestsRequest cancelRequest = new CancelSpotInstanceRequestsRequest(spotInstanceRequestIds);
                ec2.cancelSpotInstanceRequests(cancelRequest);
            } catch (AmazonServiceException e) {
                // Write out any exceptions that may have occurred.
                Log.error("Error cancelling instances");
                Log.error("Caught Exception: " + e.getMessage());
                Log.error("Reponse Status Code: " + e.getStatusCode());
                Log.error("Error Code: " + e.getErrorCode());
                Log.error("Request ID: " + e.getRequestId());
            }
        }

        // Delete all requests and instances that we have terminated.
        instanceIds.clear();
        spotInstanceRequestIds.clear();
    }

    /**
     * Sets the request type to either persistent or one-time.
     *
     * @param type
     */
    public void setRequestType(String type) {
        this.requestType = type;
    }

    /**
     * Sets the valid to and from time. If you set either value to null or "" then the period will not be set.
     *
     * @param from
     * @param to
     */
    public void setValidPeriod(Date from, Date to) {
        this.validFrom = from;
        this.validTo = to;
    }

    /**
     * Sets the launch group to be used. If you set this to null or "" then launch group will be used.
     *
     * @param launchGroup
     */
    public void setLaunchGroup(String launchGroup) {
        this.launchGroupName = launchGroup;
    }

    /**
     * Sets the availability zone group to be used. If you set this to null or "" then availability zone group will be used.
     *
     * @param azGroup
     */
    public void setAvailabilityZoneGroup(String azGroup) {
        this.availabilityZoneGroupName = azGroup;
    }

    /**
     * Sets the availability zone to be used. If you set this to null or "" then availability zone will be used.
     *
     * @param az
     */
    public void setAvailabilityZone(String az) {
        this.availabilityZoneName = az;
    }

    /**
     * Sets the placementGroupName to be used. If you set this to null or "" then no placementgroup will be used.
     *
     * @param pg
     */
    public void setPlacementGroup(String pg) {
        this.placementGroupName = pg;
    }

    /**
     * This sets the deleteOnTermination flag, so that we can determine whether or not we should delete the root partition if the instance
     * is interrupted or terminated.
     *
     * @param terminate
     */
    public void setDeleteOnTermination(boolean terminate) {
        this.deleteOnTermination = terminate;
    }

    /**
     * @return the numInstances
     */
    public int getNumInstances() {
        return numInstances;
    }

    /**
     * @param numInstances
     *            the numInstances to set
     */
    public void setNumInstances(int numInstances) {
        this.numInstances = numInstances;
    }

    /**
     * @return the instanceIds
     */
    public List<String> getInstanceIds() {
        return instanceIds;
    }

    /**
     * @param instanceIds
     *            the instanceIds to set
     */
    public void setInstanceIds(List<String> instanceIds) {
        this.instanceIds = instanceIds;
    }

}
