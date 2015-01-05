package io.cloudbindle.youxia.q2seqware;

import io.cloudbindle.youxia.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.PumpStreamHandler;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericXmlApplicationContext;

public class ExternalSeqwareScheduler {
    private static final String SPRING_CONFIG_FILE = "q2seqware-spring-config.xml";
    private static SeqwareStatusMonitor monitor = new SeqwareStatusMonitor();
    private static String workflowName = null;
    private static String workflowVersion = null;
    
    
    public static void main(String[] args) {
        ApplicationContext context = new GenericXmlApplicationContext(SPRING_CONFIG_FILE);
        
        getCLIArgs(args);

        if (workflowName == null || workflowName.trim().equals("")) {
            Log.error("Please specify a workflow name using the \"workflow\" option");
        } else if (workflowVersion == null || workflowVersion.trim().equals("")) {
            Log.error("Please specify a workflow version using the \"version\" option");
        } else {
            // 1) Query SeqWare to see if it can accept a new job.
            String accessionID = monitor.getAccessionID(workflowName, workflowVersion);
            // String status = monitor.checkSeqwareStatus(accessionID);
            if (accessionID != null) {
                // 2) If SeqWare isn't busy, get a message from the Queue/MessageSource and call SeqWare.
                // if (status.equals("") || !status.equals("running"))
                callSeqware(context, accessionID);
            } else {
                Log.error("Could not determine accession ID for workflow with name:" + workflowName + "; version: " + workflowVersion
                        + ". Please check that the workflow you specified is installed correctly. Cannot proceed, program ending.");
            }
        }
        ((ConfigurableApplicationContext) context).close();
    }

    private static void callSeqware(ApplicationContext context, String accessionID) {
        if (canScheduleNewJobs(workflowName, workflowVersion)) {
            // SeqwareJobMessageSource listener = new RabbitMQListener();
            // SeqwareJobMessageSource listener = new SeqwareCGIMessageSource();
            SeqwareJobMessageSource listener = context.getBean(SeqwareCGIMessageSource.class);
            // listener.setSourceHost("localhost");
            // listener.setQueueName("seqware_job_queue");
            // listener.setDurableQueue(true);
            // listener.setAutoAck(true);

            String message = listener.getMessage();
            if (message != null) {
                // SeqwareJobMessageProcessor messageProcessor = new SeqwareJobMessageProcessor();
                SeqwareJobMessageProcessor messageProcessor = context.getBean(SeqwareJobMessageProcessor.class);
                String pathToINI = messageProcessor.processMessage(message);
                // Now we have to call SeqWare with the INI we just created.
                scheduleSeqwareJob(pathToINI, accessionID);
            }

        } else {
            // Otherwise, simply report on the status of the currently executing SeqWare workflow.
            String currentStatus = monitor.checkSeqwareStatus(accessionID);
            SeqwareStatusReporter reporter = context.getBean(SeqwareStatusReporter.class);
            reporter.reportSeqwareStatus(currentStatus);
        }
    }

    private static void getCLIArgs(String[] args) {
        CommandLineParser parser = new BasicParser();
        Options options = new Options();
        Option workflowNameOption = new Option("workflow", true, "The name of the workflow to monitor");
        Option workflowVersionOption = new Option("version", true, "The version of the workflow to monitor");
        options.addOption(workflowVersionOption);
        options.addOption(workflowNameOption);
        try {
            org.apache.commons.cli.CommandLine line = parser.parse(options, args);
            if (line.hasOption("workflow")) {
                workflowName = line.getOptionValue("workflow");
            }
            if (line.hasOption("version")) {
                workflowVersion = line.getOptionValue("version");
            }
        } catch (ParseException e) {
            Log.error("Error parsing command-line arguments!");
            e.printStackTrace();
        }
    }

    private static boolean canScheduleNewJobs(String workflowName, String workflowVersion) {
        boolean canSchedule = false;

        boolean anySubmitted = monitor.anySubmittedJobs();
        boolean anyPending = monitor.anyPendingJobs();
        boolean anyRunning = monitor.anyRunningJobs();

        canSchedule = !anySubmitted && !anyPending && !anyRunning;

        return canSchedule;
    }

    private static void scheduleSeqwareJob(String pathToINI, String accessionID) {
        String command = "seqware workflow --accession " + accessionID + " --ini " + pathToINI + " --host localhost";
        String cmdResponse = null;
        CommandLine cli = new CommandLine(command);
        DefaultExecutor executor = new DefaultExecutor();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
        Log.trace("Scheduling SeqWare job with accesionID: " + accessionID);

        executor.setStreamHandler(streamHandler);
        try {
            executor.execute(cli);
            cmdResponse = outputStream.toString();
            Log.trace("SeqWare command response:\n"+cmdResponse);
        } catch (ExecuteException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}
