package io.cloudbindle.youxia.q2seqware;

import io.cloudbindle.youxia.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;

/**
 * Checks to see if a local seqware instances is currently running. This is done by calling seqware from the command line.
 * @author sshorser
 *
 */
public class SeqwareStatusMonitor {

    private static Pattern statusPattern = Pattern.compile("Workflow Run Status\\s*\\|\\s*(.+)");
    private static String workflowNamePatternPrefix = "Name\\s*\\|\\s*";
    private static String workflowVersionPatternPrefix = "Version\\s*\\|\\s*";
    private static Pattern accessionPattern = Pattern.compile("SeqWare Accession\\s*\\|\\s*(.+)");

    /**
     * Checks if seqware has any jobs with status "pending"
     * @return true if any jobs are pending.
     */
    public static boolean anyPendingJobs() {
        return checkForJobsWithStatus("pending");
    }

    /**
     * Checks if seqware has any jobs with status "submitted"
     * @return true if any jobs are submitted.
     */
    public static boolean anySubmittedJobs() {
        return checkForJobsWithStatus("submitted");
    }

    /**
     * Checks if seqware has any jobs with status "running"
     * @return true if any jobs are running.
     */
    public static boolean anyRunningJobs() {
        return checkForJobsWithStatus("running");
    }

    /**
     * Checks to see if seqware has any jobs with the given status.
     * @param status - the status to check for.
     * @return true if any jobs have the given status.
     */
    private static boolean checkForJobsWithStatus(String status) {
        boolean jobsWithStatus = false;

        String cmdResponse = null;
        String command = "seqware workflow report --status " + status;
        CommandLine cli = new CommandLine(command);
        DefaultExecutor executor = new DefaultExecutor();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);

        Log.trace("Checking Seqware for any " + status + " jobs.");

        executor.setStreamHandler(streamHandler);

        try {
            executor.execute(cli);
            cmdResponse = outputStream.toString();
            // If there is ANY response to this command, it means there is some job with the given status.
            if (!cmdResponse.trim().equals("")) {
                jobsWithStatus = true;
            }
        } catch (IOException e) {

            if (e.getMessage().contains("Cannot run program \"seqware workflow report") && e.getMessage().contains("No such file or directory")) {
                Log.error("Could not call SeqWare. Perhaps it is not installed correctly on this system?\nFull Message:\n"+e.getMessage());
            } else {
                e.printStackTrace();
            }
        }
        return jobsWithStatus;
    }

    /**
     * Report on the status of an executed workflow instance, as identified by a given accessionID.
     * @param accessionID
     * @return The status, as a string.
     */
    public static String checkSeqwareStatus(String accessionID) {
        String cmdResponse = null;
        String status = null;
        String command = "seqware workflow report --accession " + accessionID;
        CommandLine cli = new CommandLine(command);
        DefaultExecutor executor = new DefaultExecutor();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);

        Log.trace("Checking Seqware with accession: " + accessionID);

        executor.setStreamHandler(streamHandler);
        try {
            executor.execute(cli);
            cmdResponse = outputStream.toString();
            // Check the response string for the status.
            Matcher matcher = statusPattern.matcher(cmdResponse);

            if (matcher.find()) {
                status = matcher.group(1);
            } else {
                Log.error("Could not determine status from SeqWare output:\n" + cmdResponse);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return status;
    }

    /**
     * Gets the accessionID of a workflow that is identified by a name and version.
     * @param workflowName - the name of the workflow
     * @param workflowVersion - the version of the workflow.
     * @return The accessionID of the workflow. You can use this accession ID to execute the workflow.
     */
    public static String getAccessionID(String workflowName, String workflowVersion) {
        String cmdResponse = null;
        String accessionID = null;
        String command = "seqware workflow list";

        CommandLine cli = new CommandLine(command);
        DefaultExecutor executor = new DefaultExecutor();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);

        Log.trace("Querying SeqWare for workflow/version: " + workflowName + " / " + workflowVersion);

        executor.setStreamHandler(streamHandler);
        try {
            executor.execute(cli);
            cmdResponse = outputStream.toString();
            Pattern workflowNamePattern = Pattern.compile(workflowNamePatternPrefix + workflowName);
            Pattern workflowVersionPattern = Pattern.compile(workflowVersionPatternPrefix + workflowVersion);

            Matcher nameMatcher = workflowNamePattern.matcher(cmdResponse);
            Matcher versionMatcher = workflowVersionPattern.matcher(cmdResponse);
            // First try to find the given workflow name and version in the command ouptput.
            if (nameMatcher.find() && versionMatcher.find()) {
                // If the name and version number were found, try to extrac the accession.
                Matcher accessionMatcher = accessionPattern.matcher(cmdResponse);
                if (accessionMatcher.find()) {
                    accessionID = accessionMatcher.group(1);
                } else {
                    Log.error("Could not determine accession ID from SeqWare output:\n" + cmdResponse);
                }
            } else {
                Log.error("Could not determine accession ID from SeqWare output:\n" + cmdResponse);
            }
        } catch (IOException e) {
            if (e.getMessage().contains("Cannot run program \"seqware workflow list\"") && e.getMessage().contains("No such file or directory")) {
                Log.error("Could not get the seqware workflow list. Perhaps SeqWare is not installed correctly on this system?\nFull message:\n" + e.getMessage());
            } else {
                e.printStackTrace();
            }
        }

        return accessionID;
    }
}
