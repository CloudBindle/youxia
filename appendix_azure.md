This document details how to setup youxia in Azure which, unfortunately, requires multiple types of authentication to access various services. All instructions here refer to the Azure preview portal unless otherwise specified. 

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

### Resource Management

The third step is to get authenticated for the Resource Manager API which allows us to tag instances with metadata in order to manage the provisioning process. Here, you will require four pieces of information. 

1. ```azure_active_directory_username```
2. ```azure_active_directory_password```
3. ```azure_active_directory_tenant_id```
4. ```azure_active_directory_client_id```
 
Unfortunately, it looks like this segment needs to be done in the old Azure portal. First, follow [this document](http://blog.baslijten.com/create-an-organizational-account-to-administrate-azure-when-having-a-microsoft-account/) in order to create an organizational account to administer your work. If you have logged in using a purely Azure account you may be able to skip this step but otherwise if you have a Microsoft account you will need to follow this. This will give you the first two pieces of information. Note that you cannot use the password that is first set when you create the account. You will need to login at least once to reset the password while logged in as that user. 

Next, you'll need to get the ```azure_active_directory_tenant_id```. Sadly, it looks like the only place to get that is to look in the URL for the active directory tab in the Azure portal. For example, if your URL was ```https://manage.windowsazure.com/@funkyuser.onmicrosoft.com#Workspaces/ActiveDirectoryExtension/Directory/sdfhsdgh-sdagsadg-sdgsdgds-f908397/reports``` then your tenant id would be ```sdfhsdgh-sdagsadg-sdgsdgds-f908397```.

Finally, the last piece of information that is required is the ```azure_active_directory_client_id```. You can find this by following this document under the header of [Set up authentication using the Management Portal](https://msdn.microsoft.com/en-us/library/azure/dn790557.aspx#bk_portal). Note that the redirect URI is unimportant. You only need to follow steps 1 and 2. After you're done you will be able to find your client id under the properties listing for your application. 


## Additional Settings

### SSH Access

Currently, your SSH key will be burned into your Azure image. Use the parameter ```azure_ssh_key``` to specify the path to your private key that will be used to SSH into workers and configure them. 

### Deployer Options

Two additional settings require explanation. Due to the lack of an azure API for determining when instances are available for SSH (as opposed to when the synchronous API returns after merely completing the request for a VM), there is an ```arbitrary_wait``` for which 200000 milliseconds (3.33 minutes) seems sufficient. 

Additionally, the virtual network is also needed. This can be created from the Preview Portal. Note that we require the full network name which is not what you entered as the name. Browse to this via "Browse All" -> "Virtual networks (classic) -> <your network> -> "All settings" -> "Properties" -> "Full network name". 
