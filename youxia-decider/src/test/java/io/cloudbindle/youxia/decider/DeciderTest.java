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
package io.cloudbindle.youxia.decider;

import io.cloudbindle.youxia.util.ConfigTools;
import net.sourceforge.seqware.common.metadata.MetadataFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

/**
 *
 * @author dyuen
 */
@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.net.ssl.*")
@PrepareForTest({ ConfigTools.class, Decider.class, MetadataFactory.class })
public class DeciderTest {

    /**
     * Test of main method, of class Decider.
     *
     * @throws java.lang.Exception
     */
    @Test(expected = RuntimeException.class)
    public void testHelp() throws Exception {
        System.out.println("help");
        String[] args = { "--help" };
        Decider.main(args);
    }
}
