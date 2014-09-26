package io.cloudbindle.youxia.generator;

import io.cloudbindle.youxia.util.ConfigTools;
import java.io.IOException;
import java.util.Arrays;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.BuiltinHelpFormatter;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.apache.commons.configuration.HierarchicalINIConfiguration;

/**
 * The generator aggregates inventory information based on instances tagged in AWS, manually created JSON files, and instance metadata on
 * Openstack.
 * 
 * Before you run this code, be sure to fill in your ~/.youxia/config and ~/.aws/config
 */
public class Generator {

    private static final long SLEEP_CYCLE = 60000;
    private final ArgumentAcceptingOptionSpec<Integer> totalNodes;
    private final ArgumentAcceptingOptionSpec<Float> maxSpotPrice;
    private final ArgumentAcceptingOptionSpec<Integer> batchSize;
    private OptionSet options;
    private final HierarchicalINIConfiguration youxiaConfig;
    public static final String DEPLOYER_INSTANCE_TYPE = "deployer.instance_type";

    private final ArgumentAcceptingOptionSpec<String> playbook;

    public Generator(String[] args) {
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

    public static void main(String[] args) throws Exception {

        Generator deployer = new Generator(args);
    }
}
