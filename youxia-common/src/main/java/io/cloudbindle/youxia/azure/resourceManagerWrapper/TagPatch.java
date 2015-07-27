/*
 * Copyright (C) 2015 CloudBindle
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
package io.cloudbindle.youxia.azure.resourceManagerWrapper;

import java.util.HashMap;
import java.util.Map;

/**
 * This is the type that needs to be sent for updates.
 *
 * @author dyuen
 */
public class TagPatch {
    private Map<String, String> tags = new HashMap<>();

    public TagPatch() {

    }

    public TagPatch(Map<String, String> newTags) {
        this.tags = newTags;
    }

    /**
     * @return the tags
     */
    public Map<String, String> getTags() {
        return tags;
    }

    /**
     * @param tags
     *            the tags to set
     */
    public void setTags(Map<String, String> tags) {
        this.tags = tags;
    }
}
