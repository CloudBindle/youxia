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

As a prerequisite, please install ansible. You can use the [directions](https://github.com/CloudBindle/Bindle/blob/develop/README.md) from the Bindle project.
You should also look at the instructions for ansible\_sensu. At a minimum, you will need to generate your SSL certificates with the script in ansible\_sensu/ssl from that directory.

First, configure your AWS security credentials in a <code>.aws/config</code> file in your home directory. For example:

    [default]
    aws_access_key_id     = your AWS access key
    aws_secret_access_key = your AWS secret access key

Second, configure your Youxia credentials in a <code>.youxia/config</code> file in your home directory. For example:

	[youxia]
	sensu_username = admin
	sensu_password = XXXXX 
	sensu_ip_address = XX.XX.XXX.XXX
	aws_ssh_key = /home/dyuen/.ssh/oicr-aws-dyuen.pem
	aws_key_name = oicr-aws-dyuen
        aws_security_group = launch-wizard-73
	openstack_username = sweng:dyuen	
	openstack_password = XXXXX
	openstack_endpoint = http://sweng.os.oicr.on.ca:5000/v2.0/
	zone = us-east-1a
	managed_tag = drunken_master

	[deployer]
	ami_image = ami-90da15f8
	instance_type = m1.xlarge
        security_group = launch-wizard-73


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

Next, you will need to setup the various components on AWS. Note that you will need to open certain ports for your security group settings. 

For our security group, we needed to expose the following ports: 

* all TCP traffic between nodes in the security group should be allowed (this uses private ip addresses as sources)
* all TCP traffic from the public sensu-server up address should be allowed (this uses the public ip address as a source)
* SSH should be open across the board
* port 3000 (uchiwa), 4567 (rabbitmq) and 5671 (sensu-api) should be open from your institution

Make sure to customise your managed\_tag since this is how we track your instances whether on AWS or OpenStack and also determines the domain for persistence of WorkflowRun information to SimpleDB.  

#### Multiple Webservices

The initial version of this project is designed to deploy and manage single-node SeqWare instances on AWS paired with a decider and GNOS repo on an academic cloud. If you are in OICR, you can also take a look at our specification in progress on our [wiki](https://wiki.oicr.on.ca/display/SEQWARE/Youxia+Tools+Specification).

First, you will want to setup a sensu central server. I recommend using the ansible\_sensu playbook with only a manually provisioned ubuntu host specified as a "sensu-server". In our experiments, it is also possible for the Deployer to configure both a sensu-server and clients at the same time. 

##### Deployer

Second, you will probably want to setup the Deployer component. The deployer component provisions VMs on AWS given the ami image and instance type specified in the configuration above. It also tags them with a managed\_tag in order to indicate that this is managed by youxia (thus the tag should be unique to each paired cloud) and then runs an Ansible playbook on it in order to configure ansible and other modifications from the base AMI image. 

A sample command:

    java -jar youxia-deployer/target/youxia-deployer-1.1.0-alpha.0-jar-with-dependencies.jar --ansible-playbook ansible_sensu/site.yml --max-spot-price 1 --batch-size 1 --total-nodes-num 10

This indicates that you will pay a maximum spot price of 1, deploy a maximum of 1 VM per execution, up to a maximum of 10 managed nodes on AWS. You will probably want to put the jar and playbook somewhere on your deployer host. You may also want to update your crontab with the command at an appropriate interval on an appropriate host.

##### Reaper

Third, you will probably want to setup the Reaper component. The Reaper component talks to managed instances to persist their workflow run information to SimpleDB, determine whether VMs have reached their kill-limit, and kills them if necessary after potentially cross-referencing with sensu data. 

A sample command (omit the test to actually kill instances) to kill instances when they have run 5 workflow runs to completion and persist workflow run information to simpleDB:

    java -jar youxia-reaper/target/youxia-reaper-1.1.0-alpha.0-jar-with-dependencies.jar --kill-limit 5 --persist --test

You can also just list information in SimpleDB to be used by the decider with:

    java -jar youxia-reaper/target/youxia-reaper-1.1.0-alpha.0-jar-with-dependencies.jar --list

##### Generator

Fourth, you will probably want to setup the Generator component. The Generator component talks to AWS, OpenStack, and potentially a manually created JSON listing and merges them to produce a listing of valid VMs to schedule with.

A sample command is:

    java -jar youxia-generator/target/youxia-generator-1.1.0-alpha.0-jar-with-dependencies.jar --aws --openstack --json youxia-common/src/test/resources/io/cloudbindle/youxia/pawg/api/single_cluster.json

This will merge a listing of managed VMs from AWS, OpenStack, and a manually generated JSON file. This component requires no ports aside from connectivity to AWS and Openstack. 


## Other Links

See [Youxia](https://en.wikipedia.org/wiki/Youxia)
