package io.cloudbindle.youxia.decider;

import com.amazonaws.services.simpledb.AmazonSimpleDBClient;
import com.amazonaws.services.simpledb.model.Attribute;
import com.amazonaws.services.simpledb.model.Item;
import com.amazonaws.services.simpledb.model.SelectRequest;
import com.amazonaws.services.simpledb.model.SelectResult;
import com.google.common.collect.Lists;
import com.google.common.reflect.TypeToken;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.cloudbindle.youxia.pawg.api.ClusterDetails;
import io.cloudbindle.youxia.util.ConfigTools;
import io.cloudbindle.youxia.util.Constants;
import io.seqware.Engines;
import io.seqware.pipeline.SqwKeys;
import io.seqware.pipeline.api.Scheduler;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.BuiltinHelpFormatter;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpecBuilder;
import net.sourceforge.seqware.common.metadata.MetadataFactory;
import net.sourceforge.seqware.common.metadata.MetadataWS;
import net.sourceforge.seqware.common.module.ReturnValue;
import net.sourceforge.seqware.common.util.Log;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;

/**
 * This is a mock decider that schedules HelloWorld workflows in a youxia enabled ecosystem
 * 
 */
public class Decider {

    private final OptionSet options;
    private final OptionSpecBuilder testMode;
    private final HierarchicalINIConfiguration youxiaConfig;
    private final ArgumentAcceptingOptionSpec<String> json;
    private Map<String, ClusterDetails> instances;

    /**
     * This stores all potential greetings, the decider should not schedule the same greeting again after it has completed.
     */
    public final Set<String> potentialGreetings = new HashSet<>();

    public Decider(String[] args) {
        this.youxiaConfig = ConfigTools.getYouxiaConfig();
        // TODO: validate that all used properties are present

        OptionParser parser = new OptionParser();

        parser.acceptsAll(Arrays.asList("help", "h", "?"), "Provides this help message.");

        this.json = parser.acceptsAll(Arrays.asList("instance-json", "j"), "A list of instances that we can schedule workflows onto")
                .withRequiredArg().required();
        this.testMode = parser.acceptsAll(Arrays.asList("test", "t"),
                "In test mode, we only output workflows that would have been scheduled");

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

        // populate greetings
        final int randomGreetingLength = 10;
        final int numberGreetings = 100;
        final int randomSeed = 4; // random seed chosen by roll of the dice ;)
        Random random = new Random(randomSeed); // make sure we choose the same "random" greetings each time
        for (int i = 0; i < numberGreetings; i++) {
            potentialGreetings.add(RandomStringUtils.random(randomGreetingLength, 0, 0, true, true, null, random));
        }
    }

