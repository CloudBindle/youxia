package io.cloudbindle.youxia.reaper;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.simpledb.AmazonSimpleDBClient;
import com.amazonaws.services.simpledb.model.Attribute;
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
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonWriter;
import io.cloudbindle.youxia.listing.AbstractInstanceListing;
import io.cloudbindle.youxia.listing.AbstractInstanceListing.InstanceDescriptor;
import io.cloudbindle.youxia.listing.ListingFactory;
import io.cloudbindle.youxia.sensu.api.Client;
import io.cloudbindle.youxia.sensu.api.ClientHistory;
import io.cloudbindle.youxia.sensu.client.SensuClient;
import io.cloudbindle.youxia.util.ConfigTools;
import io.cloudbindle.youxia.util.Constants;
import io.cloudbindle.youxia.util.Log;
import io.seqware.common.model.WorkflowRunStatus;
import io.seqware.pipeline.SqwKeys;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TimeZone;
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
import org.apache.commons.io.FileUtils;
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
    private final OptionSpecBuilder openStackMode;
    private final AbstractHelper helper;
    private final String workflowRunDomain;
    private final String deletedClientsDomain;
    private final String dayOfTheYearString = "day_of_year";
    private final String yearString = "year";
    private final int dayOfTheYear;
    private final int year;
    private final ArgumentAcceptingOptionSpec<String> overrideListSpec;
    private Set<String> overrideList;
    private final OptionSpecBuilder azureMode;

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
        this.openStackMode = parser.acceptsAll(Arrays.asList("openstack"), "Run the reaper using OpenStack (default is AWS)");
        this.azureMode = parser.acceptsAll(Arrays.asList("azure"), "Run the reaper using Azure (default is AWS)");

        this.persistWR = parser
                .acceptsAll(Arrays.asList("persist", "p"),
                        "Persist workflow run information and information on killed instances to SimpleDB. Required to report killed instances to sensu.");
        this.listWR = parser.acceptsAll(Arrays.asList("list", "l"), "Only read workflow run information from SimpleDB");
        this.outputFile = parser.acceptsAll(Arrays.asList("output", "o"), "Save output to a json file").withRequiredArg()
                .defaultsTo("output.json").ofType(String.class);
        this.overrideListSpec = parser.acceptsAll(Arrays.asList("kill-list"),
                "Rather than interrogating SeqWare, reap instances based on a JSON listing of ip addresses").withRequiredArg();

        this.killLimit = parser
                .acceptsAll(Arrays.asList("kill-limit", "k"), "Number of finished workflow runs that triggers the kill limit")
                .requiredUnless(this.listWR).requiredUnless(this.overrideListSpec).withRequiredArg().ofType(Integer.class);

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

        if (this.options.has(this.openStackMode)) {
            helper = new OpenStackHelper();
        } else if (this.options.has(this.azureMode)) {
            helper = new AzureHelper();
        } else {
            helper = new AWSHelper();
            // activate Amazon client to ensure value Amazon credentials early
            AmazonEC2Client eC2Client = ConfigTools.getEC2Client();
            // make sure that connectvity is ok, we don't want to terminate instances and find out that we don't have valid Amazon
            // credentials
            eC2Client.describeRegions();
        }
        workflowRunDomain = youxiaConfig.getString(ConfigTools.YOUXIA_MANAGED_TAG) + Constants.WORKFLOW_RUNS;
        deletedClientsDomain = youxiaConfig.getString(ConfigTools.YOUXIA_MANAGED_TAG) + Constants.CLIENTS;

        // fix persistent information
        // fix timezone in case paired clouds are in different time zones
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"), Locale.ENGLISH);
        this.dayOfTheYear = calendar.get(Calendar.DAY_OF_YEAR);
        this.year = calendar.get(Calendar.YEAR);

        // grab list of override instances if found
        if (options.has(this.overrideListSpec)) {
            try {
                Gson gson = new Gson();
                Type collectionType = new TypeToken<Set<String>>() {
                }.getType();
                this.overrideList = gson.fromJson(FileUtils.readFileToString(new File(options.valueOf(this.overrideListSpec))),
                        collectionType);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    /**
     * Determine the clients that we need to bring down.
     *
     * @return
     */
    private Map<String, String> assessClients() {

        AbstractInstanceListing lister;
        if (options.has(this.openStackMode)) {
            lister = ListingFactory.createOpenStackListing();
        } else if (this.options.has(this.azureMode)) {
            lister = ListingFactory.createAzureListing();
        } else {
            lister = ListingFactory.createAWSListing();
        }

        // real id -> ip address
        Map<String, InstanceDescriptor> instances = lister.getInstances();
        // real id -> sensu name (cannot include '/')
        Map<String, String> instancesToKill = new HashMap<>();

        // determine number of clients in distress
        List<Client> distressedClients = Lists.newArrayList();
        if (options.has(useSensu)) {
            distressedClients = determineSensuUnhealthyClients();
        }
        // TODO: incoporate sensu information to determine instances to kill here when that data is deemed reliable enough to act
        // automatically upon

        Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).setPrettyPrinting().create();
        AmazonSimpleDBClient simpleDBClient;

        for (Entry<String, InstanceDescriptor> instance : instances.entrySet()) {
            if (instance.getValue().getIpAddress() == null) {
                Log.info("Skipping instance with no ip address" + instance.getKey());
                continue;
            }
            if (helper.identifyOrphanedInstance(instance)) {
                Log.info(instance.getKey() + " is added to kill list since it is an orphan");
                // orphaned instances likely do not have a sensu name, but try to get it from the metadata
                instancesToKill.put(instance.getKey(), helper.translateCloudIDToSensuName(instance.getKey()));
                continue;
            }

            if (options.has(this.overrideListSpec)) {
                if (this.overrideList.contains(instance.getValue().getIpAddress())
                        || this.overrideList.contains(instance.getValue().getPrivateIpAddress())) {
                    Log.info(instance.getKey() + " is added to kill list since it was found in the override list");
                    instancesToKill.put(instance.getKey(), helper.translateCloudIDToSensuName(instance.getKey()));
                }
            } else {

                // fake a settings
                String url = "http://" + instance.getValue().getIpAddress() + ":" + youxiaConfig.getString(ConfigTools.SEQWARE_REST_PORT)
                        + "/" + youxiaConfig.getString(ConfigTools.SEQWARE_REST_ROOT);
                Log.info("Looking at " + url);
                Map<String, String> settings = Maps.newHashMap();
                settings.put(SqwKeys.SW_REST_USER.getSettingKey(), youxiaConfig.getString(ConfigTools.SEQWARE_REST_USER));
                settings.put(SqwKeys.SW_REST_PASS.getSettingKey(), youxiaConfig.getString(ConfigTools.SEQWARE_REST_PASS));
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
                        instancesToKill.put(instance.getKey(), helper.translateCloudIDToSensuName(instance.getKey()));
                    }
                    if (options.has(this.persistWR)) {
                        simpleDBClient = ConfigTools.getSimpleDBClient();
                        createDomainIfRequired(simpleDBClient, workflowRunDomain);

                        for (WorkflowRun run : workflowRuns) {
                            String workflowRunJson = gson.toJson(run);
                            Map<String, Object> workflowRunMap = gson.fromJson(workflowRunJson, Map.class);
                            for (Entry<String, Object> field : workflowRunMap.entrySet()) {
                                // split up the ini file to ensure it makes it into the DB
                                final int maximumLength = 1024;
                                if (field.getKey().equals(Constants.INI_FILE)) {
                                    String iniFile = (String) field.getValue();
                                    String[] iniFileLines = iniFile.split("\n");
                                    for (String iniFileLine : iniFileLines) {
                                        String[] keyValue = StringUtils.split(iniFileLine, "=", 2);
                                        if (keyValue.length >= 2) {
                                            ReplaceableAttribute attr = new ReplaceableAttribute(field.getKey() + "." + keyValue[0],
                                                    StringUtils.abbreviate(keyValue[1], maximumLength), true);
                                            PutAttributesRequest request = new PutAttributesRequest(workflowRunDomain, instance.getKey()
                                                    + "." + run.getSwAccession(), Lists.newArrayList(attr));
                                            simpleDBClient.putAttributes(request);
                                        }
                                    }
                                } else {
                                    // check to see if the item is too big, if so split it up
                                    ReplaceableAttribute attr = new ReplaceableAttribute(field.getKey(), StringUtils.abbreviate(field
                                            .getValue().toString(), maximumLength), true);
                                    PutAttributesRequest request = new PutAttributesRequest(workflowRunDomain, instance.getKey() + "."
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
        }

        // consider batch size
        while (instancesToKill.size() > (options.has(this.batchSize) ? options.valueOf(this.batchSize) : Integer.MAX_VALUE)) {
            String next = instancesToKill.keySet().iterator().next();
            instancesToKill.remove(next);
            Log.info(next + " is removed from kill list due to batch size");
        }

        return instancesToKill;
    }

    private void createDomainIfRequired(AmazonSimpleDBClient simpleDBClient, String domain) {
        ListDomainsResult listDomains = simpleDBClient.listDomains();
        if (!listDomains.getDomainNames().contains(domain)) {
            simpleDBClient.createDomain(new CreateDomainRequest(domain));
        }
    }

    private void listWorkflowRuns() {
        AmazonSimpleDBClient simpleDBClient = ConfigTools.getSimpleDBClient();
        Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).disableHtmlEscaping()
                .setPrettyPrinting().create();

        Writer outWriter = new OutputStreamWriter(System.out, StandardCharsets.UTF_8);
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
            SelectResult select = simpleDBClient.select(new SelectRequest("select * from `" + workflowRunDomain + "`"));
            for (Item item : select.getItems()) {
                gson.toJson(item, Item.class, writer);
            }
            while (select.getNextToken() != null) {
                select = simpleDBClient.select(new SelectRequest("select * from `" + workflowRunDomain + "`").withNextToken(select
                        .getNextToken()));
                for (Item item : select.getItems()) {
                    gson.toJson(item, Item.class, writer);
                }
            }
            writer.endArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private void terminateSensuClients(boolean test, Set<String> namesToKill) {
        SensuClient sensuClient = new SensuClient(youxiaConfig.getString(ConfigTools.YOUXIA_SENSU_IP_ADDRESS),
                youxiaConfig.getInt(ConfigTools.YOUXIA_SENSU_PORT), youxiaConfig.getString(ConfigTools.YOUXIA_SENSU_USERNAME),
                youxiaConfig.getString(ConfigTools.YOUXIA_SENSU_PASSWORD));
        List<Client> clients = sensuClient.getClients();

        for (Client client : clients) {
            if (namesToKill.contains(client.getName())) {
                if (test) {
                    Log.info("Would have deleted sensu client " + client.getName());
                } else {
                    boolean success = sensuClient.deleteClient(client.getName());
                    Log.info("Deleting sensu client " + client.getName() + (success ? " succeeded" : "failed"));
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

    /**
     *
     * @param instancesToKill
     *            map of instance id (known to cloud) to sensu name (String with restrictions)
     */
    private void terminateInstances(Map<String, String> instancesToKill) {
        if (options.has(this.persistWR)) {
            persistTerminatedInstances(instancesToKill);
        }
        helper.terminateInstances(instancesToKill.keySet());
    }

    private void persistTerminatedInstances(Map<String, String> instancesToKill) {
        // look for instances that were not terminated by the reaper (ex: spot requests on Amazon)
        String managedTagValue = youxiaConfig.getString(ConfigTools.YOUXIA_MANAGED_TAG);
        if (!options.has(this.openStackMode)) {
            AmazonEC2Client eC2Client = ConfigTools.getEC2Client();
            // terminate instances that did not finish deployment
            Filter[] filters = new Filter[] { new Filter().withName("instance-state-name").withValues("terminated"),
                    new Filter().withName("tag:" + ConfigTools.YOUXIA_MANAGED_TAG).withValues(managedTagValue) };
            Log.info("Looking for instances with the following filters:" + Arrays.toString(filters));
            DescribeInstancesResult describeInstances = eC2Client.describeInstances(new DescribeInstancesRequest().withFilters(filters));
            for (Reservation r : describeInstances.getReservations()) {
                for (Instance i : r.getInstances()) {
                    Log.info("Adding instance to persisted list: " + i.getInstanceId());
                    instancesToKill.put(i.getInstanceId(), helper.translateCloudIDToSensuName(i.getInstanceId()));
                }
            }
        }

        // persist sensu name of killed instances in Amazon SimpleDB to ensure that
        // the delay between a termination request and rabbitmq spinning up doesn't
        // persist "zombie" instances
        AmazonSimpleDBClient simpleDBClient = ConfigTools.getSimpleDBClient();
        Log.stdoutWithTime("Persisting terminated instances with day " + dayOfTheYear + " year " + year);
        createDomainIfRequired(simpleDBClient, deletedClientsDomain);
        // persist instance information on instances that will be terminated
        for (Entry<String, String> entry : instancesToKill.entrySet()) {
            List<ReplaceableAttribute> attributes = new ArrayList<>();
            attributes.add(new ReplaceableAttribute("cloud_id", entry.getKey(), false));
            attributes.add(new ReplaceableAttribute("sensu_id", entry.getValue(), false));
            attributes.add(new ReplaceableAttribute(dayOfTheYearString, Integer.toString(dayOfTheYear), false));
            attributes.add(new ReplaceableAttribute(yearString, Integer.toString(year), false));
            PutAttributesRequest request = new PutAttributesRequest(deletedClientsDomain, entry.getKey(), attributes);
            simpleDBClient.putAttributes(request);
        }
    }

    private void mergePersistentRecord(Map<String, String> instancesToKill) {
        if (options.has(this.persistWR)) {
            AmazonSimpleDBClient simpleDBClient = ConfigTools.getSimpleDBClient();
            final String query = "select * from `" + deletedClientsDomain + "`" + " where " + dayOfTheYearString + " = \"" + dayOfTheYear
                    + "\" and " + yearString + " = \"" + year + "\"";
            // get information on previously cleaned instances for today and merge with today's
            createDomainIfRequired(simpleDBClient, deletedClientsDomain);
            SelectRequest select = new SelectRequest(query, true);
            SelectResult selectResult = simpleDBClient.select(select);
            for (Item item : selectResult.getItems()) {
                handleAttribute(item, instancesToKill);
            }
            while (selectResult.getNextToken() != null) {
                selectResult = simpleDBClient.select(new SelectRequest(query, true).withNextToken(select.getNextToken()));
                for (Item item : selectResult.getItems()) {
                    handleAttribute(item, instancesToKill);
                }
            }
            Log.stdoutWithTime("Merged kill list is " + StringUtils.join(instancesToKill, ','));
        }
    }

    private void handleAttribute(Item item, Map<String, String> instancesToKill) {
        String key = null;
        String value = null;
        for (Attribute attribute : item.getAttributes()) {
            switch (attribute.getName()) {
            case "cloud_id":
                key = attribute.getValue();
                break;
            case "sensu_id":
                value = attribute.getValue();
                break;
            default:
            }
        }

        if (key != null && value != null) {
            System.out.println("Merging " + key + " " + value);
            instancesToKill.put(key, value);
        }
    }

    public static void main(String[] args) throws Exception {

        Reaper reaper = new Reaper(args);

        if (reaper.options.has(reaper.listWR)) {
            reaper.listWorkflowRuns();
            return;
        }
        // map is cloud name -> sensu name
        Map<String, String> instancesToKill = reaper.assessClients();
        boolean test = reaper.options.has(reaper.testMode);
        if (test) {
            Log.info("Test mode:");
            for (Entry<String, String> instance : instancesToKill.entrySet()) {
                Log.info("Would have killed instance id:" + instance.getKey() + " sensu name:" + instance.getValue());
            }
        } else {
            Log.info("Live mode:");
            Log.stdoutWithTime("Killing " + StringUtils.join(instancesToKill, ','));
            reaper.terminateInstances(instancesToKill);
        }

        reaper.mergePersistentRecord(instancesToKill);
        reaper.terminateSensuClients(test, Sets.newHashSet(instancesToKill.values()));
    }
}
