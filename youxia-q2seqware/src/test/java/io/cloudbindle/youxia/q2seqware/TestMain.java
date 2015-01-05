package io.cloudbindle.youxia.q2seqware;

import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;
import org.powermock.core.classloader.annotations.PrepareForTest;

@RunWith(PowerMockRunner.class)
@PrepareForTest(io.cloudbindle.youxia.q2seqware.ExternalSeqwareScheduler.class)
public class TestMain {

    private static final String WORKFLOW_VERSION = "1.0";
    private static final String WORKFLOW_NAME = "TestWorkflow";

    @Test
    public void testCLIArgs() throws Exception
    {
        String[] args = {"-workflow",WORKFLOW_NAME,"-version",WORKFLOW_VERSION};
        ExternalSeqwareScheduler mainObject = new ExternalSeqwareScheduler();
        Whitebox.invokeMethod(ExternalSeqwareScheduler.class,"getCLIArgs",((Object)args));
        
        Set<Field> fields = Whitebox.getAllStaticFields(ExternalSeqwareScheduler.class);
        for (Field f : fields)
        {
            if (f.getName().equals("workflowName"))
            {
                System.out.println((String)f.get(mainObject));
                assertEquals(WORKFLOW_NAME,(String)f.get(mainObject));
            }
            if (f.getName().equals("workflowVersion"))
            {
                System.out.println((String)f.get(mainObject));
                assertEquals(WORKFLOW_VERSION,(String)f.get(mainObject));
            }
        }
        
        //assertEquals("Test",Whitebox.<String>getInternalState(mainObject, "workflowName"));
    }
}
