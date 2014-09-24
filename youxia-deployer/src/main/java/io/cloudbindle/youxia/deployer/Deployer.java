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
import io.cloudbindle.youxia.client.SensuClient;
import io.cloudbindle.youxia.sensu.api.Client;
import io.cloudbindle.youxia.sensu.api.ClientHistory;
import io.cloudbindle.youxia.util.ConfigTools;
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
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.FileUtils;

/**
 * This class maintains a fleet of amazon instances dependent on state retrieved from sensu.
 * 
 * Before you run this code, be sure to fill in your AWS security credentials in the src/main/resources/AwsCredentials.properties file in
 * this project.
 */
public class Deployer {

    private static final int DEFAULT_SENSU_PORT = 4567;
    private static final long SLEEP_CYCLE = 60000;
    private ArgumentAcceptingOptionSpec<Integer> totalNodes;
    private ArgumentAcceptingOptionSpec<Float> maxSpotPrice;
    private ArgumentAcceptingOptionSpec<Integer> batchSize;
    private ArgumentAcceptingOptionSpec<String> sensuHost;
    private ArgumentAcceptingOptionSpec<Integer> sensuPort;
    private OptionSet options;

    public void parseArguments(String[] args) {
        OptionParser parser = new OptionParser();

        parser.acceptsAll(Arrays.asList("help", "h", "?"), "Provides this help message.");

        this.totalNodes = parser
                .acceptsAll(Arrays.asList("total-nodes-num", "t"), "Total number of spot and on-demand instances to maintain.")
                .withRequiredArg().ofType(Integer.class).required();
        this.maxSpotPrice = parser.acceptsAll(Arrays.asList("max-spot-price", "p"), "Maximum price to pay for spot-price instances.")
                .withRequiredArg().ofType(Float.class).required();
        this.batchSize = parser.acceptsAll(Arrays.asList("batch-size", "s"), "Number of instances to bring up at one time")
                .withRequiredArg().ofType(Integer.class).required();
        this.sensuHost = parser.acceptsAll(Arrays.asList("sensu-host", "sh"), "URL for the sensu host").withRequiredArg()
                .ofType(String.class).defaultsTo("localhost");
        this.sensuPort = parser.acceptsAll(Arrays.asList("sensu-port", "sp"), "Port for the sensu server api").withRequiredArg()
                .ofType(Integer.class).required().defaultsTo(DEFAULT_SENSU_PORT);

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
        assert (options != null);
    }

    /**
     * Determine the number of clients that we need to spawn.
     * 
     * @return
     */
    public int assessClients() {
        HierarchicalINIConfiguration youxiaConfig = ConfigTools.getYouxiaConfig();
        SubnodeConfiguration configurationAt = youxiaConfig.configurationAt("youxia");
        configurationAt.getString(null);
        // Talk to sensu and determine number of AWS clients that are active
        SensuClient sensuClient = new SensuClient(options.valueOf(sensuHost), options.valueOf(sensuPort),
                (String) youxiaConfig.getProperty("youxia.sensu_username"), (String) youxiaConfig.getProperty("youxia.sensu_password"));

        List<Client> clients = sensuClient.getClients();
        List<Client> awsClients = Lists.newArrayList();
        for (Client client : clients) {
            if (client.getEnvironment().getAnsible_system_vendor().equals("")
                    && client.getEnvironment().getAnsible_product_name().equals("")) {
                // TODO: find better way to denote AWS clients aside from the lack of a openstack vendor or product name
                awsClients.add(client);
            }
        }
        System.out.println("Found " + awsClients.size() + " AWS clients");

        // determine number of clients in distress
        List<Client> distressedClients = Lists.newArrayList();
        for (Client client : awsClients) {
            // TODO: properly assess clients for distress
            List<ClientHistory> history = sensuClient.getClientHistory(client.getName());
            for (ClientHistory h : history) {
                if (h.getLast_status() != 0) {
                    distressedClients.add(client);
                }
            }
        }
        System.out.println("Found " + distressedClients.size() + " distressed AWS clients");
        // TODO: tear-down distressed clients

        int clientsNeeded = options.valueOf(totalNodes) - awsClients.size();
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
    public boolean isReadyToRequestSpotInstances() {
        AmazonEC2Client ec2 = ConfigTools.getEC2Client();
        DescribeSpotPriceHistoryResult describeSpotPriceHistory = ec2.describeSpotPriceHistory();
        // TODO: parameterize zone and instance types
        Float currentPrice = null;
        for (SpotPrice spotPrice : describeSpotPriceHistory.getSpotPriceHistory()) {
            if (spotPrice.getAvailabilityZone().contains("us-east-1a") && spotPrice.getInstanceType().equals("m1.xlarge")
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
            Requests requests = new Requests("m1.xlarge", "ami-90da15f8", Float.toString(options.valueOf(this.maxSpotPrice)), "Default",
                    numInstances);
            requests.setAvailabilityZone("us-east-1a");
            // Create the list of tags we want to create and tag any associated requests.
            ArrayList<Tag> tags = new ArrayList<>();
            tags.add(new Tag("youxia-provisioned", "true"));
            // Initialize the timer to now.
            Calendar startTimer = Calendar.getInstance();
            Calendar nowTimer = null;
            if (skipWait) {
                requests.launchOnDemand();
            } else {
                // Submit all of the requests.
                requests.submitRequests();
                requests.tagRequests(tags);
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

    public static void main(String[] args) throws Exception {

        Deployer deployer = new Deployer();
        deployer.parseArguments(args);
        int clientsToDeploy = deployer.assessClients();
        if (clientsToDeploy > 0) {
            boolean readyToRequestSpot = deployer.isReadyToRequestSpotInstances();
            List<Instance> readyInstances = Lists.newArrayList();
            if (readyToRequestSpot) {
                // call out to request spot instances
                // wait until SSH connection is live
                readyInstances = deployer.requestSpotInstances(clientsToDeploy);
            } else {
                readyInstances = deployer.requestSpotInstances(clientsToDeploy, true);
            }
            // hook up sensu to requested instances using Ansible
            // 1. generate an ansible inventory file
            StringBuilder buffer = new StringBuilder();
            // TODO: parameterize all this stuff
            buffer.append("[sensu-server]")
                    .append('\n')
                    .append("\tansible_ssh_host=23.22.238.129\tansible_ssh_user=ubuntu\tansible_ssh_private_key_file=/home/dyuen/.ssh/oicr-aws-dyuen.pem\n");
            // assume all clients are masters (single-node clusters) for now
            buffer.append("[master]\n");
            for (Instance s : readyInstances) {
                buffer.append(s.getInstanceId()).append('\t').append("ansible_ssh_host=").append(s.getPublicIpAddress());
                buffer.append('\t').append("ansible_ssh_private_key_file=/home/dyuen/.ssh/sweng-dyuen.pem").append('\n');
            }
            Path createTempFile = Files.createTempFile("ansible", ".inventory");
            FileUtils.writeStringToFile(createTempFile.toFile(), buffer.toString());
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
            map.put("playbook", "/home/dyuen/youxia/ansible_sensu/site.yml");
            cmdLine.setSubstitutionMap(map);

            System.out.println(cmdLine.toString());
            // kill ansible if it hangs for 15 minutes
            final int waitTime = 15 * 60 * 1000;
            ExecuteWatchdog watchdog = new ExecuteWatchdog(waitTime);
            Executor executor = new DefaultExecutor();
            executor.setStreamHandler(new PumpStreamHandler(System.out));
            executor.setWatchdog(watchdog);
            executor.execute(cmdLine, environmentMap);
        }
    }
}
