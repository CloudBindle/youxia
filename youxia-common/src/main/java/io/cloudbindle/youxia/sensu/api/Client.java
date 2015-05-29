package io.cloudbindle.youxia.sensu.api;

import java.util.Arrays;
import java.util.Objects;

/**
 * 
 * @author dyuen
 */
public class Client {
    private String name;
    private String address;
    private String[] subscriptions;
    private Environment environment;
    private long timestamp;

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name
     *            the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the address
     */
    public String getAddress() {
        return address;
    }

    /**
     * @param address
     *            the address to set
     */
    public void setAddress(String address) {
        this.address = address;
    }

    /**
     * @return the subscriptions
     */
    public String[] getSubscriptions() {
        return subscriptions;
    }

    /**
     * @param subscriptions
     *            the subscriptions to set
     */
    public void setSubscriptions(String[] subscriptions) {
        this.subscriptions = subscriptions;
    }

    /**
     * @return the environment
     */
    public Environment getEnvironment() {
        return environment;
    }

    /**
     * @param environment
     *            the environment to set
     */
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    /**
     * @return the timestamp
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * @param timestamp
     *            the timestamp to set
     */
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public class Environment {

        private String ansibleSystemVendor;
        private String ansibleProductName;

        /**
         * @return the ansibleSystemVendor
         */
        public String getAnsibleSystemVendor() {
            return ansibleSystemVendor;
        }

        /**
         * @param ansibleSystemVendor
         *            the ansibleSystemVendor to set
         */
        public void setAnsibleSystemVendor(String ansibleSystemVendor) {
            this.ansibleSystemVendor = ansibleSystemVendor;
        }

        /**
         * @return the ansibleProductName
         */
        public String getAnsibleProductName() {
            return ansibleProductName;
        }

        /**
         * @param ansibleProductName
         *            the ansibleProductName to set
         */
        public void setAnsibleProductName(String ansibleProductName) {
            this.ansibleProductName = ansibleProductName;
        }

    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Client other = (Client) obj;
        if (!Objects.equals(this.name, other.name)) {
            return false;
        }
        if (!Objects.equals(this.address, other.address)) {
            return false;
        }
        if (!Arrays.deepEquals(this.subscriptions, other.subscriptions)) {
            return false;
        }
        if (!Objects.equals(this.environment, other.environment)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 41 * hash + Objects.hashCode(this.name);
        hash = 41 * hash + Objects.hashCode(this.address);
        hash = 41 * hash + Arrays.deepHashCode(this.subscriptions);
        hash = 41 * hash + Objects.hashCode(this.environment);
        return hash;
    }

}
