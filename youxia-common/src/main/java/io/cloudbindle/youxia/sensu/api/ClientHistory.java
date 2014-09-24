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

package io.cloudbindle.youxia.sensu.api;

/**
 * 
 * @author dyuen
 */
public class ClientHistory {
    private String check;
    private int[] history;
    private Long last_execution;
    private int last_status;

    /**
     * @return the check
     */
    public String getCheck() {
        return check;
    }

    /**
     * @param check the check to set
     */
    public void setCheck(String check) {
        this.check = check;
    }

    /**
     * @return the history
     */
    public int[] getHistory() {
        return history;
    }

    /**
     * @param history the history to set
     */
    public void setHistory(int[] history) {
        this.history = history;
    }

    /**
     * @return the last_execution
     */
    public Long getLast_execution() {
        return last_execution;
    }

    /**
     * @param last_execution the last_execution to set
     */
    public void setLast_execution(Long last_execution) {
        this.last_execution = last_execution;
    }

    /**
     * @return the last_status
     */
    public int getLast_status() {
        return last_status;
    }

    /**
     * @param last_status the last_status to set
     */
    public void setLast_status(int last_status) {
        this.last_status = last_status;
    }

}
