package io.cloudbindle.youxia.deployer;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DescribeSpotPriceHistoryResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.SpotPrice;
import com.amazonaws.services.ec2.model.Tag;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.cloudbindle.youxia.amazonaws.Requests;
import io.cloudbindle.youxia.listing.AwsListing;
import io.cloudbindle.youxia.util.ConfigTools;
import io.cloudbindle.youxia.listing.InstanceListingInterface;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.BuiltinHelpFormatter;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.FileUtils;

/**
 * This class maintains a fleet of amazon instances dependent on state retrieved from sensu.
 * 
 * Before you run this code, be sure to fill in your ~/.youxia/config and ~/.aws/config
 */
public class Deployer {

    private static final long SLEEP_CYCLE = 60000;
    private final ArgumentAcceptingOptionSpec<Integer> totalNodes;
    private final ArgumentAcceptingOptionSpec<Float> maxSpotPrice;
    private final ArgumentAcceptingOptionSpec<Integer> batchSize;
    private OptionSet options;
    private final HierarchicalINIConfiguration youxiaConfig;
    public static final String DEPLOYER_INSTANCE_TYPE = "deployer.instance_type";
    public static final String DEPLOYER_AMI_IMAGE = "deployer.ami_image";

    private final ArgumentAcceptingOptionSpec<String> playbook;

