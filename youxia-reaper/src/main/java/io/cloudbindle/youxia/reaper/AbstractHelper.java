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
package io.cloudbindle.youxia.reaper;

import io.cloudbindle.youxia.listing.AbstractInstanceListing;
import java.util.Map;
import java.util.Set;

/**
 * This helper interface defines the operations that need to be implemented for each cloud that the Reaper needs to interact with.
 *
 * @author dyuen
 */
public interface AbstractHelper {

    /**
     * Look for instances that can be killed due to being orphaned (state tag indicates that we did not finish deployment)
     *
     * @param instance
     *            the instance that we are examining, name -> address
     * @return a non-null sensu name if we wish to delete the instance
     */
    String identifyOrphanedInstance(Map.Entry<String, String> instance);

    /**
     * Get a listing of instances that we can act upon
     *
     * @return
     */
    AbstractInstanceListing getListing();

    /**
     * Alter state tag to "marked for death" and issue a request to the cloud API for termination
     *
     * @param instancesToKill
     */
    void terminateInstances(Set<String> instancesToKill);
}
