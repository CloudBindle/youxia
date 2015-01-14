package io.cloudbindle.youxia.q2seqware;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import io.cloudbindle.youxia.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@PrepareForTest({ io.cloudbindle.youxia.q2seqware.SeqwareStatusMonitor.class, io.cloudbindle.youxia.util.Log.class })
@RunWith(PowerMockRunner.class)
public class TestSeqwareMonitor {
    @Mock
    DefaultExecutor mockExecutor;

    private final static String CHARSET_ENCODING = "UTF-8";
    
    private String readFileToString(String fileName) throws IOException {
        byte[] encoded = Files.readAllBytes(Paths.get(fileName));
        return new String(encoded);
    }

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testNoJobsRunning() throws Exception {
        String seqwareReportOutput = "";
        SeqwareStatusMonitor monitor = new SeqwareStatusMonitor();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        outputStream.write(seqwareReportOutput.getBytes(CHARSET_ENCODING));

        String command = "seqware workflow report --status running";
        CommandLine cli = new CommandLine(command);
        PowerMockito.whenNew(CommandLine.class).withArguments(command).thenReturn(cli);

        PowerMockito.doReturn(1).when(mockExecutor).execute(cli);

        PowerMockito.whenNew(ByteArrayOutputStream.class).withNoArguments().thenReturn(outputStream);
        PowerMockito.whenNew(DefaultExecutor.class).withNoArguments().thenReturn(mockExecutor);

        boolean anyJobsRunning = monitor.anyRunningJobs();
        assertFalse(anyJobsRunning);
    }

    @Test
    public void testJobsRunning() throws Exception {
        String seqwareReportOutput = "<Some seqware output>";
        SeqwareStatusMonitor monitor = new SeqwareStatusMonitor();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        outputStream.write(seqwareReportOutput.getBytes(CHARSET_ENCODING));

        String command = "seqware workflow report --status running";
        CommandLine cli = new CommandLine(command);
        PowerMockito.whenNew(CommandLine.class).withArguments(command).thenReturn(cli);

        PowerMockito.doReturn(1).when(mockExecutor).execute(cli);

        PowerMockito.whenNew(ByteArrayOutputStream.class).withNoArguments().thenReturn(outputStream);
        PowerMockito.whenNew(DefaultExecutor.class).withNoArguments().thenReturn(mockExecutor);

        boolean anyJobsRunning = monitor.anyRunningJobs();
        assertTrue(anyJobsRunning);
    }

    @Test
    public void testGetJobStatusWithErr() throws Exception {
        PowerMockito.mockStatic(Log.class);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        PowerMockito.doNothing().when(Log.class,"error",captor.capture());
        String seqwareReportOutput = "<Some seqware output>";
        SeqwareStatusMonitor monitor = new SeqwareStatusMonitor();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        outputStream.write(seqwareReportOutput.getBytes(CHARSET_ENCODING));

        String command = "seqware workflow report --status running";
        CommandLine cli = new CommandLine(command);
        PowerMockito.whenNew(CommandLine.class).withArguments(command).thenReturn(cli);
        String testIOExceptionMessage = "Test IOException";
       
        PowerMockito.doThrow(new IOException(testIOExceptionMessage)).when(mockExecutor).execute(cli);
        monitor.anyRunningJobs();
        
        assertEquals("Could not call SeqWare. Perhaps it is not installed correctly on this system?\n"+
                    "Full Message:\n"+
                    "Cannot run program \"seqware workflow report --status running\" (in directory \".\"): error=2, No such file or directory",captor.getValue());
    }
    
    @Test
    public void testCheckSeqwareStatus() throws Exception {
        String runningOutput = readFileToString("src/test/resources/seqware_status_running.txt");

        SeqwareStatusMonitor monitor = new SeqwareStatusMonitor();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        outputStream.write(runningOutput.getBytes(CHARSET_ENCODING));

        String command = "seqware workflow report --accession 1";
        CommandLine cli = new CommandLine(command);
        PowerMockito.whenNew(CommandLine.class).withArguments(command).thenReturn(cli);

        PowerMockito.doReturn(1).when(mockExecutor).execute(cli);

        PowerMockito.whenNew(ByteArrayOutputStream.class).withNoArguments().thenReturn(outputStream);
        PowerMockito.whenNew(DefaultExecutor.class).withNoArguments().thenReturn(mockExecutor);

        String output = monitor.checkSeqwareStatus("1");

        System.out.println(output);
        assertNotNull(output);

        assertEquals("running", output);
    }

    @Test
    public void testGetAccessionID() throws Exception {
        String runningOutput = readFileToString("src/test/resources/workflow_list.txt");

        SeqwareStatusMonitor monitor = new SeqwareStatusMonitor();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        outputStream.write(runningOutput.getBytes(CHARSET_ENCODING));

        String command = "seqware workflow list";
        CommandLine cli = new CommandLine(command);
        PowerMockito.whenNew(CommandLine.class).withArguments(command).thenReturn(cli);

        PowerMockito.doReturn(1).when(mockExecutor).execute(cli);

        PowerMockito.whenNew(ByteArrayOutputStream.class).withNoArguments().thenReturn(outputStream);
        PowerMockito.whenNew(DefaultExecutor.class).withNoArguments().thenReturn(mockExecutor);

        String output = monitor.getAccessionID("HelloWorld", "1.0-SNAPSHOT");

        System.out.println(output);
        assertNotNull(output);

        assertEquals("1", output);

    }

}
