/*
 * Copyright (C) 2014 CloudBindle
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.cloudbindle.youxia.util;

import org.apache.commons.configuration.HierarchicalINIConfiguration;
import static org.easymock.EasyMock.expect;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.powermock.api.easymock.PowerMock.mockStatic;
import static org.powermock.api.easymock.PowerMock.replayAll;
import static org.powermock.api.easymock.PowerMock.verifyAll;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

/**
 *
 * @author dyuen
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ ConfigTools.class, HierarchicalINIConfiguration.class })
public class LogTest {

    public LogTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
        mockStatic(ConfigTools.class);
        HierarchicalINIConfiguration mockConfig = mock(HierarchicalINIConfiguration.class);
        expect(ConfigTools.getYouxiaConfig()).andReturn(mockConfig);
        when(mockConfig.containsKey(Constants.SLACK_URL)).thenReturn(false);
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of trace method, of class Log.
     */
    @Test
    public void testTrace_String() {
        System.out.println("trace");
        String message = "not empty";
        Log.trace(message);
    }

    /**
     * Test of trace method, of class Log.
     */
    @Test
    public void testTrace_String_Throwable() {
        System.out.println("trace");
        String message = "not empty";
        Throwable t = null;
        Log.trace(message, t);
    }

    /**
     * Test of debug method, of class Log.
     */
    @Test
    public void testDebug_String() {
        System.out.println("debug");
        String message = "not empty";
        Log.debug(message);
    }

    /**
     * Test of debug method, of class Log.
     */
    @Test
    public void testDebug_String_Throwable() {
        System.out.println("debug");
        String message = "not empty";
        Throwable t = null;
        Log.debug(message, t);
    }

    /**
     * Test of info method, of class Log.
     */
    @Test
    public void testInfo_String() {
        System.out.println("info");
        String message = "not empty";
        Log.info(message);
    }

    /**
     * Test of info method, of class Log.
     */
    @Test
    public void testInfo_String_Throwable() {
        System.out.println("info");
        String message = "not empty";
        Throwable t = null;
        Log.info(message, t);
    }

    /**
     * Test of warn method, of class Log.
     */
    @Test
    public void testWarn_String() {
        System.out.println("warn");
        String message = "not empty";
        Log.warn(message);
    }

    /**
     * Test of warn method, of class Log.
     */
    @Test
    public void testWarn_String_Throwable() {
        System.out.println("warn");
        String message = "not empty";
        Throwable t = null;
        Log.warn(message, t);
    }

    /**
     * Test of error method, of class Log.
     */
    @Test
    public void testError_String() {
        System.out.println("error");
        String message = "not empty";
        Log.error(message);
    }

    /**
     * Test of error method, of class Log.
     */
    @Test
    public void testError_String_Throwable() {
        System.out.println("error");
        String message = "not empty";
        Throwable t = null;
        Log.error(message, t);
    }

    /**
     * Test of stdout method, of class Log.
     */
    @Test
    public void testStdout() {
        replayAll();
        System.out.println("stdout");
        String message = "not empty";
        Log.stdout(message);
        verifyAll();
    }

    /**
     * Test of stdoutWithTime method, of class Log.
     */
    @Test
    public void testStdoutWithTime() {
        replayAll();
        System.out.println("stdoutWithTime");
        String message = "not empty";
        Log.stdoutWithTime(message);
        verifyAll();
    }

    /**
     * Test of stderrWithTime method, of class Log.
     */
    @Test
    public void testStderrWithTime() {
        replayAll();
        System.out.println("stderrWithTime");
        String message = "not empty";
        Log.stderrWithTime(message);
        verifyAll();
    }

    /**
     * Test of stderr method, of class Log.
     */
    @Test
    public void testStderr() {
        replayAll();
        System.out.println("stderr");
        String message = "not empty";
        Log.stderr(message);
        verifyAll();
    }

}
