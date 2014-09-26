package io.cloudbindle.youxia.sensu.api;

/**
 * 
 * @author dyuen
 */
public class Client {
    private String name;
    private String address;
    private String[] subscriptions;
    private Environment environment;

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

}
