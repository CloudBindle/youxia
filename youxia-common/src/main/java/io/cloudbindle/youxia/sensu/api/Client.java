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

        private String ansible_system_vendor;
        private String ansible_product_name;

        /**
         * @return the ansible_system_vendor
         */
        public String getAnsible_system_vendor() {
            return ansible_system_vendor;
        }

        /**
         * @param ansible_system_vendor
         *            the ansible_system_vendor to set
         */
        public void setAnsible_system_vendor(String ansible_system_vendor) {
            this.ansible_system_vendor = ansible_system_vendor;
        }

        /**
         * @return the ansible_product_name
         */
        public String getAnsible_product_name() {
            return ansible_product_name;
        }

        /**
         * @param ansible_product_name
         *            the ansible_product_name to set
         */
        public void setAnsible_product_name(String ansible_product_name) {
            this.ansible_product_name = ansible_product_name;
        }

    }

}
