package io.cloudbindle.youxia.reaper;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.simpledb.AmazonSimpleDBClient;
import com.amazonaws.services.simpledb.model.CreateDomainRequest;
import com.amazonaws.services.simpledb.model.Item;
import com.amazonaws.services.simpledb.model.ListDomainsResult;
import com.amazonaws.services.simpledb.model.PutAttributesRequest;
import com.amazonaws.services.simpledb.model.ReplaceableAttribute;
import com.amazonaws.services.simpledb.model.SelectRequest;
import com.amazonaws.services.simpledb.model.SelectResult;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonWriter;
import io.cloudbindle.youxia.listing.AwsListing;
import io.cloudbindle.youxia.sensu.api.Client;
import io.cloudbindle.youxia.sensu.api.ClientHistory;
import io.cloudbindle.youxia.sensu.client.SensuClient;
import io.cloudbindle.youxia.util.ConfigTools;
import io.cloudbindle.youxia.util.Constants;
import io.cloudbindle.youxia.util.Log;
import io.seqware.common.model.WorkflowRunStatus;
import io.seqware.pipeline.SqwKeys;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import static java.lang.System.out;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
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

    private final ArgumentAcceptingOptionSpec<Integer> batchSize;
    private final OptionSet options;
    private final ArgumentAcceptingOptionSpec<Integer> killLimit;
    private final OptionSpecBuilder testMode;
    private final HierarchicalINIConfiguration youxiaConfig;
    private final OptionSpecBuilder persistWR;
    private final OptionSpecBuilder listWR;
    private final ArgumentAcceptingOptionSpec<String> outputFile;
    private final OptionSpecBuilder useSensu;

    public Reaper(String[] args) {
        this.youxiaConfig = ConfigTools.getYouxiaConfig();
        // TODO: validate that all used properties are present

        OptionParser parser = new OptionParser();

        parser.acceptsAll(Arrays.asList("help", "h", "?"), "Provides this help message.");

        this.batchSize = parser.acceptsAll(Arrays.asList("batch-size", "s"), "Number of instances to bring down at one time")
                .withRequiredArg().ofType(Integer.class).defaultsTo(1);
        this.useSensu = parser.acceptsAll(Arrays.asList("sensu", "s"), "Cross-reference clients with sensu health information");
        this.testMode = parser.acceptsAll(Arrays.asList("test", "t"),
                "In test mode, we only output instances that would be killed rather than actually kill them");

        this.persistWR = parser.acceptsAll(Arrays.asList("persist", "p"), "Persist workflow run information to SimpleDB");
        this.listWR = parser.acceptsAll(Arrays.asList("list", "l"), "Only read workflow run information from SimpleDB");

        this.killLimit = parser
                .acceptsAll(Arrays.asList("kill-limit", "k"), "Number of finished workflow runs that triggers the kill limit")
                .requiredUnless(this.listWR).withRequiredArg().ofType(Integer.class);
        this.outputFile = parser.acceptsAll(Arrays.asList("output", "o"), "Save output to a json file").withRequiredArg()
                .defaultsTo("output.json");

        try {
            this.options = parser.parse(args);
        } catch (OptionException e) {
            try {
                final int helpNumColumns = 160;
                parser.formatHelpWith(new BuiltinHelpFormatter(helpNumColumns, 2));
                parser.printHelpOn(System.out);
                throw new RuntimeException("Showing usage");
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
        if (options.has(useSensu)) {
            distressedClients = determineSensuUnhealthyClients();
        }
        // TODO: incoporate sensu information to determine instances to kill here

        Map<String, String> settings = Maps.newHashMap();
        settings.put(SqwKeys.SW_REST_USER.getSettingKey(), youxiaConfig.getString(ConfigTools.SEQWARE_REST_USER));
        settings.put(SqwKeys.SW_REST_PASS.getSettingKey(), youxiaConfig.getString(ConfigTools.SEQWARE_REST_PASS));

        Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).setPrettyPrinting().create();
        AmazonSimpleDBClient simpleDBClient;
        AmazonEC2Client eC2Client = ConfigTools.getEC2Client();
        for (Entry<String, String> instance : instances.entrySet()) {
            if (instance.getValue() == null) {
                Log.info("Skipping instance with no ip address" + instance.getKey());
                continue;
            }
            // terminate instances that did not finish deployment
            DescribeInstancesResult describeInstances = eC2Client.describeInstances(new DescribeInstancesRequest().withInstanceIds(instance
                    .getKey()));

            boolean killed = false;
            for (Reservation r : describeInstances.getReservations()) {
                for (Instance i : r.getInstances()) {
                    for (Tag tag : i.getTags()) {
                        if (tag.getKey().equals(Constants.STATE_TAG) && !tag.getValue().equals(Constants.STATE.READY.toString())) {
                            Log.info(instance.getKey() + " is not ready, likely an orphaned VM");
                            instancesToKill.add(instance.getKey());
                            killed = true;
                        }
                    }
                }
            }
            if (killed) {
                continue;
            }

            // fake a settings
            String url = "http://" + instance.getValue() + ":" + youxiaConfig.getString(ConfigTools.SEQWARE_REST_PORT) + "/"
                    + youxiaConfig.getString(ConfigTools.SEQWARE_REST_ROOT);
            Log.info("Looking at " + url);
            settings.put(SqwKeys.SW_REST_URL.getSettingKey(), url);
            MetadataWS ws = MetadataFactory.getWS(settings);
            // TODO: can we really not just get all workflow runs?
            try {
                List<WorkflowRun> workflowRuns = ws.getWorkflowRunsByStatus(WorkflowRunStatus.cancelled);
                workflowRuns.addAll(ws.getWorkflowRunsByStatus(WorkflowRunStatus.failed));
                workflowRuns.addAll(ws.getWorkflowRunsByStatus(WorkflowRunStatus.completed));
                Log.info(instance.getKey() + " has " + workflowRuns.size() + " workflow runs");
                if (workflowRuns.size() >= options.valueOf(this.killLimit)) {
                    Log.info(instance.getKey() + " is at or above the kill limit");
                    instancesToKill.add(instance.getKey());
                }
                if (options.has(this.persistWR)) {
                    simpleDBClient = ConfigTools.getSimpleDBClient();
                    final String domainName = youxiaConfig.getString(ConfigTools.YOUXIA_MANAGED_TAG) + Constants.WORKFLOW_RUNS;
                    ListDomainsResult listDomains = simpleDBClient.listDomains();
                    if (!listDomains.getDomainNames().contains(domainName)) {
                        simpleDBClient.createDomain(new CreateDomainRequest(domainName));
                    }

                    for (WorkflowRun run : workflowRuns) {
                        String json = gson.toJson(run);
                        Map<String, Object> fromJson = gson.fromJson(json, Map.class);
                        for (Entry<String, Object> field : fromJson.entrySet()) {
                            // split up the ini file to ensure it makes it into the DB
                            if (field.getKey().equals(Constants.INI_FILE)) {
                                String iniFile = (String) field.getValue();
                                String[] iniFileLines = iniFile.split("\n");
                                for (String iniFileLine : iniFileLines) {
                                    String[] keyValue = StringUtils.split(iniFileLine, "=", 2);
                                    ReplaceableAttribute attr = new ReplaceableAttribute(field.getKey() + "." + keyValue[0], keyValue[1],
                                            true);
                                    PutAttributesRequest request = new PutAttributesRequest(domainName, instance.getKey() + "."
                                            + run.getSwAccession(), Lists.newArrayList(attr));
                                    simpleDBClient.putAttributes(request);
                                }
                            } else {
                                // check to see if the item is too big, if so split it up
                                final int maximumLength = 1024;
                                ReplaceableAttribute attr = new ReplaceableAttribute(field.getKey(), StringUtils.abbreviate(field
                                        .getValue().toString(), maximumLength), true);
                                PutAttributesRequest request = new PutAttributesRequest(domainName, instance.getKey() + "."
                                        + run.getSwAccession(), Lists.newArrayList(attr));
                                simpleDBClient.putAttributes(request);
                            }
                        }
                    }
                }
            } catch (AmazonClientException e) {
                Log.error("Skipping " + instance.getKey() + " " + instance.getValue() + " due to AmazonClient error", e);
            } catch (JsonSyntaxException e) {
                Log.error("Skipping " + instance.getKey() + " " + instance.getValue() + " due to JSON error", e);
            } catch (RuntimeException e) {
                Log.error("Skipping " + instance.getKey() + " " + instance.getValue() + " due to runtime error", e);
            }
        }

        // consider batch size
        while (instancesToKill.size() > (options.has(this.batchSize) ? options.valueOf(this.batchSize) : Integer.MAX_VALUE)) {
            Log.info(instancesToKill.get(0) + " is removed from kill list due to batch size");
            instancesToKill.remove(0);
        }

        return instancesToKill;
    }

    private void listWorkflowRuns() {
        AmazonSimpleDBClient simpleDBClient = ConfigTools.getSimpleDBClient();
        Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).disableHtmlEscaping()
                .setPrettyPrinting().create();

        Writer outWriter = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        if (options.has(outputFile)) {
            try {
                outWriter = Files.newBufferedWriter(Paths.get(options.valueOf(outputFile)), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }

        try (JsonWriter writer = new JsonWriter(outWriter)) {
            writer.setIndent("\t");
            writer.beginArray();
            final String domainName = youxiaConfig.getString(ConfigTools.YOUXIA_MANAGED_TAG) + Constants.WORKFLOW_RUNS;
            SelectResult select = simpleDBClient.select(new SelectRequest("select * from `" + domainName + "`"));
            for (Item item : select.getItems()) {
                gson.toJson(item, Item.class, writer);
            }
            while (select.getNextToken() != null) {
                select = simpleDBClient
                        .select(new SelectRequest("select * from `" + domainName + "`").withNextToken(select.getNextToken()));
                for (Item item : select.getItems()) {
                    gson.toJson(item, Item.class, writer);
                }
            }
            writer.endArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private void terminateSensuClients(List<String> providerIDs, boolean test) {
        SensuClient sensuClient = new SensuClient(youxiaConfig.getString(ConfigTools.YOUXIA_SENSU_IP_ADDRESS),
                youxiaConfig.getInt(ConfigTools.YOUXIA_SENSU_PORT), youxiaConfig.getString(ConfigTools.YOUXIA_SENSU_USERNAME),
                youxiaConfig.getString(ConfigTools.YOUXIA_SENSU_PASSWORD));
        List<Client> clients = sensuClient.getClients();
        Set<String> addressesToKill = Sets.newHashSet();

        AwsListing listing = new AwsListing();
        Map<String, String> instances = listing.getInstances();
        for (Entry<String, String> entry : instances.entrySet()) {
            if (providerIDs.contains(entry.getKey())) {
                addressesToKill.add(entry.getValue());
            }
        }

        for (Client client : clients) {
            if (addressesToKill.contains(client.getAddress())) {
                if (test) {
                    Log.info("Would have deleted sensu client " + client.getName());
                } else {
                    boolean success = sensuClient.deleteClient(client.getName());
                    Log.stdout("Deleting sensu client " + client.getName() + (success ? " succeeded" : "failed"));
                }
            }
        }
    }

    private List<Client> determineSensuUnhealthyClients() {
        List<Client> distressedClients;
        Log.info("Considering sensu information to identify distressed hosts");
        // If sensu options are specified, talk to sensu and cross-reference health information
        SensuClient sensuClient = new SensuClient(youxiaConfig.getString(ConfigTools.YOUXIA_SENSU_IP_ADDRESS),
                youxiaConfig.getInt(ConfigTools.YOUXIA_SENSU_PORT), youxiaConfig.getString(ConfigTools.YOUXIA_SENSU_USERNAME),
                youxiaConfig.getString(ConfigTools.YOUXIA_SENSU_PASSWORD));
        List<Client> clients = sensuClient.getClients();
        List<Client> awsClients = Lists.newArrayList();
        for (Client client : clients) {
            if (client.getEnvironment().getAnsibleSystemVendor().equals("") && client.getEnvironment().getAnsibleProductName().equals("")) {
                // TODO: find better way to denote AWS clients aside from the lack of a openstack vendor or product name
                awsClients.add(client);
            }
        }
        Log.info("Found " + awsClients.size() + " AWS clients in sensu");
        // determine number of clients in distress
        distressedClients = Lists.newArrayList();
        for (Client client : awsClients) {
            // TODO: properly assess clients for distress
            List<ClientHistory> history = sensuClient.getClientHistory(client.getName());
            for (ClientHistory h : history) {
                if (h.getLastStatus() != 0) {
                    distressedClients.add(client);
                }
            }
        }
        return distressedClients;
    }

    public static void main(String[] args) throws Exception {

        Reaper deployer = new Reaper(args);
        if (deployer.options.has(deployer.listWR)) {
            deployer.listWorkflowRuns();
            return;
        }
        List<String> instancesToKill = deployer.assessClients();
        if (instancesToKill.size() > 0) {
            boolean test = deployer.options.has(deployer.testMode);
            if (test) {
                Log.info("Test mode:");
                for (String instance : instancesToKill) {
                    Log.info("Would have killed: " + instance);
                }
            } else {
                Log.info("Live mode:");
                Log.stdoutWithTime("Killing " + StringUtils.join(instancesToKill, ','));
                AmazonEC2 client = ConfigTools.getEC2Client();
                TerminateInstancesRequest request = new TerminateInstancesRequest(instancesToKill);
                client.terminateInstances(request);
            }
            deployer.terminateSensuClients(instancesToKill, test);
        }
    }
}
