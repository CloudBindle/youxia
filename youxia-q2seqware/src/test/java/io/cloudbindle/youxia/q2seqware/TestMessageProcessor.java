package io.cloudbindle.youxia.q2seqware;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class TestMessageProcessor {

    private String readFileToString(String fileName) throws IOException {
        byte[] encoded = Files.readAllBytes(Paths.get(fileName));
        return new String(encoded);
    }

    @Test
    public void testProcessMessage() throws IOException {
        SeqwareJobMessageProcessor processor = new SeqwareJobMessageProcessor();
        processor.setPathToINIs("/tmp/inis4seqware/test/");
        String message = readFileToString("src/test/resources/sample_message.json");

        String pathToINI = processor.processMessage(message);

        System.out.println(pathToINI);
        assertTrue(pathToINI.startsWith(processor.getPathToINIs() + "workflow_"));
        assertTrue(pathToINI.endsWith(".ini"));

        String iniFile = readFileToString(pathToINI);
        assertNotNull(iniFile);

        assertTrue(iniFile.contains("input_file = somefile.txt"));
        assertTrue(iniFile.contains("output_prefix = _out_"));
    }

}
