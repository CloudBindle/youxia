package io.cloudbindle.youxia.q2seqware;

/**
 * An interface for external sources that could send messages about jobs that should be scheduled to seqware.
 * @author sshorser
 *
 */
public interface SeqwareJobMessageSource {
    /**
     * Get a message from the external source.
     * @return
     */
    String getMessage();

    /**
     * Get the host URI.
     * @return
     */
    String getSourceHost();

    /**
     * Set the host URI.
     * @param s
     */
    void setSourceHost(String s);
}
