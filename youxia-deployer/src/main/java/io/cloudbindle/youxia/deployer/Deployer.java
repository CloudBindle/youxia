package io.cloudbindle.youxia.deployer;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.auth.profile.ProfilesConfigFile;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.AvailabilityZone;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesResult;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.iterable.S3Objects;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.cloudbindle.youxia.sensu.api.Client;
import io.cloudbindle.youxia.util.ConfigTools;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.BuiltinHelpFormatter;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

/**
 * This class maintains a fleet of amazon instances dependent on state retrieved from sensu.
 * 
 * Before you run this code, be sure to fill in your AWS security credentials in the src/main/resources/AwsCredentials.properties file in
 * this project.
 */
public class Deployer {

    private static final int DEFAULT_SENSU_PORT = 4567;
    /*
     * Important: Be sure to fill in your AWS access credentials in the AwsCredentials.properties file in this project before you run this
     * sample. http://aws.amazon.com/security-credentials
     */
    static AmazonEC2 ec2;
    static AmazonS3 s3;

    /**
     * The only information needed to create a client are security credentials - your AWS Access Key ID and Secret Access Key. All other
     * configuration, such as the service endpoints have defaults provided.
     * 
     * Additional client parameters, such as proxy configuration, can be specified in an optional ClientConfiguration object when
     * constructing a client.
     * 
     * @see com.amazonaws.auth.BasicAWSCredentials
     * @see com.amazonaws.auth.PropertiesCredentials
     * @see com.amazonaws.ClientConfiguration
     */
    private static void populateAWSCredentials() throws Exception {
        /*
         * ProfileCredentialsProvider loads AWS security credentials from a .aws/config file in your home directory.
         * 
         * These same credentials are used when working with the AWS CLI.
         * 
         * You can find more information on the AWS profiles config file here:
         * http://docs.aws.amazon.com/cli/latest/userguide/cli-chap-getting-started.html
         */
        File configFile = new File(System.getProperty("user.home"), ".aws/config");
        AWSCredentialsProvider credentialsProvider = new ProfileCredentialsProvider(new ProfilesConfigFile(configFile), "default");

        if (credentialsProvider.getCredentials() == null) {
            throw new RuntimeException("No AWS security credentials found:\n" + "Make sure you've configured your credentials in: "
                    + configFile.getAbsolutePath() + "\n" + "For more information on configuring your credentials, see "
                    + "http://docs.aws.amazon.com/cli/latest/userguide/cli-chap-getting-started.html");
        }

        ec2 = new AmazonEC2Client(credentialsProvider);
        s3 = new AmazonS3Client(credentialsProvider);
    }

    public static void main(String[] args) throws Exception {

        OptionParser parser = new OptionParser();

        parser.acceptsAll(Arrays.asList("help", "h", "?"), "Provides this help message.");

        ArgumentAcceptingOptionSpec<Integer> totalNodes = parser
                .acceptsAll(Arrays.asList("total-nodes-num", "t"), "Total number of spot and on-demand instances to maintain.")
                .withRequiredArg().ofType(Integer.class).required();
        ArgumentAcceptingOptionSpec<Float> maxSpotPrice = parser
                .acceptsAll(Arrays.asList("max-spot-price", "p"), "Maximum price to pay for spot-price instances.").withRequiredArg()
                .ofType(Float.class).required();
        ArgumentAcceptingOptionSpec<Integer> waitTime = parser
                .acceptsAll(Arrays.asList("wait-time", "w"),
                        "Number of seconds to wait before replacing an offline (not-responding to keepalive) instance").withRequiredArg()
                .ofType(Integer.class).required();
        ArgumentAcceptingOptionSpec<Integer> batchSize = parser
                .acceptsAll(Arrays.asList("batch-size", "s"), "Number of instances to bring up at one time").withRequiredArg()
                .ofType(Integer.class).required();
        ArgumentAcceptingOptionSpec<String> sensuHost = parser.acceptsAll(Arrays.asList("sensu-host", "sh"), "URL for the sensu host")
                .withRequiredArg().ofType(String.class).defaultsTo("localhost");
        ArgumentAcceptingOptionSpec<Integer> sensuPort = parser
                .acceptsAll(Arrays.asList("sensu-port", "sp"), "Port for the sensu server api").withRequiredArg().ofType(Integer.class)
                .required().defaultsTo(DEFAULT_SENSU_PORT);

        OptionSet options = null;
        try {
            options = parser.parse(args);
        } catch (OptionException e) {
            final int helpNumColumns = 160;
            parser.formatHelpWith(new BuiltinHelpFormatter(helpNumColumns, 2));
            parser.printHelpOn(System.out);
            System.exit(-1);
        }
        assert (options != null);

        populateAWSCredentials();
        HierarchicalINIConfiguration youxiaConfig = ConfigTools.getYouxiaConfig();

        // Talk to sensu and determine number of AWS clients that are active
        CloseableHttpClient httpclient = HttpClients.createDefault();
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(new AuthScope(options.valueOf(sensuHost), options.valueOf(sensuPort)),
                new UsernamePasswordCredentials(youxiaConfig.getString("username"), youxiaConfig.getString("password")));

        HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);

