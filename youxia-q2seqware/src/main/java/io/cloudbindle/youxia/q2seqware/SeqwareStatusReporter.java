package io.cloudbindle.youxia.q2seqware;

/**
 * Interface for objects that will report seqware status to some interested party.
 * 
 * @author sshorser
 *
 */
public interface SeqwareStatusReporter {

    /**
     * Report seqware status.
     * 
     * @param status
     *            - the status to report.
     */
    void reportSeqwareStatus(String status);
}
