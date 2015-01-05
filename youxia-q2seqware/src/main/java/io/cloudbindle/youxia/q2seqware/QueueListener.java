package io.cloudbindle.youxia.q2seqware;

public interface QueueListener extends SeqwareJobMessageSource {
    String getQueueName();

    void setQueueName(String queueName);

    void getMessageFromQueue();
}
