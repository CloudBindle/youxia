package io.cloudbindle.youxia.reaper;

import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeSpotPriceHistoryResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.SpotPrice;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.cloudbindle.youxia.sensu.client.SensuClient;
import io.cloudbindle.youxia.sensu.api.Client;
import io.cloudbindle.youxia.sensu.api.ClientHistory;
import io.cloudbindle.youxia.util.ConfigTools;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
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
 * This class tears down VMs that are unhealthy or have reached their kill limit.
 * 
 * Before you run this code, be sure to fill in your AWS security credentials in the src/main/resources/AwsCredentials.properties file in
 * this project.
 */
public class Reaper {

    private static final int DEFAULT_SENSU_PORT = 4567;
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

    public static void main(String[] args) throws Exception {

        Reaper deployer = new Reaper();
        deployer.parseArguments(args);
        int clientsToDeploy = deployer.assessClients();
        if (clientsToDeploy > 0) {
            boolean readyToRequestSpot = deployer.isReadyToRequestSpotInstances();
            List<Instance> readyInstances;

            // hook up sensu to requested instances using Ansible
            // 1. generate an ansible inventory file
            StringBuilder buffer = new StringBuilder();
            // TODO: parameterize all this stuff
            buffer.append("[sensu-server]")
                    .append('\n')
                    .append("sensu-server\tansible_ssh_host=23.22.238.129\tansible_ssh_user=ubuntu\tansible_ssh_private_key_file=/home/dyuen/.ssh/oicr-aws-dyuen.pem\n");
            // assume all clients are masters (single-node clusters) for now
            buffer.append("[master]\n");

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
