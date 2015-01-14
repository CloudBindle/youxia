package io.cloudbindle.youxia.q2seqware;

import io.cloudbindle.youxia.util.Log;

import java.io.IOException;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.ConsumerCancelledException;
import com.rabbitmq.client.ShutdownSignalException;
/**
 * Gets messages from a RabbitMQ queue for seqware.<br/><br/>
 * NOTE: Implementation is not yet complete (2015-01-14).
 * Work has focused on getting messages from a CGI script at a URL, but the RabbitMQ may yet come back...
 * @author sshorser
 *
 */
public class RabbitMQListener implements QueueListener {

    private String hostName;
    private String queueName;
    private boolean autoAck;
    private boolean durableQueue;
    private String pathToINIs = "/tmp/inis4seqware/";

    /**
     * Get the host that is hosting the queue.
     */
    @Override
    public String getSourceHost() {
        return this.hostName;
    }

    /**
     * Get the name of the queue to connect to.
     */
    @Override
    public String getQueueName() {
        return this.queueName;
    }

    /**
     * Set the name of the queue host.
     */
    @Override
    public void setSourceHost(String hostName) {
        this.hostName = hostName;
    }

    /**
     * Set the name of the queue to check for messages.
     */
    @Override
    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    /**
     * Get a message from the queue. <b>NOT YET IMPLEMENTED.</b>
     */
    @Override
    public void getMessageFromQueue()  {
    }

    /**
     * Returns whether or not the queue connection is set to auto-acknowledge.
     * @return
     */
    public boolean isAutoAck() {
        return autoAck;
    }

    /**
     * Sets this queue listener to auto-acknowledge (or not.
     * @param autoAck
     */
    public void setAutoAck(boolean autoAck) {
        this.autoAck = autoAck;
    }

    /**
     * Returns a value if the queue that is being listened to is durable.
     * @return
     */
    public boolean isDurableQueue() {
        return durableQueue;
    }

    /**
     * Sets if the queue should be durable.
     * @param durableQueue
     */
    public void setDurableQueue(boolean durableQueue) {
        this.durableQueue = durableQueue;
    }

    /**
     * Gets the path that will be used when writing INI files for seqware.
     * @return
     */
    public String getPathToINIs() {
        //TODO: There is newer code that focuses specifically on writing INI files.
        // This class should focus on just getting messages and letting the SeqwareJobMessageProcessor focus
        // on writing INI files.
        return pathToINIs;
    }

    /**
     * Sets the path that will be used when writing INI files for seqware.
     * @param pathToINIs
     */
    public void setPathToINIs(String pathToINIs) {
        //TODO: There is newer code that focuses specifically on writing INI files.
        // This class should focus on just getting messages and letting the SeqwareJobMessageProcessor focus
        // on writing INI files.
        this.pathToINIs = pathToINIs;
    }

    /**
     * Gets a message from the message queue.
     * <br/>
     * NOTE: Implementation is incomplete.
     * @return The message body, as a string.
     */
    @Override
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



}
