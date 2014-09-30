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

import com.google.common.reflect.TypeToken;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.FileUtils;

/**
 * 
 * @author dyuen
 */
public class ClusterDetails {

    private String workflowAccession;
    private String workflowName;
    private String workflowVersion;
    private String username;
    private String password;
    private String webservice;
    private String host;
    private String maxWorkflows;
    private String maxScheduledWorkflows;

    /**
     * @return the workflowAccession
     */
    public String getWorkflowAccession() {
        return workflowAccession;
    }

    /**
     * @param workflowAccession
     *            the workflowAccession to set
     */
    public void setWorkflowAccession(String workflowAccession) {
        this.workflowAccession = workflowAccession;
    }

    /**
     * @return the workflowName
     */
    public String getWorkflowName() {
        return workflowName;
    }

    /**
     * @param workflowName
     *            the workflowName to set
     */
    public void setWorkflowName(String workflowName) {
        this.workflowName = workflowName;
    }

    /**
     * @return the workflowVersion
     */
    public String getWorkflowVersion() {
        return workflowVersion;
    }

    /**
     * @param workflowVersion
     *            the workflowVersion to set
     */
    public void setWorkflowVersion(String workflowVersion) {
        this.workflowVersion = workflowVersion;
    }

    /**
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * @param username
     *            the username to set
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * @return the password
     */
    public String getPassword() {
        return password;
    }

    /**
     * @param password
     *            the password to set
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * @return the webservice
     */
    public String getWebservice() {
        return webservice;
    }

    /**
     * @param webservice
     *            the webservice to set
     */
    public void setWebservice(String webservice) {
        this.webservice = webservice;
    }

    /**
     * @return the host
     */
    public String getHost() {
        return host;
    }

    /**
     * @param host
     *            the host to set
     */
    public void setHost(String host) {
        this.host = host;
    }

    /**
     * @return the maxWorkflows
     */
    public String getMaxWorkflows() {
        return maxWorkflows;
    }

    /**
     * @param maxWorkflows
     *            the maxWorkflows to set
     */
    public void setMaxWorkflows(String maxWorkflows) {
        this.maxWorkflows = maxWorkflows;
    }

    /**
     * @return the maxScheduledWorkflows
     */
    public String getMaxScheduledWorkflows() {
        return maxScheduledWorkflows;
    }

    /**
     * @param maxScheduledWorkflows
     *            the maxScheduledWorkflows to set
     */
    public void setMaxScheduledWorkflows(String maxScheduledWorkflows) {
        this.maxScheduledWorkflows = maxScheduledWorkflows;
    }

    public static void main(String[] args) throws IOException {
        Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).setPrettyPrinting().create();
        String readFileToString = FileUtils.readFileToString(new File(args[0]));
        Type mapType = new TypeToken<Map<String, ClusterDetails>>() {
        }.getType();
        Map<String, List<Map<String, String>>> map = gson.fromJson(readFileToString, mapType);
        System.out.println(map);
    }
}
