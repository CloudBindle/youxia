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
package io.cloudbindle.youxia.generator;

import com.google.common.collect.Maps;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import static io.cloudbindle.youxia.generator.Generator.GENERATOR_MAX_SCHEDULED_WORKFLOWS;
import static io.cloudbindle.youxia.generator.Generator.GENERATOR_MAX_WORKFLOWS;
import static io.cloudbindle.youxia.generator.Generator.GENERATOR_WORKFLOW_ACCESSION;
import static io.cloudbindle.youxia.generator.Generator.GENERATOR_WORKFLOW_NAME;
import static io.cloudbindle.youxia.generator.Generator.GENERATOR_WORKFLOW_VERSION;
import io.cloudbindle.youxia.listing.AbstractInstanceListing.InstanceDescriptor;
import io.cloudbindle.youxia.listing.AwsListing;
import io.cloudbindle.youxia.listing.ListingFactory;
import io.cloudbindle.youxia.listing.OpenStackJCloudsListing;
import io.cloudbindle.youxia.pawg.api.ClusterDetails;
import io.cloudbindle.youxia.util.ConfigTools;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import static org.easymock.EasyMock.expect;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.powermock.api.easymock.PowerMock.createMockAndExpectNew;
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
@PrepareForTest({ ConfigTools.class, Generator.class, ListingFactory.class })
public class GeneratorTest {
    private File manualFile;

    public GeneratorTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() throws Exception {
        mockStatic(ConfigTools.class);
        String testString = IOUtils.toString(Generator.class.getResourceAsStream("double_cluster.json"));
        Path tempFile = Files.createTempFile("inventory", ".json");
        FileUtils.write(tempFile.toFile(), testString);
        this.manualFile = tempFile.toFile();
    }

    @After
    public void tearDown() {
        this.manualFile.deleteOnExit();
    }

    /**
     * Test of main method, of class Reaper.
     *
     * @throws java.lang.Exception
     */
    @Test(expected = RuntimeException.class)
    public void testHelp() throws Exception {
        System.out.println("help");
        String[] args = { "--help" };
        Generator.main(args);
    }

    @Test
    public void testAggregateAllOnlyJsonContents() throws IOException, Exception {
        mockOutConfig();

        AwsListing listing1 = createMockAndExpectNew(AwsListing.class);
        OpenStackJCloudsListing listing2 = createMockAndExpectNew(OpenStackJCloudsListing.class);

        Map<String, InstanceDescriptor> result1 = Maps.newHashMap();
        expect(listing1.getInstances()).andReturn(result1);
        expect(listing2.getInstances()).andReturn(result1);

        Path tempFile = Files.createTempFile("output", ".json");
        String[] args = { "--aws", "--openstack", "--json", this.manualFile.getAbsolutePath(), "--output", tempFile.toString() };

        replayAll();

        Generator.main(args);

        Gson gson = new GsonBuilder().create();
        String readFileToString = FileUtils.readFileToString(tempFile.toFile(), StandardCharsets.UTF_8);
        Type mapType = new TypeToken<Map<String, ClusterDetails>>() {
        }.getType();
        Map<String, ClusterDetails> map = gson.fromJson(readFileToString, mapType);
        Assert.assertTrue(readFileToString, map.entrySet().size() == 2);

        verifyAll();
    }

    @Test
    public void testAggregateAll() throws IOException, Exception {
        mockOutConfig();

        AwsListing listing1 = createMockAndExpectNew(AwsListing.class);
        OpenStackJCloudsListing listing2 = createMockAndExpectNew(OpenStackJCloudsListing.class);

        Map<String, InstanceDescriptor> result1 = Maps.newHashMap();
        result1.put("Wong_Fei-hong", new InstanceDescriptor("name", "123.123.123.123"));
        result1.put("Ip_Man", new InstanceDescriptor("name", "124.124.124.124"));
        Map<String, InstanceDescriptor> result2 = Maps.newHashMap();
        result2.put("Ouyang_Feng", new InstanceDescriptor("name", "125.125.125.125"));
        result2.put("Murong_Yang", new InstanceDescriptor("name", "126.126.126.126"));
        expect(listing1.getInstances()).andReturn(result1);
        expect(listing2.getInstances()).andReturn(result2);

        Path tempFile = Files.createTempFile("output", ".json");
        String[] args = { "--aws", "--openstack", "--json", this.manualFile.getAbsolutePath(), "--output", tempFile.toString() };

        replayAll();

        Generator.main(args);

        Gson gson = new GsonBuilder().create();
        String readFileToString = FileUtils.readFileToString(tempFile.toFile(), StandardCharsets.UTF_8);
        Type mapType = new TypeToken<Map<String, ClusterDetails>>() {
        }.getType();
        Map<String, ClusterDetails> map = gson.fromJson(readFileToString, mapType);
        Assert.assertTrue(readFileToString, map.entrySet().size() == 6);

        verifyAll();
    }

    private void mockOutConfig() {
        HierarchicalINIConfiguration mockConfig = mock(HierarchicalINIConfiguration.class);
        expect(ConfigTools.getYouxiaConfig()).andReturn(mockConfig).anyTimes();
        when(mockConfig.getString(GENERATOR_MAX_SCHEDULED_WORKFLOWS)).thenReturn("1 billion");
        when(mockConfig.getString(GENERATOR_MAX_WORKFLOWS)).thenReturn("1 billion");
        when(mockConfig.getString(ConfigTools.SEQWARE_REST_PASS)).thenReturn("password");
        when(mockConfig.getString(ConfigTools.SEQWARE_REST_USER)).thenReturn("username");
        when(mockConfig.getString(ConfigTools.SEQWARE_REST_PORT)).thenReturn("1234");
        when(mockConfig.getString(ConfigTools.SEQWARE_REST_ROOT)).thenReturn("123.123.123.123");
        when(mockConfig.getString(GENERATOR_WORKFLOW_ACCESSION)).thenReturn("1");
        when(mockConfig.getString(GENERATOR_WORKFLOW_NAME)).thenReturn("workflow name");
        when(mockConfig.getString(GENERATOR_WORKFLOW_VERSION)).thenReturn("1");
    }
}
