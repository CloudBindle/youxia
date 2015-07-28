This document details how to setup youxia in Azure which, unfortunately, requires multiple types of authentication to access various services. All instructions here refer to the Azure preview portal unless otherwise specified. 

### SSH Access

Currently, your SSH key will be burned into your Azure image. Use the parameter ```azure_ssh_key``` to specify the path to your private key that will be used to SSH into workers and configure them. 

## Authentication

#### Compute

The first step is to get authenticated for compute so that you can provision and teardown VMs. Here you will require three pieces of information. 

1. ```azure_subscription_id```
2. ```azure_keystore_location```
3. ```azure_keystore_password```

For the first, browse to "Browse All" -> Subscriptions and then look under the header of subscription id. 
For the latter two, you will need to create a "management certificate" for Azure in order to use the Java API. Please follow the instructions under the heading of [Create a Management Certificate for Azure](https://azure.microsoft.com/en-us/documentation/articles/java-create-azure-website-using-java-sdk/) until you finish by converting your PFX file into JKS. When you finish, add the path to your JKS file to ```azure_keystore_location``` and the password for the keystore to ```azure_keystore_password```. 

### Storage

The second step is to get authenticated for storage so that your VMs can actually store data. Here you will require two pieces of information. 

1. ```azure_storage_account_name```
2. ```azure_storage_account_key```

For these two, refer to the heading of [View, copy, and regenerate storage access keys](https://azure.microsoft.com/en-us/documentation/articles/storage-create-storage-account/#view-copy-and-regenerate-storage-access-keys). Note that you're looking only for the storage account name and the primary access key. You won't need the secondary access key. 




