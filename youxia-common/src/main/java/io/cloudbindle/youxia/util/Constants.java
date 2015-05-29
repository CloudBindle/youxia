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

/**
 *
 * @author dyuen
 */
public class Constants {
    public static final String STATE_TAG = "youxia.managed_state";
    public static final String SENSU_NAME = "youxia.sensu_name";
    public static final String SLACK_URL = "youxia.slack_webhook";
    /**
     * Combined with managed tag to define domain names for simpledb
     */
    public static final String WORKFLOW_RUNS = ".workflow_runs";
    public static final String CLIENTS = ".clients";

    public static final String INI_FILE = "ini_file";

    public enum STATE {
        SETTING_UP, READY, MARKED_FOR_DEATH
    }

}
