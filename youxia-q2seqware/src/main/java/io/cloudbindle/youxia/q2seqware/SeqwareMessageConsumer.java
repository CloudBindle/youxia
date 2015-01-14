package io.cloudbindle.youxia.q2seqware;

import io.cloudbindle.youxia.util.Log;

import java.io.IOException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

/**
 * A consumer that extends the RabbitMQ DefaultConsumer. When it receives a message,
 * it will process the message as a seqware job message.
 * <br/>
 * NOTE: Implementation is not yet complete.
 * @author sshorser
 *
 */
public class SeqwareMessageConsumer extends DefaultConsumer {

    private static final String CHARSET_ENCODING = "UTF-8";
    boolean autoAck = true;
    private String pathToINIs = "/tmp/inis4seqware/";

    public SeqwareMessageConsumer(Channel channel) {
        super(channel);
    }

    public boolean isAutoAck() {
        return autoAck;
    }

    public void setAutoAck(boolean autoAck) {
        this.autoAck = autoAck;
    }

    public String getPathToINIs() {
        return pathToINIs;
    }

    public void setPathToINIs(String pathToINIs) {
        this.pathToINIs = pathToINIs;
    }

    @Override
    public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
        String message = new String(body,CHARSET_ENCODING);
        
        Log.info("message received: " + message);

        if (message.equals("EXIT")) {
            Log.info("EXIT message recieved. Shutting down...");
            this.getChannel().basicCancel(consumerTag);
        } else {
            processMessage(message);
        }

        if (!this.isAutoAck()) {
            this.getChannel().basicAck(envelope.getDeliveryTag(), false);
        }
    }

    /**
     * Process a message from the queue using a SeqwareJobMessageProcessor.
     * @param message
     */
    private void processMessage(String message) {
        SeqwareJobMessageProcessor messageProcessor = new SeqwareJobMessageProcessor();
        messageProcessor.setPathToINIs(this.getPathToINIs());
        messageProcessor.processMessage(message);
    }
}