    private void loadAvailableInstances() {
        try {
            Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).disableHtmlEscaping()
                    .setPrettyPrinting().create();
            File instanceJson = new File(options.valueOf(this.json));
            String readFileToString = FileUtils.readFileToString(instanceJson, StandardCharsets.UTF_8);
            Type mapType = new TypeToken<Map<String, ClusterDetails>>() {
            }.getType();
            this.instances = gson.fromJson(readFileToString, mapType);
            Log.stdout(this.instances.size() + " instances loaded");
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void readInGreetingsAndEliminateCompletedOnes() {
        Log.stdout(this.potentialGreetings.size() + " greetings before filtering");
        AmazonSimpleDBClient simpleDBClient = ConfigTools.getSimpleDBClient();
        final String domainName = youxiaConfig.getString(ConfigTools.YOUXIA_MANAGED_TAG) + Constants.WORKFLOW_RUNS;
        SelectResult select = simpleDBClient.select(new SelectRequest("select * from `" + domainName + "`"));
        for (Item item : select.getItems()) {
            // get greetings and workflow status
            String greetings = null;
            String status = null;
            for (Attribute attribute : item.getAttributes()) {
                if (attribute.getName().equals("status")) {
                    status = attribute.getValue();
                }
                if (attribute.getName().equals(Constants.INI_FILE + ".greeting")) {
                    greetings = attribute.getValue();
                }
            }
            if (greetings == null || status == null) {
                Log.error("Workflow run " + item.getName() + " did not have greeting or a status");
                Log.error("greeting: " + greetings + " status: " + status);
                continue;
            }
            if ("failed".equals(status) || "completed".equals(status)) {
                this.potentialGreetings.remove(greetings);
            }
        }
        Log.stdout(this.potentialGreetings.size() + " potential greetings left after filtering");
    }

    private void scheduleWorkflowRuns() {
        Log.stdout("Starting scheduling");
        for (Entry<String, ClusterDetails> entry : instances.entrySet()) {
            Iterator<String> iterator = this.potentialGreetings.iterator();
            if (!iterator.hasNext()) {
                Log.stdout("Out of greetings exiting");
                return;
            }
            String greeting = iterator.next();
            iterator.remove();
            Map<String, String> config = new HashMap<>();
            config.put(SqwKeys.SW_METADATA_METHOD.getSettingKey(), "webservice");
            config.put(SqwKeys.SW_REST_URL.getSettingKey(), entry.getValue().getWebservice());
            config.put(SqwKeys.SW_REST_USER.getSettingKey(), entry.getValue().getUsername());
            config.put(SqwKeys.SW_REST_PASS.getSettingKey(), entry.getValue().getPassword());
            MetadataWS ws = MetadataFactory.getWS(config);
            Scheduler scheduler = new Scheduler(ws, config);

            // this is ugly, this should be a template or something
            String iniFile = " key=input_file:type=file:display=F:file_meta_type=text/plain\n"
                    + "input_file=${workflow_bundle_dir}/Workflow_Bundle_HelloWorld/1.0-SNAPSHOT/data/input.txt\n"
                    + "# key=greeting:type=text:display=T:display_name=Greeting\n"
                    + "greeting="
                    + greeting
                    + "\n"
                    + "\n"
                    + "cat=${workflow_bundle_dir}/Workflow_Bundle_HelloWorld/1.0-SNAPSHOT/bin/gnu-coreutils-5.67/cat\n"
                    + "echo=${workflow_bundle_dir}/Workflow_Bundle_HelloWorld/1.0-SNAPSHOT/bin/gnu-coreutils-5.67/echo\n"
                    + "\n"
                    + "# the output directory is a convention used in many workflows to specify a relative output path\n"
                    + "output_dir=seqware-results\n"
                    + "# the output_prefix is a convention used to specify the root of the absolute output path or an S3 bucket name \n"
                    + "# you should pick a path that is available on all cluster nodes and can be written by your user\n"
                    + "output_prefix=./\n"
                    + "# manual output is provided as an example of a workflow parameter that modifies output behaviour. \n"
                    + "# If false, the workflow will create output files in a directory specified by output_prefix/output_dir/workflowname_version/RANDOM/ where RANDOM is an integer. \n"
                    + "# If true, the workflow places the files at output_prefix/output_dir and may overwrite existing files.\" \n"
                    + "manual_output=false\n" + "\n" + "seqware_bundle_version_number=${sqw.bundle-seqware-version}\n" + "\n";
            File tempFile;
            try {
                tempFile = Files.createTempFile("workflow", "ini").toFile();
                FileUtils.write(tempFile, iniFile, StandardCharsets.UTF_8);
                if (options.has(testMode)) {
                    Log.stdout("Would have scheduled onto " + entry.getKey() + " with greeting " + greeting);
                } else {
                    // TODO: hardcode the schedule to master until the generator is fixed
                    Log.stdout("Scheduled onto " + entry.getKey() + " with greeting " + greeting);
                    ReturnValue scheduleInstalledBundle = scheduler.scheduleInstalledBundle(entry.getValue().getWorkflowAccession(),
                            Lists.newArrayList(tempFile.getAbsolutePath()), false, new ArrayList<String>(), new ArrayList<String>(),
                            new ArrayList<String>(), "master", Engines.TYPES.oozie_sge.getCliString(), new HashSet<Integer>(), true);
                    if (scheduleInstalledBundle.getExitStatus() != ReturnValue.SUCCESS) {
                        throw new RuntimeException("Failed scheduling");
                    }
                }
            } catch (IOException ex) {
                throw new RuntimeException("Could not write ini file");
            }

        }
        Log.stdout("Out of instances exiting");
    }

    public static void main(String[] args) throws Exception {
        Decider decider = new Decider(args);
        decider.loadAvailableInstances();
        decider.readInGreetingsAndEliminateCompletedOnes();
        decider.scheduleWorkflowRuns();
    }
}
