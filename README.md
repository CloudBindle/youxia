youxia
======

A collection of tools allowing one to manage clouds of SeqWare instances (specifically an AWS cloud acting as overflow for an academic cloud based on OpenStack).

For most tools, you will need to have a valid Amazon Web Services developer account.

## Developers

1. Java contributions must be formatted using SeqWare's Eclipse formatting file. See https://seqware.github.io/docs/100-coding-standards/ for more details. Contributions that do not follow this formatting will be rejected. 
2. Test your build against Checkstyle and a few Maven checks for consistent dependencies using <code>mvn clean install</code>
3. Check your build status on jenkins

## Users

### Configuration

Configure your AWS security credentials in a <code>.aws/config</code> file in your home directory. For example:

    [default]
    aws_access_key_id     = your AWS access key
    aws_secret_access_key = your AWS secret access key

Configure your credentials in a <code>.youxia/config</code> file in your home directory. For example:

	[youxia]
	sensu_username = admin
	sensu_password = XXXXX 
	sensu_ip_address = XX.XX.XXX.XXX
	aws_ssh_key = /home/dyuen/.ssh/oicr-aws-dyuen.pem
	aws_key_name = oicr-aws-dyuen
	openstack_username = sweng:dyuen	
	openstack_password = XXXXX
	openstack_endpoint = http://sweng.os.oicr.on.ca:5000/v2.0/
	zone = us-east-1a
	managed_tag = drunken_master

	[deployer]
	ami_image = ami-90da15f8
	instance_type = m1.xlarge

	[seqware]
	rest_user = admin@admin.com
	rest_pass = XXXXX
	rest_port = 8080
	rest_root = SeqWareWebService

	[generator]
	max_scheduled_workflows = 1
	max_workflows = 1
	workflow_accession = 2
	workflow_name = Workflow_Bundle_BWA
	workflow_version = 2.6.0

See [Youxia](https://en.wikipedia.org/wiki/Youxia)
