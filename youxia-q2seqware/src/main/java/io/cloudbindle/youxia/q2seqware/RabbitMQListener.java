package io.cloudbindle.youxia.q2seqware;

import io.cloudbindle.youxia.util.Log;

import java.io.IOException;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.ConsumerCancelledException;
import com.rabbitmq.client.ShutdownSignalException;

public class RabbitMQListener implements QueueListener {

    private String hostName;
    private String queueName;
    private boolean autoAck;
    private boolean durableQueue;
    private String pathToINIs = "/tmp/inis4seqware/";

    @Override
    public String getSourceHost() {
        return this.hostName;
    }

    @Override
    public String getQueueName() {
        return this.queueName;
    }

    @Override
    public void setSourceHost(String hostName) {
        this.hostName = hostName;
    }

    @Override
    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    @Override
    public void getMessageFromQueue() {

    }

    public boolean isAutoAck() {
        return autoAck;
    }

    public void setAutoAck(boolean autoAck) {
        this.autoAck = autoAck;
    }

    public boolean isDurableQueue() {
        return durableQueue;
    }

    public void setDurableQueue(boolean durableQueue) {
        this.durableQueue = durableQueue;
    }

    public String getPathToINIs() {
        return pathToINIs;
    }

    public void setPathToINIs(String pathToINIs) {
        this.pathToINIs = pathToINIs;
    }

    public String getMessage() {
        String message = null;

        ConnectionFactory connFactory = new ConnectionFactory();
        connFactory.setHost(this.getSourceHost());
        Connection connection = null;
        Channel channel = null;
        try {
            connection = connFactory.newConnection();
            channel = connection.createChannel();
            channel.queueDeclare(this.getQueueName(), this.isDurableQueue(), false, false, null);
            Log.info("Listening for messages on queue: " + this.getQueueName() + " at host: " + this.getSourceHost());
            SeqwareMessageConsumer consumer = new SeqwareMessageConsumer(channel);
            channel.basicConsume(this.getQueueName(), this.isAutoAck(), consumer);
            boolean keepListening = true;
            /*
             * while (keepListening) { QueueingConsumer.Delivery delivery = consumer.nextDelivery(); message = new
             * String(delivery.getBody()); Log.info("message received: "+message);
             * 
             * if (message.equals("EXIT")) { Log.info("EXIT message recieved. Shutting down..."); keepListening = false; } else {
             * processMessage(message); }
             * 
             * 
             * if (!this.isAutoAck()) { channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false); } }
             */
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ShutdownSignalException e) {
            e.printStackTrace();
        } catch (ConsumerCancelledException e) {
            e.printStackTrace();
        } finally {
            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (connection != null) {
                try {
                    connection.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return message;
    }

//    private void processMessage(String message) {
//        JsonObject jsonObj = new Gson().fromJson(message, JsonObject.class);
//
//        String jobId = jsonObj.get("job_id").getAsString();
//        String queueName = jsonObj.get("queue_name").getAsString();
//        String workflow = jsonObj.get("workflow").getAsString();
//        String workflowVersion = jsonObj.get("workflow_version").getAsString();
//        String timeQueued = jsonObj.get("time_queued").getAsString();
//        JsonObject iniArgs = jsonObj.get("ini").getAsJsonObject();
//
//        String inputFile = iniArgs.get("input_file").getAsString();
//        String outputPrefix = iniArgs.get("output_prefix").getAsString();
//
//        HierarchicalINIConfiguration config = new HierarchicalINIConfiguration();
//
//        JsonStreamParser parser = new JsonStreamParser(iniArgs.toString());
//        // Map<String,String> iniSettings = new HashMap<String,String>();
//        while (parser.hasNext()) {
//            JsonElement element = parser.next();
//            JsonObject elementObject = element.getAsJsonObject();
//            for (Entry<String, JsonElement> e : elementObject.entrySet()) {
//                config.addProperty(e.getKey(), e.getValue().getAsString());
//            }
//        }
//
//        /*
//         * for (String k : iniSettings.keySet()) { config.addProperty(k, iniSettings.get(k)); }
//         */
//        /*
//         * config.addProperty("inputFile", inputFile); config.addProperty("outputPrefix", outputPrefix);
//         */
//        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("YYYYMMdd_HHmmss");
//        String dateString = simpleDateFormat.format(new Date());
//        String iniFileName = pathToINIs + "workflow_" + dateString + ".ini";
//        Log.info("Writing INI file to: " + iniFileName);
//        try {
//            config.save(iniFileName);
//        } catch (ConfigurationException e) {
//            // TODO Auto-generated catch block
//            e.printStackTrace();
//        }
//    }

}
