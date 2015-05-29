package io.cloudbindle.youxia.reaper;

import com.amazonaws.services.simpledb.AmazonSimpleDBClient;
import com.amazonaws.services.simpledb.model.Attribute;
import com.amazonaws.services.simpledb.model.Item;
import com.amazonaws.services.simpledb.model.SelectRequest;
import com.amazonaws.services.simpledb.model.SelectResult;
import io.cloudbindle.youxia.util.ConfigTools;
import io.cloudbindle.youxia.util.Constants;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import static java.lang.System.out;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.BuiltinHelpFormatter;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpecBuilder;
import net.sourceforge.seqware.common.util.Log;
import org.apache.commons.configuration.HierarchicalINIConfiguration;

/**
 * This class is a utility to convert from SimpleDB data to BWA's blacklist format.
 *
 */
public class BWABlackListConverter {

    private final OptionSet options;
    private final HierarchicalINIConfiguration youxiaConfig;
    public static final String WORKFLOW_RUNS = ".workflow_runs";
    private final ArgumentAcceptingOptionSpec<String> outputFile;
    private final OptionSpecBuilder help;

    public BWABlackListConverter(String[] args) throws IOException {
        this.youxiaConfig = ConfigTools.getYouxiaConfig();
        // TODO: validate that all used properties are present

        OptionParser parser = new OptionParser();

        this.help = parser.acceptsAll(Arrays.asList("help", "h", "?"), "Provides this help message.");
        this.outputFile = parser.acceptsAll(Arrays.asList("output", "o"), "Save output to a json file").withRequiredArg()
                .defaultsTo("blacklist.txt");

        try {
            this.options = parser.parse(args);
        } catch (OptionException e) {
            try {
                showHelp(parser);
                throw new RuntimeException("Showing usage");
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
        assert (options != null);
        if (options.has(help)) {
            showHelp(parser);
        }
    }

    private void showHelp(OptionParser parser) throws IOException {
        final int helpNumColumns = 160;
        parser.formatHelpWith(new BuiltinHelpFormatter(helpNumColumns, 2));
        parser.printHelpOn(System.out);
        throw new RuntimeException("Showing usage");
    }

    private void listWorkflowRuns() {
        AmazonSimpleDBClient simpleDBClient = ConfigTools.getSimpleDBClient();

        Writer outWriter = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        if (options.has(outputFile)) {
            try {
                outWriter = Files.newBufferedWriter(Paths.get(options.valueOf(outputFile)), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }

        try (BufferedWriter writer = new BufferedWriter(outWriter)) {
            final String domainName = youxiaConfig.getString(ConfigTools.YOUXIA_MANAGED_TAG) + WORKFLOW_RUNS;
            SelectResult select = simpleDBClient.select(new SelectRequest("select * from `" + domainName + "`"));
            for (Item item : select.getItems()) {
                handleItem(item, writer);
            }
            while (select.getNextToken() != null) {
                select = simpleDBClient
                        .select(new SelectRequest("select * from `" + domainName + "`").withNextToken(select.getNextToken()));
                for (Item item : select.getItems()) {
                    handleItem(item, writer);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private void handleItem(Item item, final BufferedWriter writer) throws IOException {
        // get greetings and workflow status
        String sampleID = null;
        String status = null;
        for (Attribute attribute : item.getAttributes()) {
            if (attribute.getName().equals("status")) {
                status = attribute.getValue();
            }
            if (attribute.getName().equals(Constants.INI_FILE + ".sample_id")) {
                sampleID = attribute.getValue();
            }
        }
        if (sampleID == null || status == null) {
            Log.error("Workflow run " + item.getName() + " did not have sample_id or a status");
            Log.error("sample_id: " + sampleID + " status: " + status);
        }
        if ("failed".equals(status) || "completed".equals(status)) {
            writer.write(sampleID + '\n');
        }
    }

    public static void main(String[] args) throws Exception {

        BWABlackListConverter converter = new BWABlackListConverter(args);
        converter.listWorkflowRuns();
    }
}