    public Deployer(String[] args) {
        // record configuration
        this.youxiaConfig = ConfigTools.getYouxiaConfig();
        // TODO: validate that all required parameters are present
        OptionParser parser = new OptionParser();

        parser.acceptsAll(Arrays.asList("help", "h", "?"), "Provides this help message.");

        this.totalNodes = parser
                .acceptsAll(Arrays.asList("total-nodes-num", "t"), "Total number of spot and on-demand instances to maintain.")
                .withRequiredArg().ofType(Integer.class).required();
        this.maxSpotPrice = parser.acceptsAll(Arrays.asList("max-spot-price", "p"), "Maximum price to pay for spot-price instances.")
                .withRequiredArg().ofType(Float.class).required();
        this.batchSize = parser.acceptsAll(Arrays.asList("batch-size", "s"), "Number of instances to bring up at one time")
                .withRequiredArg().ofType(Integer.class).required();
        this.playbook = parser
                .acceptsAll(Arrays.asList("ansible-playbook", "a"), "If specified, ansible will be run using the specified playbook")
                .withRequiredArg().ofType(String.class).required();
        try {
            this.options = parser.parse(args);
        } catch (OptionException e) {
            try {
                final int helpNumColumns = 160;
                parser.formatHelpWith(new BuiltinHelpFormatter(helpNumColumns, 2));
                parser.printHelpOn(System.out);
                System.exit(-1);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    /**
     * Determine the number of clients that we need to spawn.
     * 
     * @return
     */
    private int assessClients() {
        AwsListing awsLister = new AwsListing();
        Map<String, String> map = awsLister.getInstances();
        System.out.println("Found " + map.size() + " AWS clients");

        int clientsNeeded = options.valueOf(totalNodes) - map.size();
        System.out.println("Need " + clientsNeeded + " more clients");
        int clientsAfterBatching = Math.min(options.valueOf(this.batchSize), clientsNeeded);
        System.out.println("After batch limit, we can requisition up to " + clientsAfterBatching + " this run");
        return clientsAfterBatching;
    }

    /**
     * This checks to see whether the current spot price is reasonable.
     * 
     * @return
     */
    private boolean isReadyToRequestSpotInstances() {
        AmazonEC2Client ec2 = ConfigTools.getEC2Client();
        DescribeSpotPriceHistoryResult describeSpotPriceHistory = ec2.describeSpotPriceHistory();
        Float currentPrice = null;
        for (SpotPrice spotPrice : describeSpotPriceHistory.getSpotPriceHistory()) {
            if (spotPrice.getAvailabilityZone().contains(youxiaConfig.getString(ConfigTools.YOUXIA_ZONE))
                    && spotPrice.getInstanceType().equals(youxiaConfig.getString(DEPLOYER_INSTANCE_TYPE))
                    && spotPrice.getProductDescription().contains("Linux")) {
                System.out.println(spotPrice.toString());
                currentPrice = Float.valueOf(spotPrice.getSpotPrice());
                break;
            }
        }
        if (currentPrice == null) {
            throw new RuntimeException("Invalid spot price request, check your zone or instance type");
        }
        boolean currentPriceIsAcceptable = options.valueOf(this.maxSpotPrice) - currentPrice > 0;
        return currentPriceIsAcceptable;
    }

    private List<Instance> requestSpotInstances(int numInstances) {
        return requestSpotInstances(numInstances, false);
    }

    /**
     * Request spot instances, incorporates code from
     * https://github.com/amazonwebservices/aws-sdk-for-java/blob/master/src/samples/AmazonEC2SpotInstances-Advanced/GettingStartedApp.java
     * 
     * @param numInstances
     * @param skipWait
     * @return
     */
    private List<Instance> requestSpotInstances(int numInstances, boolean skipWait) {
        try {
            // Setup the helper object that will perform all of the API calls.
            Requests requests = new Requests(youxiaConfig.getString(DEPLOYER_INSTANCE_TYPE), youxiaConfig.getString(DEPLOYER_AMI_IMAGE),
                    Float.toString(options.valueOf(this.maxSpotPrice)), "Default", numInstances,
                    youxiaConfig.getString(ConfigTools.YOUXIA_AWS_KEY_NAME));
            requests.setAvailabilityZone(youxiaConfig.getString(ConfigTools.YOUXIA_ZONE));
            // Create the list of tags we want to create and tag any associated requests.
            ArrayList<Tag> tags = new ArrayList<>();
            tags.add(new Tag(InstanceListingInterface.YOUXIA_MANAGED_TAG, youxiaConfig
                    .getString(InstanceListingInterface.YOUXIA_MANAGED_TAG)));
            // Initialize the timer to now.
            Calendar startTimer = Calendar.getInstance();
            Calendar nowTimer = null;
            if (skipWait) {
                requests.launchOnDemand();
            } else {
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
                    requests.launchOnDemand();
                }
            }
            // Tag any created instances.
            requests.tagInstances(tags);

            // wait until instances are ready for SSH
            List<String> instanceIds = Lists.newArrayList();
            instanceIds.addAll(requests.getInstanceIds());
            // Cancel all requests
            requests.cleanup();

            System.out.println("Examining " + instanceIds.size() + " instances");
            AmazonEC2Client eC2Client = ConfigTools.getEC2Client();

            List<Instance> returnInstances = Lists.newArrayList();
            boolean wait;
            do {
                wait = false;
                returnInstances.clear();
                DescribeInstancesRequest describeInstancesRequest = new DescribeInstancesRequest();
                describeInstancesRequest.setInstanceIds(instanceIds);
                DescribeInstancesResult describeInstances = eC2Client.describeInstances(describeInstancesRequest);
                for (Reservation r : describeInstances.getReservations()) {
                    List<Instance> instances = r.getInstances();
                    for (Instance i : instances) {
                        System.out.println(i.toString());
                        if (i.getState().getName().equals("pending") || i.getState().getName().equals("stopping")
                                || i.getState().getName().equals("shutting-down")) {
                            wait = true;
                        }
                        returnInstances.add(i);
                    }
                }
                if (wait) {
                    Thread.sleep(SLEEP_CYCLE);
                } else {
                    break;
                }
            } while (true);

            return returnInstances;
        } catch (AmazonServiceException ase) {
            // Write out any exceptions that may have occurred.
            System.out.println("Caught Exception: " + ase.getMessage());
            System.out.println("Reponse Status Code: " + ase.getStatusCode());
            System.out.println("Error Code: " + ase.getErrorCode());
            System.out.println("Request ID: " + ase.getRequestId());
            throw new RuntimeException(ase);
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private void runAnsible(List<Instance> readyInstances) {
        if (this.options.has(this.playbook)) {
            try {
                // hook up sensu to requested instances using Ansible
                // 1. generate an ansible inventory file
                StringBuilder buffer = new StringBuilder();
                buffer.append("[sensu-server]").append('\n').append("sensu-server\tansible_ssh_host=")
                        .append(youxiaConfig.getString(ConfigTools.YOUXIA_SENSU_IP_ADDRESS))
                        .append("\tansible_ssh_user=ubuntu\tansible_ssh_private_key_file=")
                        .append(youxiaConfig.getString(ConfigTools.YOUXIA_AWS_SSH_KEY)).append("\n");
                // assume all clients are masters (single-node clusters) for now
                buffer.append("[master]\n");
                for (Instance s : readyInstances) {
                    buffer.append(s.getInstanceId()).append('\t').append("ansible_ssh_host=").append(s.getPublicIpAddress());
                    buffer.append('\t').append("ansible_ssh_private_key_file=")
                            .append(youxiaConfig.getString(ConfigTools.YOUXIA_AWS_SSH_KEY)).append('\n');
                }
                Path createTempFile = Files.createTempFile("ansible", ".inventory");
                FileUtils.writeStringToFile(createTempFile.toFile(), buffer.toString());
                System.out.println("Ansible inventory:");
                System.out.println(buffer.toString());

                // 2. run ansible
                CommandLine cmdLine = new CommandLine("ansible-playbook");
                Map<String, String> environmentMap = Maps.newHashMap();
                environmentMap.put("ANSIBLE_HOST_KEY_CHECKING", "False");
                cmdLine.addArgument("-i");
                cmdLine.addArgument("${file}");
                cmdLine.addArgument("${playbook}");
                HashMap map = new HashMap();
                map.put("file", createTempFile);
                map.put("playbook", this.options.valueOf(this.playbook));
                cmdLine.setSubstitutionMap(map);

                System.out.println(cmdLine.toString());
                // kill ansible if it hangs for 15 minutes
                final int waitTime = 15 * 60 * 1000;
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

    public static void main(String[] args) throws Exception {

        Deployer deployer = new Deployer(args);
        int clientsToDeploy = deployer.assessClients();
        if (clientsToDeploy > 0) {
            boolean readyToRequestSpot = deployer.isReadyToRequestSpotInstances();
            List<Instance> readyInstances;
            if (readyToRequestSpot) {
                // call out to request spot instances
                // wait until SSH connection is live
                readyInstances = deployer.requestSpotInstances(clientsToDeploy);
            } else {
                readyInstances = deployer.requestSpotInstances(clientsToDeploy, true);
            }
            deployer.runAnsible(readyInstances);
        }
    }
}
