package io.cloudbindle.youxia.reaper;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.cloudbindle.youxia.aws.AwsListing;
import io.cloudbindle.youxia.sensu.api.Client;
import io.cloudbindle.youxia.sensu.api.ClientHistory;
import io.cloudbindle.youxia.sensu.client.SensuClient;
import io.cloudbindle.youxia.util.ConfigTools;
import io.seqware.common.model.WorkflowRunStatus;
import io.seqware.pipeline.SqwKeys;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.BuiltinHelpFormatter;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpecBuilder;
import net.sourceforge.seqware.common.metadata.MetadataFactory;
import net.sourceforge.seqware.common.metadata.MetadataWS;
import net.sourceforge.seqware.common.model.WorkflowRun;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.lang3.StringUtils;

/**
 * This class tears down VMs that are unhealthy or have run enough workflows to reach the reaper's kill limit.
 * 
 */
public class Reaper {

    public static final String REAPER_SEQWARE_REST_PASS = "reaper.seqware_rest_pass";
    public static final String REAPER_SEQWARE_REST_USER = "reaper.seqware_rest_user";
    public static final String REAPER_SEQWARE_REST_PORT = "reaper.seqware_rest_port";
    public static final String REAPER_SEQWARE_REST_ROOT = "reaper.seqware_rest_root";

    private static final int DEFAULT_SENSU_PORT = 4567;
    private final ArgumentAcceptingOptionSpec<Integer> batchSize;
    private final ArgumentAcceptingOptionSpec<String> sensuHost;
    private final ArgumentAcceptingOptionSpec<Integer> sensuPort;
    private OptionSet options;
    private final ArgumentAcceptingOptionSpec<Integer> killLimit;
    private final OptionSpecBuilder testMode;
    private final HierarchicalINIConfiguration youxiaConfig;

    public Reaper(String[] args) {
        this.youxiaConfig = ConfigTools.getYouxiaConfig();
        // TODO: validate that all used properties are present

        OptionParser parser = new OptionParser();

        parser.acceptsAll(Arrays.asList("help", "h", "?"), "Provides this help message.");

        this.killLimit = parser
                .acceptsAll(Arrays.asList("kill-limit", "k"), "Number of finished workflow runs that triggers the kill limit")
                .withRequiredArg().ofType(Integer.class).required();
        this.batchSize = parser.acceptsAll(Arrays.asList("batch-size", "s"), "Number of instances to bring down at one time")
                .withRequiredArg().ofType(Integer.class).defaultsTo(1);
        this.sensuHost = parser.acceptsAll(Arrays.asList("sensu-host", "sh"), "URL for the sensu host").withRequiredArg()
                .ofType(String.class).defaultsTo("localhost");
        this.sensuPort = parser.acceptsAll(Arrays.asList("sensu-port", "sp"), "Port for the sensu server api").withRequiredArg()
                .ofType(Integer.class).defaultsTo(DEFAULT_SENSU_PORT);
        this.testMode = parser.acceptsAll(Arrays.asList("test", "t"), "In test mode, we only output instances that would be killed");

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
     * Determine the clients that we need to bring down.
     * 
     * @return
     */
    private List<String> assessClients() {

        AwsListing lister = new AwsListing();
        Map<String, String> instances = lister.getInstances();
        List<String> instancesToKill = Lists.newArrayList();

        // determine number of clients in distress
        List<Client> distressedClients = Lists.newArrayList();
        if (options.has(sensuHost) && options.has(sensuPort)) {
            distressedClients = determineSensuUnhealthyClients();
        }
        // TODO: incoporate sensu information to determine instances to kill here

        Map<String, String> settings = Maps.newHashMap();
        settings.put(SqwKeys.SW_REST_USER.getSettingKey(), youxiaConfig.getString(REAPER_SEQWARE_REST_USER));
        settings.put(SqwKeys.SW_REST_PASS.getSettingKey(), youxiaConfig.getString(REAPER_SEQWARE_REST_PASS));

        for (Entry<String, String> instance : instances.entrySet()) {
            // fake a settings
            String url = "http://" + instance.getValue() + ":" + youxiaConfig.getString(REAPER_SEQWARE_REST_PORT) + "/"
                    + youxiaConfig.getString(REAPER_SEQWARE_REST_ROOT);
            System.out.println("Looking at " + url);
            settings.put(SqwKeys.SW_REST_URL.getSettingKey(), url);
            MetadataWS ws = MetadataFactory.getWS(settings);
            // TODO: can we really not just get all workflow runs?
            try {
                List<WorkflowRun> workflowRuns = ws.getWorkflowRunsByStatus(WorkflowRunStatus.cancelled);
                workflowRuns.addAll(ws.getWorkflowRunsByStatus(WorkflowRunStatus.failed));
                workflowRuns.addAll(ws.getWorkflowRunsByStatus(WorkflowRunStatus.completed));
                System.out.println(instance.getKey() + " has " + workflowRuns.size() + " workflow runs");
                if (workflowRuns.size() >= options.valueOf(this.killLimit)) {
                    System.out.println(instance.getKey() + " is at or above the kill limit");
                    instancesToKill.add(instance.getKey());
                }
            } catch (RuntimeException e) {
                System.out.println("Skipping " + instance.getKey() + " " + instance.getValue() + " due to connection error");
            }
        }

        // consider batch size
        while (instancesToKill.size() > options.valueOf(this.batchSize)) {
            System.out.println(instancesToKill.get(0) + " is removed from kill list due to batch size");
            instancesToKill.remove(0);
        }

        return instancesToKill;
    }

    private List<Client> determineSensuUnhealthyClients() {
        List<Client> distressedClients;
        System.out.println("Considering sensu information to identify distressed hosts");
        // If sensu options are specified, talk to sensu and cross-reference health information
        SensuClient sensuClient = new SensuClient(options.valueOf(sensuHost), options.valueOf(sensuPort),
                youxiaConfig.getString(ConfigTools.YOUXIA_SENSU_USERNAME), youxiaConfig.getString(ConfigTools.YOUXIA_SENSU_PASSWORD));
        List<Client> clients = sensuClient.getClients();
        List<Client> awsClients = Lists.newArrayList();
        for (Client client : clients) {
            if (client.getEnvironment().getAnsible_system_vendor().equals("")
                    && client.getEnvironment().getAnsible_product_name().equals("")) {
                // TODO: find better way to denote AWS clients aside from the lack of a openstack vendor or product name
                awsClients.add(client);
            }
        }
        System.out.println("Found " + awsClients.size() + " AWS clients in sensu");
        // determine number of clients in distress
        distressedClients = Lists.newArrayList();
        for (Client client : awsClients) {
            // TODO: properly assess clients for distress
            List<ClientHistory> history = sensuClient.getClientHistory(client.getName());
            for (ClientHistory h : history) {
                if (h.getLast_status() != 0) {
                    distressedClients.add(client);
                }
            }
        }
        return distressedClients;
    }

    public static void main(String[] args) throws Exception {

        Reaper deployer = new Reaper(args);
        List<String> instancesToKill = deployer.assessClients();
        if (instancesToKill.size() > 0) {
            if (deployer.options.has(deployer.testMode)) {
                System.out.println("Test mode:");
                for (String instance : instancesToKill) {
                    System.out.println("Would have killed: " + instance);
                }
            } else {
                System.out.println("Live mode:");
                System.out.println("Killing " + StringUtils.join(instancesToKill, ','));
                AmazonEC2 client = ConfigTools.getEC2Client();
                TerminateInstancesRequest request = new TerminateInstancesRequest(instancesToKill);
                client.terminateInstances(request);
            }
        }
    }
}
