package io.cloudbindle.youxia.deployer;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusRequest;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusResult;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DescribeSpotPriceHistoryRequest;
import com.amazonaws.services.ec2.model.DescribeSpotPriceHistoryResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceStatus;
import com.amazonaws.services.ec2.model.InstanceStatusSummary;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.SpotPrice;
import com.amazonaws.services.ec2.model.Tag;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.windowsazure.core.OperationResponse;
import com.microsoft.windowsazure.management.compute.ComputeManagementClient;
import com.microsoft.windowsazure.management.compute.HostedServiceOperations;
import com.microsoft.windowsazure.management.compute.models.ConfigurationSet;
import com.microsoft.windowsazure.management.compute.models.ConfigurationSetTypes;
import com.microsoft.windowsazure.management.compute.models.DeploymentGetResponse;
import com.microsoft.windowsazure.management.compute.models.DeploymentSlot;
import com.microsoft.windowsazure.management.compute.models.HostedServiceCreateParameters;
import com.microsoft.windowsazure.management.compute.models.InputEndpoint;
import com.microsoft.windowsazure.management.compute.models.OSVirtualHardDisk;
import com.microsoft.windowsazure.management.compute.models.Role;
import com.microsoft.windowsazure.management.compute.models.VirtualHardDiskHostCaching;
import com.microsoft.windowsazure.management.compute.models.VirtualMachineCreateDeploymentParameters;
import com.microsoft.windowsazure.management.compute.models.VirtualMachineCreateParameters;
import com.microsoft.windowsazure.management.compute.models.VirtualMachineRoleType;
import io.cloudbindle.youxia.amazonaws.Requests;
import io.cloudbindle.youxia.azure.resourceManagerWrapper.AzureResourceManagerClient;
import io.cloudbindle.youxia.azure.resourceManagerWrapper.ResourceGroup;
import io.cloudbindle.youxia.listing.AbstractInstanceListing;
import io.cloudbindle.youxia.listing.AbstractInstanceListing.InstanceDescriptor;
import io.cloudbindle.youxia.listing.ListingFactory;
import io.cloudbindle.youxia.util.ConfigTools;
import io.cloudbindle.youxia.util.Constants;
import io.cloudbindle.youxia.util.Log;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.BuiltinHelpFormatter;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpecBuilder;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jclouds.collect.IterableWithMarker;
import org.jclouds.collect.PagedIterable;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.RunNodesException;
import org.jclouds.compute.domain.Hardware;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.Template;
import org.jclouds.compute.domain.TemplateBuilder;
import org.jclouds.compute.options.TemplateOptions;
import org.jclouds.openstack.nova.v2_0.NovaApi;
import org.jclouds.openstack.nova.v2_0.compute.options.NovaTemplateOptions;
import org.jclouds.openstack.nova.v2_0.domain.Server;
import org.jclouds.openstack.nova.v2_0.features.ServerApi;

/**
 * This class maintains a fleet of amazon instances dependent on state retrieved from sensu.
 *
 * Before you run this code, be sure to fill in your ~/.youxia/config and ~/.aws/config
 */
public class Deployer {

    private static final long SLEEP_CYCLE = 60000;
    private final ArgumentAcceptingOptionSpec<Integer> totalNodesSpec;
    private final ArgumentAcceptingOptionSpec<Float> maxSpotPriceSpec;
    private final ArgumentAcceptingOptionSpec<Integer> batchSizeSpec;
    private final ArgumentAcceptingOptionSpec<String> instanceRestrictions;
    private OptionSet options;
    private final HierarchicalINIConfiguration youxiaConfig;
    public static final String DEPLOYER_INSTANCE_TYPE = "deployer.instance_type";
    public static final String DEPLOYER_AMI_IMAGE = "deployer.ami_image";
    public static final String DEPLOYER_SECURITY_GROUP = "deployer.security_group";
    public static final String DEPLOYER_PRODUCT = "deployer.product";
    public static final String DEPLOYER_OPENSTACK_IMAGE_ID = "deployer_openstack.image_id";
    public static final String DEPLOYER_OPENSTACK_FLAVOR = "deployer_openstack.flavor";
    public static final String DEPLOYER_OPENSTACK_MIN_CORES = "deployer_openstack.min_cores";
    public static final String DEPLOYER_OPENSTACK_MIN_RAM = "deployer_openstack.min_ram";
    public static final String DEPLOYER_OPENSTACK_SECURITY_GROUP = "deployer_openstack.security_group";
    public static final String DEPLOYER_OPENSTACK_NETWORK_ID = "deployer_openstack.network_id";
    public static final String DEPLOYER_OPENSTACK_ARBITRARY_WAIT = "deployer_openstack.arbitrary_wait";
    public static final String DEPLOYER_DISABLE_SENSU_SERVER_DEPLOYMENT = "deployer.disable_sensu_server";
    // azure parameters
    public static final String DEPLOYER_AZURE_IMAGE_NAME = "deployer_azure.image_name";
    public static final String DEPLOYER_AZURE_FLAVOR = "deployer_azure.flavor";
    public static final String DEPLOYER_AZURE_LOCATION = "deployer_azure.location";
    public static final String DEPLOYER_AZURE_ARBITRARY_WAIT = "deployer_azure.arbitrary_wait";
    public static final String DEPLOYER_AZURE_VIRTUAL_NETWORK = "deployer_azure.virtual_network";

