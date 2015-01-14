package io.cloudbindle.youxia.q2seqware;

import io.cloudbindle.youxia.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map.Entry;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonStreamParser;

/**
 * Process messages that describe seqware jobs.
 * @author sshorser
 *
 */
public class SeqwareJobMessageProcessor {
    private String pathToINIs = "/tmp/inis4seqware/";

    /**
     * Return the path to where INI files will be written.
     * @return
     */
    public String getPathToINIs() {
        return pathToINIs;
    }
    
    /**
     * Set the path where INI files will be written.
     * @param pathToINIs
     */
    public void setPathToINIs(String pathToINIs) {
        this.pathToINIs = pathToINIs;
    }

    /**
     * This method will process a JSON message and write an INI file for SeqWare.
     * 
     * @param message
     *            The JSON message (as a string) to process
     * @return The full path to the ini file.
     * 
     */
    public String processMessage(String message) {
        JsonObject jsonObj = new Gson().fromJson(message, JsonObject.class);

        String jobId = jsonObj.get("job_id").getAsString();
        String queueName = jsonObj.get("queue_name").getAsString();
        String workflow = jsonObj.get("workflow").getAsString();
        String workflowVersion = jsonObj.get("workflow_version").getAsString();
        String timeQueued = jsonObj.get("time_queued").getAsString();
        JsonObject iniArgs = jsonObj.get("ini").getAsJsonObject();

        String inputFile = iniArgs.get("input_file").getAsString();
        String outputPrefix = iniArgs.get("output_prefix").getAsString();

        HierarchicalINIConfiguration config = new HierarchicalINIConfiguration();

        JsonStreamParser parser = new JsonStreamParser(iniArgs.toString());
        // Map<String,String> iniSettings = new HashMap<String,String>();
        while (parser.hasNext()) {
            JsonElement element = parser.next();
            JsonObject elementObject = element.getAsJsonObject();
            for (Entry<String, JsonElement> e : elementObject.entrySet()) {
                config.addProperty(e.getKey(), e.getValue().getAsString());
            }
        }

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("YYYYMMdd_HHmmss");
        String dateString = simpleDateFormat.format(new Date());
        String iniFileName = pathToINIs + "workflow_" + dateString + ".ini";
        Log.info("Writing INI file to: " + iniFileName);
        try {
            config.save(iniFileName);
        } catch (ConfigurationException e) {
            e.printStackTrace();
        }
        return iniFileName;
    }
}
