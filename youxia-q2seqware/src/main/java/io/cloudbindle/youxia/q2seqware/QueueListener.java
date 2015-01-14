package io.cloudbindle.youxia.q2seqware;

/**
 * An interface for objects that will get messages for Seqware from a message queue
 * @author sshorser
 *
 */
public interface QueueListener extends SeqwareJobMessageSource {
    /**
     * Get the queue name.
     * @return
     */
    String getQueueName();

    /**
     * Set the queue name.
     * @param queueName
     */
    void setQueueName(String queueName);

    /**
     * Get a message from a queue.
     */
    void getMessageFromQueue();
}
