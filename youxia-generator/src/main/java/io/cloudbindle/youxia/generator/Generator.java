package io.cloudbindle.youxia.generator;

import com.google.common.collect.Maps;
import com.google.common.reflect.TypeToken;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.cloudbindle.youxia.listing.AwsJCloudsListing;
import io.cloudbindle.youxia.listing.InstanceListingInterface;
import io.cloudbindle.youxia.listing.OpenStackJCloudsListing;
import io.cloudbindle.youxia.pawg.api.ClusterDetails;
import io.cloudbindle.youxia.util.ConfigTools;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.BuiltinHelpFormatter;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpecBuilder;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.io.FileUtils;

/**
 * The generator aggregates inventory information based on instances tagged in AWS, manually created JSON files, and instance metadata on
 * Openstack.
 * 
 * Before you run this code, be sure to fill in your ~/.youxia/config and ~/.aws/config
 */
public class Generator {

    public static final String GENERATOR_WORKFLOW_VERSION = "generator.workflow_version";
    public static final String GENERATOR_WORKFLOW_NAME = "generator.workflow_name";
    public static final String GENERATOR_WORKFLOW_ACCESSION = "generator.workflow_accession";
    public static final String GENERATOR_MAX_WORKFLOWS = "generator.max_workflows";
    public static final String GENERATOR_MAX_SCHEDULED_WORKFLOWS = "generator.max_scheduled_workflows";
    private OptionSet options;
    private final HierarchicalINIConfiguration youxiaConfig;
    private final OptionSpecBuilder aggregateAWS;
    private final OptionSpecBuilder aggregateOpenStack;
    private final ArgumentAcceptingOptionSpec<String> aggregateJSON;
    private final ArgumentAcceptingOptionSpec<String> outputFile;
    private final OptionSpecBuilder help;

    public Generator(String[] args) {
        this.youxiaConfig = ConfigTools.getYouxiaConfig();
        // TODO: validate that all required parameters are present
        OptionParser parser = new OptionParser();

        this.help = parser.acceptsAll(Arrays.asList("help", "h", "?"), "Provides this help message.");

        this.aggregateAWS = parser.acceptsAll(Arrays.asList("aws", "a"), "Aggregate tagged instances from AWS");
        this.aggregateOpenStack = parser.acceptsAll(Arrays.asList("openstack", "o"), "Aggregate tagged instances from OpenStack");
        this.aggregateJSON = parser.acceptsAll(Arrays.asList("json", "j"), "Aggregate tagged instances from a provided json")
                .withRequiredArg();
        this.outputFile = parser.acceptsAll(Arrays.asList("output", "o"), "Save output to a json file").withRequiredArg()
                .defaultsTo("output.json");

        try {
            this.options = parser.parse(args);
            if (!options.hasOptions()) {
                throw new RuntimeException("No options");
            }
            if (options.has(help)) {
                throw new RuntimeException("Show usage");
            }
        } catch (RuntimeException e) {
            try {
                final int helpNumColumns = 160;
                parser.formatHelpWith(new BuiltinHelpFormatter(helpNumColumns, 2));
                parser.printHelpOn(System.out);
                throw new RuntimeException("Showing usage");
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        // TODO: refactor this into proper object methods
        Generator generator = new Generator(args);
        Map<String, String> instances = Maps.newHashMap();
        if (generator.options.has(generator.aggregateAWS)) {
            InstanceListingInterface lister = new AwsJCloudsListing();
            instances.putAll(lister.getInstances());
        }
        if (generator.options.has(generator.aggregateOpenStack)) {
            InstanceListingInterface lister = new OpenStackJCloudsListing();
            instances.putAll(lister.getInstances());
        }

        Map<String, ClusterDetails> resultMap = Maps.newHashMap();
        Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).setPrettyPrinting().create();
        if (generator.options.has(generator.aggregateJSON)) {
            String filename = generator.options.valueOf(generator.aggregateJSON);
            String readFileToString = FileUtils.readFileToString(new File(filename));
            Type mapType = new TypeToken<Map<String, ClusterDetails>>() {
            }.getType();
            resultMap = gson.fromJson(readFileToString, mapType);
        }

        for (Entry<String, String> entry : instances.entrySet()) {
            ClusterDetails details = new ClusterDetails();
            details.setHost(entry.getValue());
            details.setMaxScheduledWorkflows(generator.youxiaConfig.getString(GENERATOR_MAX_SCHEDULED_WORKFLOWS));
            details.setMaxWorkflows(generator.youxiaConfig.getString(GENERATOR_MAX_WORKFLOWS));
            details.setPassword(generator.youxiaConfig.getString(ConfigTools.SEQWARE_REST_PASS));
            details.setUsername(generator.youxiaConfig.getString(ConfigTools.SEQWARE_REST_USER));
            details.setWebservice("http://" + entry.getValue() + ":" + generator.youxiaConfig.getString(ConfigTools.SEQWARE_REST_PORT)
                    + "/" + generator.youxiaConfig.getString(ConfigTools.SEQWARE_REST_ROOT));
            details.setWorkflowAccession(generator.youxiaConfig.getString(GENERATOR_WORKFLOW_ACCESSION));
            details.setWorkflowName(generator.youxiaConfig.getString(GENERATOR_WORKFLOW_NAME));
            details.setWorkflowVersion(generator.youxiaConfig.getString(GENERATOR_WORKFLOW_VERSION));
            resultMap.put(entry.getKey(), details);
        }
        if (generator.options.has(generator.outputFile)) {
            FileUtils.writeStringToFile(new File(generator.options.valueOf(generator.outputFile)), gson.toJson(resultMap),
                    StandardCharsets.UTF_8);
        } else {
            System.out.println(gson.toJson(resultMap));
        }
    }
}
