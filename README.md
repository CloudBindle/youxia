youxia
======

A collection of tools allowing one to manage clouds of SeqWare instances (specifically an AWS cloud acting as overflow for an academic cloud based on OpenStack).

For most tools, you will need to have a valid Amazon Web Services developer account.

## Configuration

Configure your AWS security credentials in a <code>.aws/config</code> file in your home directory. For example:

    [default]
    aws_access_key_id     = your AWS access key
    aws_secret_access_key = your AWS secret access key

Configure your credentials in a <code>.youxia/config</code> file in your home directory. For example:

    [youxia]
    sensu_username = admin
    sensu_password = seqware
    managed_tag = drunken_master
    
    [deployer]
    ami_image = ami-90da15f8
    

See [Youxia](https://en.wikipedia.org/wiki/Youxia)
