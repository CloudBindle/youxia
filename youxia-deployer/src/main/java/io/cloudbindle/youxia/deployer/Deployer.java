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
import io.cloudbindle.youxia.amazonaws.Requests;
import io.cloudbindle.youxia.listing.AbstractInstanceListing;
import io.cloudbindle.youxia.listing.AbstractInstanceListing.InstanceDescriptor;
import io.cloudbindle.youxia.listing.ListingFactory;
import io.cloudbindle.youxia.util.ConfigTools;
import io.cloudbindle.youxia.util.Constants;
import io.cloudbindle.youxia.util.Log;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
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
import org.jclouds.collect.IterableWithMarker;
import org.jclouds.collect.PagedIterable;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
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

    private final ArgumentAcceptingOptionSpec<String> playbookSpec;
    private final ArgumentAcceptingOptionSpec<String> extraVarsSpec;
    private final OptionSpecBuilder openStackModeSpec;
    private final ArgumentAcceptingOptionSpec<Integer> maxOnDemandSpec;
    private final ArgumentAcceptingOptionSpec<Integer> minOnDemandSpec;
    private final ArgumentAcceptingOptionSpec<String> extraTagSpec;

    public Deployer(String[] args) {
        // record configuration
        this.youxiaConfig = ConfigTools.getYouxiaConfig();
        // TODO: validate that all required parameters are present
        OptionParser parser = new OptionParser();

        parser.acceptsAll(Arrays.asList("help", "h", "?"), "Provides this help message.");

        this.totalNodesSpec = parser
                .acceptsAll(Arrays.asList("total-nodes-num", "t"), "Total number of spot and on-demand instances to maintain.")
                .withRequiredArg().ofType(Integer.class).required();

        this.openStackModeSpec = parser.acceptsAll(Arrays.asList("openstack", "o"), "Run the deployer using OpenStack (default is AWS)");

        // AWS specific parameter
        this.maxSpotPriceSpec = parser.acceptsAll(Arrays.asList("max-spot-price", "p"), "Maximum price to pay for spot-price instances.")
                .requiredUnless(openStackModeSpec).withRequiredArg().ofType(Float.class);
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
     * @return
     */
    private int assessClients() {
        AbstractInstanceListing lister;
        if (options.has(this.openStackModeSpec)) {
            lister = ListingFactory.createOpenStackListing();

        } else {
            lister = ListingFactory.createAWSListing();
        }
        Map<String, InstanceDescriptor> map = lister.getInstances();
        Log.info("Found " + map.size() + " clients");

        int clientsNeeded = options.valueOf(totalNodesSpec) - map.size();
        Log.info("Need " + clientsNeeded + " more clients");
        int clientsAfterBatching = Math.min(options.valueOf(this.batchSizeSpec), clientsNeeded);
        Log.info("After batch limit, we can requisition up to " + clientsAfterBatching + " this run");
        return clientsAfterBatching;
    }

    /**
     * This checks to see whether the current spot price is reasonable.
     *
     * @return a zone with a reasonable spot price
     */
    private String isReadyToRequestSpotInstances() {
        AmazonEC2Client ec2 = ConfigTools.getEC2Client();
        // grab all possible zones
        String[] desiredZones = youxiaConfig.getStringArray(ConfigTools.YOUXIA_ZONE);
        float lowestSpotPrice = Float.MAX_VALUE;
        String zoneWithLowestSpotPrice = null;

        for (String zone : desiredZones) {
            DescribeSpotPriceHistoryResult describeSpotPriceHistory = ec2.describeSpotPriceHistory(new DescribeSpotPriceHistoryRequest()
                    .withAvailabilityZone(zone).withInstanceTypes(youxiaConfig.getString(DEPLOYER_INSTANCE_TYPE))
                    .withProductDescriptions(youxiaConfig.getString(DEPLOYER_PRODUCT)));
            Float currentPrice = null;
            for (SpotPrice spotPrice : describeSpotPriceHistory.getSpotPriceHistory()) {
                if (spotPrice.getAvailabilityZone().equals(zone)
                        && spotPrice.getInstanceType().equals(youxiaConfig.getString(DEPLOYER_INSTANCE_TYPE))
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
    private List<Instance> requestAWSInstances(int numInstances, boolean onDemand, String zone, Map<String, String> additionalTags) {
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
            Requests requests = new Requests(youxiaConfig.getString(DEPLOYER_INSTANCE_TYPE), youxiaConfig.getString(DEPLOYER_AMI_IMAGE),
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
            Calendar nowTimer = null;
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
                    Log.info("Reponse Status Code: " + ase.getStatusCode());
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
            Log.error("Reponse Status Code: " + ase.getStatusCode());
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

    private void runDeployment(int clientsToDeploy) throws Exception {
        Map<String, String> extraTags = new HashMap<>();
        if (options.has(this.extraTagSpec)) {
            Gson gson = new Gson();
            String readFileToString = FileUtils.readFileToString(new File(options.valueOf(this.extraTagSpec)), StandardCharsets.UTF_8);
            extraTags = gson.fromJson(readFileToString, Map.class);
        }
        Set<String> ids;
        if (options.has(this.openStackModeSpec)) {
            try (ComputeServiceContext genericOpenStackApi = ConfigTools.getGenericOpenStackApi()) {
                ComputeService computeService = genericOpenStackApi.getComputeService();
                // have to use the specific api here to designate a keypair, weird
                TemplateOptions templateOptions = NovaTemplateOptions.Builder.securityGroupNames(
                        youxiaConfig.getString(DEPLOYER_OPENSTACK_SECURITY_GROUP)).keyPairName(
                        youxiaConfig.getString(ConfigTools.YOUXIA_OPENSTACK_KEY_NAME));

                for (Entry<String, String> entry : extraTags.entrySet()) {
                    templateOptions = templateOptions.userMetadata(entry.getKey(), entry.getValue());
                }

                templateOptions = templateOptions
                        .userMetadata("Name", "instance_managed_by_" + youxiaConfig.getString(ConfigTools.YOUXIA_MANAGED_TAG))
                        .userMetadata(ConfigTools.YOUXIA_MANAGED_TAG, youxiaConfig.getString(ConfigTools.YOUXIA_MANAGED_TAG))
                        .userMetadata(Constants.STATE_TAG, Constants.STATE.SETTING_UP.toString()).blockUntilRunning(true)
                        .blockOnComplete(true);

                if (youxiaConfig.getString(DEPLOYER_OPENSTACK_NETWORK_ID) != null
                        && !youxiaConfig.getString(DEPLOYER_OPENSTACK_NETWORK_ID).isEmpty()) {
                    templateOptions.networks(Lists.newArrayList(youxiaConfig.getString(DEPLOYER_OPENSTACK_NETWORK_ID)));
                }

                TemplateBuilder templateBuilder = computeService.templateBuilder().imageId(
                        youxiaConfig.getString(ConfigTools.YOUXIA_OPENSTACK_REGION) + "/"
                                + youxiaConfig.getString(DEPLOYER_OPENSTACK_IMAGE_ID));
                if (youxiaConfig.containsKey(ConfigTools.YOUXIA_OPENSTACK_ZONE)) {
                    String zone = youxiaConfig.getString(ConfigTools.YOUXIA_OPENSTACK_ZONE);
                    if (templateOptions instanceof NovaTemplateOptions) {
                        NovaTemplateOptions novaOptions = (NovaTemplateOptions) templateOptions;
                        novaOptions.availabilityZone(zone);
                    }
                }
                if (youxiaConfig.containsKey(DEPLOYER_OPENSTACK_FLAVOR)) {
                    String hardwareId = youxiaConfig.getString(DEPLOYER_OPENSTACK_FLAVOR);
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

                Set<? extends NodeMetadata> nodesInGroup = computeService.createNodesInGroup("group", clientsToDeploy, template);
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

        } else {
            String zoneWithLowestPrice = isReadyToRequestSpotInstances();
            Log.info("Reporting zone with lowest price: " + zoneWithLowestPrice);
            boolean onlyOnDemandAvailable = zoneWithLowestPrice == null;
            List<Instance> readyInstances = requestAWSInstances(clientsToDeploy, onlyOnDemandAvailable, zoneWithLowestPrice, extraTags);
            if (readyInstances.isEmpty()) {
                return;
            }
            // safety check here
            if (readyInstances.size() > clientsToDeploy) {
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
        }
    }

    private void retagInstances(Set<String> ids) {
        // retag instances with finished metadata, cannot see how to do this with the generic api
        // this sucks incredibly bad and is copied from the OpenStackTagger, there has got to be a way to use the generic api for
        // this
        NovaApi novaApi = ConfigTools.getNovaApi();
        ServerApi serverApiForZone = novaApi.getServerApiForZone(youxiaConfig.getString(ConfigTools.YOUXIA_OPENSTACK_REGION));
        PagedIterable<Server> listInDetail = serverApiForZone.listInDetail();
        // what is this crazy nested structure?
        ImmutableList<IterableWithMarker<Server>> toList = listInDetail.toList();
        for (IterableWithMarker<Server> iterable : toList) {
            ImmutableList<Server> toList1 = iterable.toList();
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
        int clientsToDeploy = deployer.assessClients();
        if (clientsToDeploy > 0) {
            deployer.runDeployment(clientsToDeploy);
        }
    }
}