    private final ArgumentAcceptingOptionSpec<String> playbookSpec;
    private final ArgumentAcceptingOptionSpec<String> extraVarsSpec;
    private final OptionSpecBuilder openStackModeSpec;
    private final ArgumentAcceptingOptionSpec<Integer> maxOnDemandSpec;
    private final ArgumentAcceptingOptionSpec<Integer> minOnDemandSpec;
    private final ArgumentAcceptingOptionSpec<String> extraTagSpec;
    private final OptionSpecBuilder azureModeSpec;

    private static final int AZURE_OPERATION_SUCCESS_CODE = 201;
    private static final int SSH_PORT_NUMBER = 22;

    public Deployer(String[] args) {
        // record configuration
        this.youxiaConfig = ConfigTools.getYouxiaConfig();
        // TODO: validate that all required parameters are present
        OptionParser parser = new OptionParser();

        parser.acceptsAll(Arrays.asList("help", "h", "?"), "Provides this help message.");

        this.totalNodesSpec = parser
                .acceptsAll(Arrays.asList("total-nodes-num", "t"), "Total number of spot and on-demand instances to maintain.")
                .withRequiredArg().ofType(Integer.class);

        this.openStackModeSpec = parser.acceptsAll(Arrays.asList("openstack", "o"), "Run the deployer using OpenStack (default is AWS)");
        this.azureModeSpec = parser.acceptsAll(Arrays.asList("azure", "a"), "Run the deployer using Azure (default is AWS)");

        // AWS specific parameter
        this.maxSpotPriceSpec = parser.acceptsAll(Arrays.asList("max-spot-price", "p"), "Maximum price to pay for spot-price instances.")
                .requiredUnless(openStackModeSpec, azureModeSpec).withRequiredArg().ofType(Float.class);
        this.maxOnDemandSpec = parser
                .acceptsAll(Arrays.asList("max-on-demand", "max"), "Maximum number of on-demand instances to maintain.").withRequiredArg()
                .ofType(Integer.class).defaultsTo(Integer.MAX_VALUE);
        this.minOnDemandSpec = parser
                .acceptsAll(Arrays.asList("min-on-demand", "min"), "Minimum number of on-demand instances to maintain.").withRequiredArg()
                .ofType(Integer.class).defaultsTo(0);

        this.batchSizeSpec = parser.acceptsAll(Arrays.asList("batch-size", "s"), "Number of instances to bring up at one time")
                .withRequiredArg().ofType(Integer.class).required();
        this.playbookSpec = parser
                .acceptsAll(Arrays.asList("ansible-playbook", "a"), "If specified, ansible will be run using the specified playbook")
                .withRequiredArg().ofType(String.class);
        this.extraVarsSpec = parser
                .acceptsAll(Arrays.asList("ansible-extra-vars", "e"),
                        "If specified, ansible will use the specified variables in YAML/JSON format").withRequiredArg()
                .ofType(String.class);
        this.extraTagSpec = parser
                .acceptsAll(Arrays.asList("server-tag-file"),
                        "If specified, ansible will use the specified tags in a JSON file (file contains a representation of a map)")
                .withRequiredArg().ofType(String.class);
        this.instanceRestrictions = parser
                .acceptsAll(Arrays.asList("instance-types"),
                        "If specified, ansible will override the basic specified flavors (files contains a map of flavour -> # of instances)").requiredUnless(totalNodesSpec)
                .withRequiredArg().ofType(String.class);

        try {
            this.options = parser.parse(args);
        } catch (OptionException e) {
            try {
                final int helpNumColumns = 160;
                parser.formatHelpWith(new BuiltinHelpFormatter(helpNumColumns, 2));
                parser.printHelpOn(System.out);
                throw new RuntimeException("Displaying help");
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
        // throw new RuntimeException("Parameters ok");
    }

    /**
     * Determine the number of clients that we need to spawn.
     *
     * @return return the flavour -> number of clients that we can start up this run
     */
    private ImmutablePair<String, Integer> assessClients() {
        AbstractInstanceListing lister;
        String defaultFlavour;
        if (options.has(this.openStackModeSpec)) {
            lister = ListingFactory.createOpenStackListing();
            defaultFlavour = youxiaConfig.getString(DEPLOYER_OPENSTACK_FLAVOR);

        } else if (options.has(this.azureModeSpec)) {
            lister = ListingFactory.createAzureListing();
            defaultFlavour = youxiaConfig.getString(DEPLOYER_AZURE_FLAVOR);
        } else {
            lister = ListingFactory.createAWSListing();
            defaultFlavour = youxiaConfig.getString(DEPLOYER_INSTANCE_TYPE);
        }
        assert (defaultFlavour != null);
        Map<String, InstanceDescriptor> map = lister.getInstances();
        Log.info("Found " + map.size() + " clients");
        final Integer batchSize = options.valueOf(this.batchSizeSpec);

        LinkedHashMap<String, Integer> clientTypes = new LinkedHashMap<>();
        if (options.has(totalNodesSpec)) {
            int totalNumNodes = options.valueOf(totalNodesSpec);
            int clientsNeeded = totalNumNodes - map.size();
            Log.info("Need " + clientsNeeded + " more workers");
            int clientsAfterBatching = Math.min(batchSize, clientsNeeded);
            Log.info("After batch limit, we can requisition up to " + clientsAfterBatching + " this run");
            clientTypes.put(defaultFlavour, clientsAfterBatching);
        } else {
            assert (options.has(instanceRestrictions));
            // get additional map of client types if needed
            if (options.has(this.instanceRestrictions)) {
                Gson gson = new Gson();
                String readFileToString = null;
                try {
                    Type type = new TypeToken<LinkedHashMap<String, Integer>>() {
                    }.getType();
                    readFileToString = FileUtils
                            .readFileToString(new File(options.valueOf(this.instanceRestrictions)), StandardCharsets.UTF_8);
                    clientTypes = gson.fromJson(readFileToString, type);
                } catch (IOException e) {
                    e.printStackTrace();
                    throw new RuntimeException("Could not read client restrictions from " + this.instanceRestrictions.toString());
                }
            }

            // map should be left with what can actually be requisitioned, with special VMs first, but limited by the batch size
            Log.info("Looking for the following types: " + clientTypes.toString());

            // first subtract the types that exist
            for (Entry<String, InstanceDescriptor> entry : map.entrySet()) {
                final String flavour = entry.getValue().getFlavour();
                if (clientTypes.containsKey(flavour)) {
                    clientTypes.compute(flavour, (k, v) -> (v == 1 ? null : v - 1));
                }
            }
            // cap each group by the batch size
            clientTypes.replaceAll((k,v) -> Math.min(batchSize,v));

            Log.info("After removing found instances and capping by batch size: " + clientTypes.toString());
        }

        // consider the first type of instance first, default type should be last since it was inserted last
        if (clientTypes.size() > 0) {
            final Entry<String, Integer> firstEntry = clientTypes.entrySet().iterator().next();
            int clientsNeededForType = Integer.valueOf(firstEntry.getValue());
            final String flavour = firstEntry.getKey();
            if (clientsNeededForType > 0) {
                Log.info("After batch limit, we can requisition up to " + clientsNeededForType + " " + firstEntry.getKey() + " this run");
                return new ImmutablePair<>(flavour, clientsNeededForType);
            }
        }
        return null;
    }

    /**
     * This checks to see whether the current spot price is reasonable.
     *
     * @return a zone with a reasonable spot price
     */
    private String isReadyToRequestSpotInstances(String flavour) {
        AmazonEC2Client ec2 = ConfigTools.getEC2Client();
        // grab all possible zones
        String[] desiredZones = youxiaConfig.getStringArray(ConfigTools.YOUXIA_ZONE);
        float lowestSpotPrice = Float.MAX_VALUE;
        String zoneWithLowestSpotPrice = null;

        for (String zone : desiredZones) {
            DescribeSpotPriceHistoryResult describeSpotPriceHistory = ec2.describeSpotPriceHistory(new DescribeSpotPriceHistoryRequest()
                    .withAvailabilityZone(zone).withInstanceTypes(flavour)
                    .withProductDescriptions(youxiaConfig.getString(DEPLOYER_PRODUCT)));
            Float currentPrice = null;
            for (SpotPrice spotPrice : describeSpotPriceHistory.getSpotPriceHistory()) {
                if (spotPrice.getAvailabilityZone().equals(zone)
                        && spotPrice.getInstanceType().equals(flavour)
                        && spotPrice.getProductDescription().contains("Linux")) {
                    Log.info(spotPrice.toString());
                    currentPrice = Float.valueOf(spotPrice.getSpotPrice());
                    Log.info("Zone: " + zone + " reports " + currentPrice);
                    break;
                }
            }
            if (currentPrice == null) {
                throw new RuntimeException("Invalid spot price request, check your zone or instance type");
            }
            if (currentPrice < lowestSpotPrice) {
                lowestSpotPrice = currentPrice;
                zoneWithLowestSpotPrice = zone;
            }
        }
        Log.info("Checking Zone: " + zoneWithLowestSpotPrice + " with " + lowestSpotPrice + " against "
                + options.valueOf(this.maxSpotPriceSpec) + " maximum");
        boolean currentPriceIsAcceptable = options.valueOf(this.maxSpotPriceSpec) - lowestSpotPrice > 0;
        if (currentPriceIsAcceptable) {
            return zoneWithLowestSpotPrice;
        }
        return null;
    }

    /**
     * Request spot instances, incorporates code from
     * https://github.com/amazonwebservices/aws-sdk-for-java/blob/master/src/samples/AmazonEC2SpotInstances-Advanced/GettingStartedApp.java
     *
     * @param numInstances
     *            number of instances to get in total
     * @param onDemand
     *            true when only on-demand instances are available
     * @param zone
     *            the value of zone
     * @param additionalTags
     *            the value of additionalTags
     * @return the java.util.List<com.amazonaws.services.ec2.model.Instance>
     */
    private List<Instance> requestAWSInstances(int numInstances, boolean onDemand, String zone, Map<String, String> additionalTags, String flavor) {
        try {
            // we need additional information on number of on-demand and spot instances in order to cap our requests
            AbstractInstanceListing lister = ListingFactory.createAWSListing();
            Map<String, InstanceDescriptor> instanceListing = lister.getInstances();
            int currentOnDemand = 0;
            int currentSpotInstances = 0;
            for (InstanceDescriptor desc : instanceListing.values()) {
                if (desc.isSpotInstance()) {
                    currentSpotInstances++;
                } else {
                    currentOnDemand++;
                }
            }
            Log.info("Currently running " + currentOnDemand + " on-demand, " + currentSpotInstances + " spot instances");
            // check for minimum number of on-demand instances and launch those first if needed
            int minOnDemand = options.valueOf(this.minOnDemandSpec);
            int maxOnDemand = options.valueOf(this.maxOnDemandSpec);
            if (currentOnDemand < minOnDemand) {
                int neededOnDemand = minOnDemand - currentOnDemand;
                Log.info("Minimum of " + neededOnDemand + " more on-demand instances needed");
                onDemand = true;
                numInstances = neededOnDemand;
            }
            int remainingOnDemandAllowed = maxOnDemand - currentOnDemand;

            // Setup the helper object that will perform all of the API calls.
            Requests requests = new Requests(flavor, youxiaConfig.getString(DEPLOYER_AMI_IMAGE),
                    Float.toString(options.valueOf(this.maxSpotPriceSpec)), youxiaConfig.getString(DEPLOYER_SECURITY_GROUP), numInstances,
                    youxiaConfig.getString(ConfigTools.YOUXIA_AWS_KEY_NAME));
            requests.setAvailabilityZone(zone);
            // Create the list of tags we want to create and tag any associated requests.
            ArrayList<Tag> tags = new ArrayList<>();
            for (Entry<String, String> entry : additionalTags.entrySet()) {
                tags.add(new Tag(entry.getKey(), entry.getValue()));
            }
            tags.add(new Tag("Name", "instance_managed_by_" + youxiaConfig.getString(ConfigTools.YOUXIA_MANAGED_TAG)));
            tags.add(new Tag(ConfigTools.YOUXIA_MANAGED_TAG, youxiaConfig.getString(ConfigTools.YOUXIA_MANAGED_TAG)));
            tags.add(new Tag(Constants.STATE_TAG, Constants.STATE.SETTING_UP.toString()));
            // Initialize the timer to now.
            Calendar startTimer = Calendar.getInstance();
            Calendar nowTimer;
            if (onDemand) {
                if (launchOnDemandInstances(requests, remainingOnDemandAllowed, numInstances)) {
                    return new ArrayList<>();
                }
            } else {
                // try launching spot requests
                try {

                    // Submit all of the requests.
                    requests.submitRequests();
                    // Loop through all of the requests until all bids are in the active state
                    // (or at least not in the open state).
                    do {
                        final int wait15Minutes = -15;
                        // Sleep for 60 seconds.
                        Thread.sleep(SLEEP_CYCLE);
                        // Initialize the timer to now, and then subtract 15 minutes, so we can
                        // compare to see if we have exceeded 15 minutes compared to the startTime.
                        nowTimer = Calendar.getInstance();
                        nowTimer.add(Calendar.MINUTE, wait15Minutes);
                    } while (requests.areAnyOpen() && !nowTimer.after(startTimer));
                    // If we couldn't launch Spot within the timeout period, then we should launch an On-Demand
                    // Instance.
                    if (nowTimer.after(startTimer)) {
                        // Cancel all requests because we timed out.
                        requests.cleanup();
                        // Launch On-Demand instances instead
                        if (launchOnDemandInstances(requests, remainingOnDemandAllowed, numInstances)) {
                            return new ArrayList<>();
                        }
                    }
                } catch (AmazonServiceException ase) {
                    // Write out any exceptions that may have occurred.
                    Log.info("Caught Exception: " + ase.getMessage());
                    Log.info("Response Status Code: " + ase.getStatusCode());
                    Log.info("Error Code: " + ase.getErrorCode());
                    Log.info("Request ID: " + ase.getRequestId());
                    Log.info("Attempting recovery with on-demand instance");
                    // Cancel all requests because we timed out.
                    requests.cleanup();
                    // Launch On-Demand instances instead
                    if (launchOnDemandInstances(requests, remainingOnDemandAllowed, numInstances)) {
                        return new ArrayList<>();
                    }
                }
            }
            // Tag any created instances.
            requests.tagInstances(tags);

            // wait until instances are ready for SSH
            List<String> instanceIds = Lists.newArrayList();
            instanceIds.addAll(requests.getInstanceIds());
            // Cancel all requests
            requests.cleanup();

            Log.info("Examining " + instanceIds.size() + " instances, " + StringUtils.join(instanceIds, ","));
            AmazonEC2Client eC2Client = ConfigTools.getEC2Client();

            List<Instance> returnInstances = Lists.newArrayList();
            boolean wait;
            // Initialize the timer to now.
            startTimer = Calendar.getInstance();
            do {
                wait = determineRunningInstances(instanceIds, eC2Client, returnInstances);
                // Initialize the timer to now, and then subtract 15 minutes, so we can
                // compare to see if we have exceeded 15 minutes compared to the startTime.
                final int wait15Minutes = -15;
                nowTimer = Calendar.getInstance();
                nowTimer.add(Calendar.MINUTE, wait15Minutes);
                if (wait) {
                    Thread.sleep(SLEEP_CYCLE);
                } else {
                    break;
                }
            } while (!nowTimer.after(startTimer));

            return returnInstances;
        } catch (AmazonServiceException ase) {
            // Write out any exceptions that may have occurred.
            Log.error("Caught Exception: " + ase.getMessage());
            Log.error("Response Status Code: " + ase.getStatusCode());
            Log.error("Error Code: " + ase.getErrorCode());
            Log.error("Request ID: " + ase.getRequestId());
            throw new RuntimeException(ase);
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     *
     * @param requests
     * @param remainingOnDemandAllowed
     * @param numInstances
     * @return true if we didn't launch anything
     */
    private boolean launchOnDemandInstances(Requests requests, int remainingOnDemandAllowed, int numInstances) {
        // launch on demand instances
        requests.setInstanceIds(new ArrayList<String>());
        if (remainingOnDemandAllowed > 0) {
            requests.setNumInstances(Math.min(remainingOnDemandAllowed, numInstances));
            Log.info("Launching " + requests.getNumInstances() + " after on-demand max cutoff");
            requests.launchOnDemand();
        } else {
            Log.info("No more on-demand instances allowed, aborting");
            // don't launch anything
            return true;
        }
        return false;
    }

    /**
     * Populate a list of returnInstances and return true iff all instanceIds are running and ok
     *
     * @param instanceIds
     * @param eC2Client
     * @param returnInstances
     * @return
     */
    private boolean determineRunningInstances(List<String> instanceIds, AmazonEC2Client eC2Client, List<Instance> returnInstances) {
        returnInstances.clear();
        boolean wait = false;
        DescribeInstancesRequest describeInstancesRequest = new DescribeInstancesRequest();
        describeInstancesRequest.setInstanceIds(instanceIds);
        DescribeInstancesResult describeInstances = eC2Client.describeInstances(describeInstancesRequest);
        for (Reservation r : describeInstances.getReservations()) {
            List<Instance> instances = r.getInstances();
            for (Instance i : instances) {
                Log.info(i.toString());
                if (i.getState().getName().equals("running")) {
                    // next check health information
                    DescribeInstanceStatusResult describeInstanceStatus = eC2Client
                            .describeInstanceStatus(new DescribeInstanceStatusRequest().withInstanceIds(i.getInstanceId()));
                    List<InstanceStatus> instanceStatuses = describeInstanceStatus.getInstanceStatuses();
                    for (InstanceStatus status : instanceStatuses) {
                        Log.info(status.toString());
                        InstanceStatusSummary instanceStatus = status.getInstanceStatus();
                        if (instanceStatus.getStatus().equals("ok")) {
                            returnInstances.add(i);
                        } else {
                            wait = true;
                        }
                    }
                } else {
                    wait = true;
                }
            }
        }
        return wait;
    }

    private Set<String> runAnsible(Map<String, DeploymentGetResponse> map) {
        Set<String> ids = new HashSet<>();
        Map<String, String> instanceMap = new HashMap<>();
        for (Entry<String, DeploymentGetResponse> e : map.entrySet()) {
            ids.add(e.getKey());
            instanceMap.put(e.getKey(), e.getValue().getVirtualIPAddresses().get(0).getAddress().getHostAddress());
        }
        runAnsible(instanceMap, youxiaConfig.getString(ConfigTools.YOUXIA_AZURE_SSH_KEY));
        return ids;
    }

    private Set<String> runAnsible(List<Instance> readyInstances) {
        Set<String> ids = new HashSet<>();
        Map<String, String> instanceMap = new HashMap<>();
        for (Instance s : readyInstances) {
            ids.add(s.getInstanceId());
            instanceMap.put(s.getInstanceId(), s.getPublicIpAddress());
        }
        runAnsible(instanceMap, youxiaConfig.getString(ConfigTools.YOUXIA_AWS_SSH_KEY));
        return ids;
    }

    private Set<String> runAnsible(Set<? extends NodeMetadata> nodeMetadata) {
        Set<String> ids = new HashSet<>();
        Map<String, String> instanceMap = new HashMap<>();
        for (NodeMetadata node : nodeMetadata) {
            final String nodeId = node.getId().replace("/", "-");
            ids.add(nodeId);
            if (node.getPrivateAddresses().isEmpty()) {
                throw new RuntimeException("Node " + nodeId + " was not assigned an ip address");
            }
            instanceMap.put(nodeId, node.getPrivateAddresses().iterator().next());
        }
        runAnsible(instanceMap, youxiaConfig.getString(ConfigTools.YOUXIA_OPENSTACK_SSH_KEY));
        return ids;
    }

    private void runAnsible(Map<String, String> instanceMap, String keyFile) {
        if (this.options.has(this.playbookSpec)) {
            try {
                // hook up sensu to requested instances using Ansible
                // 1. generate an ansible inventory file
                StringBuilder buffer = new StringBuilder();
                boolean disableSensuServer = youxiaConfig.getBoolean(DEPLOYER_DISABLE_SENSU_SERVER_DEPLOYMENT, Boolean.FALSE);
                if (!disableSensuServer) {
                    buffer.append("[sensu-server]").append('\n').append("sensu-server\tansible_ssh_host=")
                            .append(youxiaConfig.getString(ConfigTools.YOUXIA_SENSU_IP_ADDRESS))
                            .append("\tansible_ssh_user=ubuntu\tansible_ssh_private_key_file=").append(keyFile).append("\n");
                }
                // assume all clients are masters (single-node clusters) for now
                buffer.append("[master]\n");
                for (Entry<String, String> s : instanceMap.entrySet()) {
                    buffer.append(s.getKey()).append('\t').append("ansible_ssh_host=").append(s.getValue());
                    buffer.append("\tansible_ssh_user=ubuntu\t").append("ansible_ssh_private_key_file=").append(keyFile).append('\n');
                }
                buffer.append('\n');
                // seqware-bag needs a listing of all groups
                buffer.append("[all_groups:children]\n");
                buffer.append("master\n");

                Path createTempFile = Files.createTempFile("ansible", ".inventory");
                FileUtils.writeStringToFile(createTempFile.toFile(), buffer.toString());
                Log.info("Ansible inventory:");
                Log.info(buffer.toString());

                // 2. run ansible
                CommandLine cmdLine = new CommandLine("ansible-playbook");
                Map<String, String> environmentMap = Maps.newHashMap();
                environmentMap.put("ANSIBLE_HOST_KEY_CHECKING", "False");
                cmdLine.addArgument("-i");
                cmdLine.addArgument("${file}");
                cmdLine.addArgument("${playbook}");
                if (options.has(this.extraVarsSpec)) {
                    cmdLine.addArgument("-e");
                    cmdLine.addArgument("\"@" + options.valueOf(this.extraVarsSpec) + "\"");
                }
                HashMap<String, String> map = new HashMap<>();
                map.put("file", createTempFile.toAbsolutePath().toString());
                map.put("playbook", this.options.valueOf(this.playbookSpec));
                cmdLine.setSubstitutionMap(map);

                Log.info(cmdLine.toString());
                // kill ansible if it hangs for 120 minutes
                final int waitTime = 120 * 60 * 1000;
                ExecuteWatchdog watchdog = new ExecuteWatchdog(waitTime);
                Executor executor = new DefaultExecutor();
                executor.setStreamHandler(new PumpStreamHandler(System.out));
                executor.setWatchdog(watchdog);
                executor.execute(cmdLine, environmentMap);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    /**
     * Deploys a certain number of one flavour type.
     * @param clientsToDeploy a number of clients to deploy
     * @throws Exception
     */
    private void runDeployment(Pair<String, Integer> clientsToDeploy) throws Exception {
        Map<String, String> extraTags = new HashMap<>();
        if (options.has(this.extraTagSpec)) {
            Gson gson = new Gson();
            String readFileToString = FileUtils.readFileToString(new File(options.valueOf(this.extraTagSpec)), StandardCharsets.UTF_8);
            extraTags = gson.fromJson(readFileToString, Map.class);
        }
        if (options.has(this.openStackModeSpec)) {
            runDeploymentForOpenStack(extraTags, clientsToDeploy);
        } else if (options.has(this.azureModeSpec)) {
            runDeploymentForAzure(extraTags, clientsToDeploy);
        } else {
            runDeploymentForAWS(extraTags, clientsToDeploy);
        }
    }

    private boolean runDeploymentForAWS(Map<String, String> extraTags, Pair<String, Integer> clientsToDeploy) {
        String zoneWithLowestPrice = isReadyToRequestSpotInstances(clientsToDeploy.getKey());
        Log.info("Reporting zone with lowest price: " + zoneWithLowestPrice);
        boolean onlyOnDemandAvailable = zoneWithLowestPrice == null;
        List<Instance> readyInstances = requestAWSInstances(clientsToDeploy.getValue(), onlyOnDemandAvailable, zoneWithLowestPrice, extraTags, clientsToDeploy.getKey());
        if (readyInstances.isEmpty()) {
            return true;
        }
        // safety check here
        if (readyInstances.size() > clientsToDeploy.getValue()) {
            Log.info("Something has gone awry, more instances reported as ready than were provisioned, aborting ");
            throw new RuntimeException("readyInstances incorrect information");
        }
        runAnsible(readyInstances); // this should throw an Exception on playbook failure
        AmazonEC2Client eC2Client = ConfigTools.getEC2Client();
        // set managed state of instance to ready
        for (Instance i : readyInstances) {
            Log.stdoutWithTime("Finishing configuring " + i.getInstanceId());
            eC2Client.createTags(new CreateTagsRequest().withResources(i.getInstanceId())
                    .withTags(new Tag(Constants.STATE_TAG, Constants.STATE.READY.toString()))
                    .withTags(new Tag(Constants.SENSU_NAME, i.getInstanceId())));
        }
        return false;
    }

    private void runDeploymentForAzure(Map<String, String> extraTags, Pair<String, Integer> clientsToDeploy) throws Exception {
        Map<String, DeploymentGetResponse> nodes = new HashMap<>();
        ComputeManagementClient azureComputeClient = ConfigTools.getAzureComputeClient();
        AzureResourceManagerClient azureResourceManagerClient = ConfigTools.getAzureResourceManagerClient();

        // TODO: implement deployment of different types of instances on Azure
        for (int i = 0; i < clientsToDeploy.getValue(); i++) {

            String randomizedName = youxiaConfig.getString(ConfigTools.YOUXIA_MANAGED_TAG) + "-" + UUID.randomUUID().toString();

            CloudStorageAccount azureStorage = ConfigTools.getAzureStorage();
            CloudBlobClient serviceClient = azureStorage.createCloudBlobClient();
            // Container name must be lower case.
            CloudBlobContainer container = serviceClient.getContainerReference(randomizedName);
            container.createIfNotExists();

            String storageAccountName = youxiaConfig.getString(ConfigTools.YOUXIA_AZURE_STORAGE_ACCOUNT_NAME);

            ArrayList<ConfigurationSet> configlist = new ArrayList<>();
            ConfigurationSet configurationSetForNetwork = new ConfigurationSet();

            configurationSetForNetwork.setConfigurationSetType(ConfigurationSetTypes.NETWORKCONFIGURATION);
            ArrayList<InputEndpoint> inputEndPointList = new ArrayList<>();
            InputEndpoint inputEndpoint = new InputEndpoint();
            inputEndpoint.setName("SSH");
            inputEndpoint.setPort(SSH_PORT_NUMBER);
            inputEndpoint.setLocalPort(SSH_PORT_NUMBER);
            inputEndpoint.setProtocol("tcp");
            inputEndPointList.add(inputEndpoint);
            configurationSetForNetwork.setInputEndpoints(inputEndPointList);
            configlist.add(configurationSetForNetwork);

            OSVirtualHardDisk oSVirtualHardDisk = new OSVirtualHardDisk();
            // required
            oSVirtualHardDisk.setName(randomizedName);
            oSVirtualHardDisk.setHostCaching(VirtualHardDiskHostCaching.READWRITE);
            oSVirtualHardDisk.setOperatingSystem("Linux");

            URI mediaLinkUriValue = new URI("http://" + storageAccountName + ".blob.core.windows.net/vhds/" + randomizedName + ".vhd");
            // required
            oSVirtualHardDisk.setMediaLink(mediaLinkUriValue);
            // oSVirtualHardDisk.setSourceImageName(youxiaConfig.getString(DEPLOYER_AZURE_IMAGE_NAME));

            VirtualMachineCreateParameters createParameters = new VirtualMachineCreateParameters();
            // required
            createParameters.setRoleName(randomizedName);
            createParameters.setRoleSize(youxiaConfig.getString(DEPLOYER_AZURE_FLAVOR));
            createParameters.setProvisionGuestAgent(true);
            // createParameters.setConfigurationSets(configlist);
            createParameters.setOSVirtualHardDisk(oSVirtualHardDisk);
            createParameters.setAvailabilitySetName(null);

            HostedServiceOperations hostedServicesOperations = azureComputeClient.getHostedServicesOperations();
            // create something called a hosted service first
            HostedServiceCreateParameters createHostedServiceParameters = new HostedServiceCreateParameters();
            // required
            createHostedServiceParameters.setLabel(randomizedName);
            // required
            createHostedServiceParameters.setServiceName(randomizedName);
            createHostedServiceParameters.setDescription(randomizedName);
            // required
            createHostedServiceParameters.setLocation(youxiaConfig.getString(DEPLOYER_AZURE_LOCATION));

            OperationResponse hostedServiceOperationResponse = hostedServicesOperations.create(createHostedServiceParameters);
            if (hostedServiceOperationResponse.getStatusCode() != AZURE_OPERATION_SUCCESS_CODE) {
                throw new RuntimeException("Could not create: " + createHostedServiceParameters.toString());
            }

            // create something called a role first
            ArrayList<Role> roleList = new ArrayList<>();
            Role role = new Role();
            // required
            role.setRoleName(randomizedName);
            // required
            role.setVMImageName(youxiaConfig.getString(DEPLOYER_AZURE_IMAGE_NAME));
            role.setRoleType(VirtualMachineRoleType.PersistentVMRole.toString());
            role.setRoleSize(youxiaConfig.getString(DEPLOYER_AZURE_FLAVOR));
            role.setProvisionGuestAgent(true);
            role.setConfigurationSets(configlist);
            roleList.add(role);

            // create something called a deployment first
            VirtualMachineCreateDeploymentParameters deploymentParameters = new VirtualMachineCreateDeploymentParameters();
            deploymentParameters.setDeploymentSlot(DeploymentSlot.Production);
            deploymentParameters.setName(randomizedName);
            deploymentParameters.setLabel(randomizedName);
            deploymentParameters.setRoles(roleList);
            deploymentParameters.setVirtualNetworkName(youxiaConfig.getString(DEPLOYER_AZURE_VIRTUAL_NETWORK));

            System.out.println("Deploying: " + randomizedName);
            azureComputeClient.getVirtualMachinesOperations().createDeployment(randomizedName, deploymentParameters);

            // todo: hook this up to async api and check for success to remove wait?
            System.out.println("Finished requesting VMs, starting arbitrary wait");
            // wait is in minutes
            Thread.sleep(youxiaConfig.getInt(DEPLOYER_AZURE_ARBITRARY_WAIT));
            System.out.println("Completed arbitrary wait");

            // try to do tagging before we spin up
            Map<String, String> tags = new HashMap<>();
            tags.putAll(extraTags);
            tags.put("Name", "instance_managed_by_" + youxiaConfig.getString(ConfigTools.YOUXIA_MANAGED_TAG));
            tags.put(ConfigTools.YOUXIA_MANAGED_TAG, youxiaConfig.getString(ConfigTools.YOUXIA_MANAGED_TAG));
            tags.put(Constants.STATE_TAG, Constants.STATE.SETTING_UP.toString());
            tags.put(Constants.SENSU_NAME, randomizedName);
            azureResourceManagerClient.patchResourceGroup(randomizedName, tags, Integer.MAX_VALUE);

            DeploymentGetResponse deployResponse = azureComputeClient.getDeploymentsOperations().getByName(randomizedName, randomizedName);
            nodes.put(randomizedName, deployResponse);
        }

        runAnsible(nodes);

        // retag on success
        for (Entry<String, DeploymentGetResponse> deployment : nodes.entrySet()) {
            ResourceGroup resourceGroup = azureResourceManagerClient.getResourceGroup(deployment.getKey(), Integer.MAX_VALUE);
            Map<String, String> tags = resourceGroup.getTags();
            tags.put(Constants.STATE_TAG, Constants.STATE.READY.toString());
            azureResourceManagerClient.patchResourceGroup(deployment.getKey(), tags, AzureResourceManagerClient.DEFAULT_ATTEMPTS);
        }
    }

    private void runDeploymentForOpenStack(Map<String, String> extraTags, Pair<String, Integer> clientsToDeploy) throws InterruptedException,
            RunNodesException {
        Set<String> ids;
        try (ComputeServiceContext genericOpenStackApi = ConfigTools.getGenericOpenStackApi()) {
            ComputeService computeService = genericOpenStackApi.getComputeService();
            // have to use the specific api here to designate a key-pair, weird
            TemplateOptions templateOptions = NovaTemplateOptions.Builder.securityGroupNames(
                    youxiaConfig.getString(DEPLOYER_OPENSTACK_SECURITY_GROUP)).keyPairName(
                    youxiaConfig.getString(ConfigTools.YOUXIA_OPENSTACK_KEY_NAME));

            for (Entry<String, String> entry : extraTags.entrySet()) {
                templateOptions = templateOptions.userMetadata(entry.getKey(), entry.getValue());
            }

            templateOptions = templateOptions
                    .userMetadata("Name", "instance_managed_by_" + youxiaConfig.getString(ConfigTools.YOUXIA_MANAGED_TAG))
                    .userMetadata(ConfigTools.YOUXIA_MANAGED_TAG, youxiaConfig.getString(ConfigTools.YOUXIA_MANAGED_TAG))
                    .userMetadata(Constants.STATE_TAG, Constants.STATE.SETTING_UP.toString()).blockUntilRunning(true).blockOnComplete(true);

            if (youxiaConfig.getString(DEPLOYER_OPENSTACK_NETWORK_ID) != null
                    && !youxiaConfig.getString(DEPLOYER_OPENSTACK_NETWORK_ID).isEmpty()) {
                templateOptions.networks(Lists.newArrayList(youxiaConfig.getString(DEPLOYER_OPENSTACK_NETWORK_ID)));
            }

            TemplateBuilder templateBuilder = computeService.templateBuilder()
                    .imageId(
                            youxiaConfig.getString(ConfigTools.YOUXIA_OPENSTACK_REGION) + "/"
                                    + youxiaConfig.getString(DEPLOYER_OPENSTACK_IMAGE_ID));
            if (youxiaConfig.containsKey(ConfigTools.YOUXIA_OPENSTACK_ZONE)) {
                String zone = youxiaConfig.getString(ConfigTools.YOUXIA_OPENSTACK_ZONE);
                if (templateOptions instanceof NovaTemplateOptions) {
                    NovaTemplateOptions novaOptions = (NovaTemplateOptions) templateOptions;
                    novaOptions.availabilityZone(zone);
                }
            }
            if (!clientsToDeploy.getKey().isEmpty()) {
                String hardwareId = clientsToDeploy.getKey();
                System.out.println("Flavor found, using " + hardwareId + " as flavor");
                Set<? extends Hardware> profiles = computeService.listHardwareProfiles();
                Hardware targetHardware = null;
                for (Hardware profile : profiles) {
                    if (Objects.equal(profile.getName(), hardwareId) || Objects.equal(profile.getProviderId(), hardwareId)) {
                        targetHardware = profile;
                        break;
                    }
                }
                if (targetHardware != null) {
                    templateBuilder = templateBuilder.fromHardware(targetHardware);
                } else {
                    throw new RuntimeException("could not locate hardware profile, " + hardwareId);
                }
            } else {
                System.out.println("No hardware id, using cores and ram to determine flavour");
                templateBuilder = templateBuilder.minCores(youxiaConfig.getDouble(DEPLOYER_OPENSTACK_MIN_CORES)).minRam(
                        youxiaConfig.getInt(DEPLOYER_OPENSTACK_MIN_RAM));
            }

            Template template = templateBuilder.options(templateOptions).build();

            Set<? extends NodeMetadata> nodesInGroup = computeService.createNodesInGroup("group", clientsToDeploy.getValue(), template);
            for (NodeMetadata meta : nodesInGroup) {
                Log.stdoutWithTime("Created " + meta.getId() + " " + meta.getStatus().toString());
            }
            System.out.println("Finished requesting VMs, starting arbitrary wait");
            // wait is in minutes
            Thread.sleep(youxiaConfig.getInt(DEPLOYER_OPENSTACK_ARBITRARY_WAIT));
            System.out.println("Completed arbitrary wait");

            ids = runAnsible(nodesInGroup);
            for (String id : ids) {
                System.out.println("Looking to complete tagging of " + id);
            }

            retagInstances(ids);
        }
    }

    private void retagInstances(Set<String> ids) {
        // re-tag instances with finished metadata, cannot see how to do this with the generic api
        // this sucks incredibly bad and is copied from the OpenStackTagger, there has got to be a way to use the generic api for
        // this
        NovaApi novaApi = ConfigTools.getNovaApi();
        ServerApi serverApiForZone = novaApi.getServerApiForZone(youxiaConfig.getString(ConfigTools.YOUXIA_OPENSTACK_REGION));
        PagedIterable<Server> listInDetail = serverApiForZone.listInDetail();
        // what is this crazy nested structure?
        ImmutableList<IterableWithMarker<Server>> toList = listInDetail.toList();
        for (IterableWithMarker<Server> iterate : toList) {
            ImmutableList<Server> toList1 = iterate.toList();
            for (Server server : toList1) {
                // generic api uses region ids, the specific one doesn't. Sigh.
                final String nodeId = youxiaConfig.getString(ConfigTools.YOUXIA_OPENSTACK_REGION) + "-" + server.getId().replace("/", "-");
                if (ids.contains(nodeId)) {
                    Log.stdoutWithTime("Finishing configuring " + nodeId);
                    Map<String, String> metadata = Maps.newHashMap(server.getMetadata());
                    metadata.put(Constants.STATE_TAG, Constants.STATE.READY.toString());
                    metadata.put(Constants.SENSU_NAME, nodeId);
                    serverApiForZone.setMetadata(server.getId(), metadata);
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {

        Deployer deployer = new Deployer(args);

        Pair<String, Integer> clientsToDeploy = deployer.assessClients();
        if (clientsToDeploy != null) {
            deployer.runDeployment(clientsToDeploy);
        }
    }
}