        URI uri = new URIBuilder().setScheme("http").setPort(options.valueOf(sensuPort)).setHost(options.valueOf(sensuHost))
                .setPath("/clients").build();
        HttpGet httpget = new HttpGet(uri);
        System.out.println("Looking at " + httpget.toString());

        List<Client> awsClients = Lists.newArrayList();
        try (CloseableHttpResponse response = httpclient.execute(httpget, context)) {
            System.out.println(response.toString());
            try (InputStreamReader reader = new InputStreamReader(response.getEntity().getContent())) {
                Gson gson = new GsonBuilder().create();
                Client[] clients = gson.fromJson(reader, Client[].class);
                for (Client client : clients) {
                    if (client.getEnvironment().getAnsible_system_vendor().equals("")
                            && client.getEnvironment().getAnsible_product_name().equals("")) {
                        // TODO: find better way to denote AWS clients aside from the abscence of openstack vendor or product name
                        awsClients.add(client);
                    }
                }
            }
        }
        System.out.println("Found " + awsClients.size() + " AWS clients");

        try {
            /*
             * The Amazon EC2 client allows you to easily launch and configure computing capacity in AWS datacenters.
             * 
             * In this sample, we use the EC2 client to list the availability zones in a region, and then list the instances running in
             * those zones.
             */
            DescribeAvailabilityZonesResult availabilityZonesResult = ec2.describeAvailabilityZones();
            List<AvailabilityZone> availabilityZones = availabilityZonesResult.getAvailabilityZones();
            System.out.println("You have access to " + availabilityZones.size() + " availability zones:");
            for (AvailabilityZone zone : availabilityZones) {
                System.out.println(" - " + zone.getZoneName() + " (" + zone.getRegionName() + ")");
            }

            DescribeInstancesResult describeInstancesResult = ec2.describeInstances();
            Set<Instance> instances = new HashSet<>();
            for (Reservation reservation : describeInstancesResult.getReservations()) {
                instances.addAll(reservation.getInstances());
            }

            System.out.println("You have " + instances.size() + " Amazon EC2 instance(s) running.");

            /*
             * The Amazon S3 client allows you to manage and configure buckets and to upload and download data.
             * 
             * In this sample, we use the S3 client to list all the buckets in your account, and then iterate over the object metadata for
             * all objects in one bucket to calculate the total object count and space usage for that one bucket. Note that this sample only
             * retrieves the object's metadata and doesn't actually download the object's content.
             * 
             * In addition to the low-level Amazon S3 client in the SDK, there is also a high-level TransferManager API that provides
             * asynchronous management of uploads and downloads with an easy to use API:
             * http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/s3/transfer/TransferManager.html
             */
            List<Bucket> buckets = s3.listBuckets();
            System.out.println("You have " + buckets.size() + " Amazon S3 bucket(s).");

            if (buckets.size() > 0) {
                Bucket bucket = buckets.get(0);

                long totalSize = 0;
                long totalItems = 0;
                /*
                 * The S3Objects and S3Versions classes provide convenient APIs for iterating over the contents of your buckets, without
                 * having to manually deal with response pagination.
                 */
                for (S3ObjectSummary objectSummary : S3Objects.inBucket(s3, bucket.getName())) {
                    totalSize += objectSummary.getSize();
                    totalItems++;
                }

                System.out.println("The bucket '" + bucket.getName() + "' contains " + totalItems + " objects " + "with a total size of "
                        + totalSize + " bytes.");
            }
        } catch (AmazonServiceException ase) {
            /*
             * AmazonServiceExceptions represent an error response from an AWS services, i.e. your request made it to AWS, but the AWS
             * service either found it invalid or encountered an error trying to execute it.
             */
            System.out.println("Error Message:    " + ase.getMessage());
            System.out.println("HTTP Status Code: " + ase.getStatusCode());
            System.out.println("AWS Error Code:   " + ase.getErrorCode());
            System.out.println("Error Type:       " + ase.getErrorType());
            System.out.println("Request ID:       " + ase.getRequestId());
        } catch (AmazonClientException ace) {
            /*
             * AmazonClientExceptions represent an error that occurred inside the client on the local host, either while trying to send the
             * request to AWS or interpret the response. For example, if no network connection is available, the client won't be able to
             * connect to AWS to execute a request and will throw an AmazonClientException.
             */
            System.out.println("Error Message: " + ace.getMessage());
        }
    }
}
