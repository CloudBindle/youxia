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
package io.cloudbindle.youxia.pawg.api;

import com.google.common.collect.Maps;
import com.google.common.reflect.TypeToken;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.lang.reflect.Type;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;

/**
 * 
 * @author dyuen
 */
public class ClusterDetailsTest {

    @Test
    public void serializesToJSON() throws Exception {
        Map<String, ClusterDetails> map = getInventory();

        Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).setPrettyPrinting().create();
        String toJson = gson.toJson(map);
        String testString = IOUtils.toString(ClusterDetails.class.getResourceAsStream("single_cluster.json"));

        JsonParser parser = new JsonParser();
        JsonElement parse1 = parser.parse(toJson);
        JsonElement parse2 = parser.parse(testString);
        Assert.assertEquals(parse2, parse1);
    }

    @Test
    public void deserializeFromJSON() throws Exception {
        Map<String, ClusterDetails> generatedMap = getInventory();

        Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).setPrettyPrinting().create();
        String testString = IOUtils.toString(ClusterDetails.class.getResourceAsStream("single_cluster.json"));

        Type mapType = new TypeToken<Map<String, ClusterDetails>>() {
        }.getType();
        Map<String, ClusterDetails> map = gson.fromJson(testString, mapType);

        Assert.assertEquals(generatedMap, map);
    }

    private Map<String, ClusterDetails> getInventory() {
        final ClusterDetails details = new ClusterDetails();
        details.setWorkflowAccession("2");
        details.setWorkflowName("Workflow_Bundle_BWA");
        details.setWorkflowVersion("2.6.0");
        details.setUsername("admin@admin.com");
        details.setPassword("admin");
        details.setWebservice("http://master:8080/SeqWareWebService");
        details.setHost("master");
        details.setMaxWorkflows("1");
        details.setMaxScheduledWorkflows("1");
        Map<String, ClusterDetails> map = Maps.newHashMap();
        map.put("cluster-name-1", details);
        return map;
    }
}