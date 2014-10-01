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
package io.cloudbindle.youxia.deployer;

import com.amazonaws.services.ec2.AmazonEC2Client;
import com.google.common.collect.Maps;
import io.cloudbindle.youxia.listing.AwsListing;
import io.cloudbindle.youxia.util.ConfigTools;
import java.util.Map;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import static org.easymock.EasyMock.expect;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.powermock.api.easymock.PowerMock.expectNew;
import static org.powermock.api.easymock.PowerMock.createMockAndExpectNew;
import static org.powermock.api.easymock.PowerMock.mockStatic;
import static org.powermock.api.easymock.PowerMock.replay;
import static org.powermock.api.mockito.PowerMockito.mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

/**
 * 
 * @author dyuen
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ ConfigTools.class, Deployer.class })
public class DeployerTest {

    public DeployerTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of main method, of class Deployer.
     * 
     * @throws java.lang.Exception
     */
    @Test(expected = RuntimeException.class)
    public void testHelp() throws Exception {
        System.out.println("help");
        String[] args = { "--help" };
        Deployer.main(args);
    }

    @Test
    public void testListingNoProvision() throws Exception {
        mockStatic(ConfigTools.class);
        AmazonEC2Client mockClient = mock(AmazonEC2Client.class);
        AwsListing listing = createMockAndExpectNew(AwsListing.class);
        HierarchicalINIConfiguration mockConfig = mock(HierarchicalINIConfiguration.class);
        expect(ConfigTools.getYouxiaConfig()).andReturn(mockConfig);
        expect(ConfigTools.getEC2Client()).andReturn(mockClient);
        expectNew(AwsListing.class).andReturn(listing);
        Map<String, String> map = Maps.newTreeMap();
        map.put("key1", "value1");
        map.put("key2", "value2");
        expect(listing.getInstances()).andReturn(map);
        String[] args = { "--total-nodes-num", "2", "--max-spot-price", "2", "--batch-size", "5", "--ansible-playbook", "test-book.yml" };

        replay(HierarchicalINIConfiguration.class);
        replay(ConfigTools.class);
        replay(listing, AwsListing.class);

        Deployer.main(args);
    }
}
