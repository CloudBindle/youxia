package io.cloudbindle.youxia.q2seqware;

public interface SeqwareJobMessageSource {
    String getMessage();

    String getSourceHost();

    void setSourceHost(String s);
}
