[![Build Status](https://travis-ci.org/CloudBindle/youxia.svg?branch=develop)](https://travis-ci.org/CloudBindle/youxia)
[![Coverage Status](https://coveralls.io/repos/CloudBindle/youxia/badge.svg?branch=develop)](https://coveralls.io/r/CloudBindle/youxia?branch=develop)

youxia
======

A collection of tools allowing one to manage clouds of SeqWare instances (specifically an AWS cloud acting as overflow for an academic cloud based on OpenStack).

For most tools, you will need to have a valid Amazon Web Services developer account.

## Developers

1. Java contributions must be formatted using SeqWare's Eclipse formatting file. See https://seqware.github.io/docs/100-coding-standards/ for more details. Contributions that do not follow this formatting will be rejected. 
2. Test your build against Checkstyle, a few Maven checks for consistent dependencies, and a high priority findbugs check using <code>mvn clean install</code>
3. Check your build status on jenkins
4. Releases use the standard maven release:prepare release:perform plugins

## Users

### Configuration

The steps for deploying youxia are stored in an Ansible playbook at [youxia-setup](https://github.com/CloudBindle/youxia/tree/develop/youxia-setup). Note the dependencies contained within such as Ansible and Java 7. 

As an alternative to building in an environment with access to Maven 3.0.4 and Java 7, you can also download our built artifacts from [Artifactory](https://seqwaremaven.oicr.on.ca/artifactory/seqware-release/io/cloudbindle/).

First, configure your AWS security credentials in a <code>.aws/config</code> file in your home directory. For example:

    [default]
    aws_access_key_id     = your AWS access key
    aws_secret_access_key = your AWS secret access key

Second, configure your Youxia credentials in a <code>.youxia/config</code> file in your home directory. Fill in the sections between the angled brackets. For example:

	[youxia]
	sensu_username = admin
	sensu_password = seqware
	sensu_ip_address = <ip-address of sensu server>
	sensu_port = 4567
	managed_tag = green_snake
	slack_webhook = <slack RSS handler> 

	# aws settings
	aws_ssh_key = <path to ssh key>
	aws_key_name = <key name on Amazon>
	region = eu-west-1 
	# to specify multiple zones, use a comma-separated list
	# we will target the zone with the cheapest spot price first
	zone = eu-west-1a
	# openstack settings
	openstack_username = <tenant>:<username> 
	openstack_password = <password>
	openstack_endpoint = http://10.5.73.21:5000/v2.0
	openstack_key_name = <key name on OpenStack>
	openstack_ssh_key = <path to SSH key>
	openstack_region = <region ex: RegionOne>
        # search for a location using this string, can be left blank if you don't care 
	openstack_zone = <zone> 

	[deployer]
	ami_image = <image id>
	instance_type = m1.xlarge
	security_group = <security group>
	product = Linux/UNIX
        # disable the following if you cannot setup the sensu server (for example, due to lack of permissions)
        # disable_sensu_server = true

	[deployer_openstack]
        flavor = m1.tiny
        # flavour overrides cores and ram, which can be used to search for an appropriate instance
	min_cores = 4 
	min_ram = 16384
	image_id = <image id>
	security_group = default
	network_id = <network id>
	arbitrary_wait = 200000

	[seqware]
	rest_user = admin@admin.com
	rest_pass = admin
	rest_port = 8080
	rest_root = SeqWareWebService

	[generator]
	max_scheduled_workflows = 1
	max_workflows = 1
	workflow_accession = 1 
	workflow_name = HelloWorld 
	workflow_version = 1.0-SNAPSHOT

Next, you will need to setup the various components on AWS. Note that you will need to open certain ports for your security group settings. 

For our security group, we needed to expose the following ports: 

* all TCP traffic between nodes in the security group should be allowed (this uses private ip addresses as sources)
* all TCP traffic from the public sensu-server ip address should be allowed (this uses the public ip address as a source)
* SSH should be open across the board
* port 3000 (uchiwa), 4567 (rabbitmq) and 5671 (sensu-api) should be open from your institution

Make sure to customise your managed\_tag since this is how we track your instances whether on AWS or OpenStack and also determines the domain for persistence of WorkflowRun information to SimpleDB.  

Finally, note that Slack is used as a quick monitoring tool to track at a rough level, instances being provisioned and terminated. You will need to create an incoming webhook at slack.com and set your token above correctly. 

#### Multiple Webservices

The initial version of this project is designed to deploy and manage single-node SeqWare instances on AWS paired with a decider and GNOS repo on an academic cloud. If you are in OICR, you can also take a look at our specification in progress on our [wiki](https://wiki.oicr.on.ca/display/SEQWARE/Youxia+Tools+Specification).

First, you will want to setup a sensu central server. I recommend using the ansible\_sensu playbook with only a manually provisioned ubuntu host specified as a "sensu-server". In our experiments, it is also possible for the Deployer to configure both a sensu-server and clients at the same time. 

##### Deployer

Second, you will probably want to setup the Deployer component. The deployer component provisions VMs on AWS given the ami image and instance type specified in the configuration above. It also tags them with a managed\_tag in order to indicate that this is managed by youxia (thus the tag should be unique to each paired cloud) and then runs an Ansible playbook on it in order to configure ansible and other modifications from the base AMI image. 

A sample command:

    java -jar youxia-deployer/target/youxia-deployer-*-jar-with-dependencies.jar --ansible-playbook ansible_sensu/site.yml --max-spot-price 1 --batch-size 1 --total-nodes-num 10

This indicates that you will pay a maximum spot price of 1, deploy a maximum of 1 VM per execution, up to a maximum of 10 managed nodes on AWS. You will probably want to put the jar and playbook somewhere on your deployer host. You may also want to update your crontab with the command at an appropriate interval on an appropriate host.

Note that it is possible to pass variables in yaml or json format to the ansible playbook using an extra parameter. For example, with the following variable file:

	{
	"lvm_device_whitelist" : "/dev/xvdc,/dev/xvdd,/dev/xvde,/dev/xvdf",
	"single_node_lvm" : true
	}

You could use:

    java -jar youxia-deployer/target/youxia-deployer-*-jar-with-dependencies.jar --ansible-playbook /home/ubuntu/youxia/ansible_sensu/site.yml --max-spot-price 0.001 --batch-size 1 --total-nodes-num 1 -e /home/ubuntu/crons/deployer_variables.json
    
Append the parameter --openstack to deploy OpenStack instances

There are also two parameters that can be used to deploy a minimum number of on-demand instances (--max-on-demand) and cap the number of on-demand instances (--min-on-demand). 

Finally, we tag created instances with some information for identification and state purposes. You can add your own additional tags via the (--server-tag-file).

        {"one":"won","two":"two"}

##### Reaper

Third, you will probably want to setup the Reaper component. The Reaper component talks to managed instances to persist their workflow run information to SimpleDB, determine whether VMs have reached their kill-limit, and kills them if necessary after potentially cross-referencing with sensu data. The kill-limit refers to the number of instances that have reached a "final" state in SeqWare, specifically the cancelled, failed, and completed states. 

A sample command (omit the test to actually kill instances) to kill instances when they have run 5 workflow runs to completion and persist workflow run information to simpleDB:

    java -jar youxia-reaper/target/youxia-reaper-*-jar-with-dependencies.jar --kill-limit 5 --persist --test

You can also kill nodes based on a JSON list of ip addresses rather than a kill limit. The reaper will compare these against the private ip addresses for the nodes it has under management. 

    java -jar youxia-reaper/target/youxia-reaper-*-jar-with-dependencies.jar --kill-list kill.json  --kill-limit 5 --openstack

As example, here is a json list with two addresses:

    [
      "192.168.42.5",
      "192.168.42.49"
    ]


You can also just list information in SimpleDB to be used by the decider with:

    java -jar youxia-reaper/target/youxia-reaper-*-jar-with-dependencies.jar --list

Note, if you want to run both the Reaper and the Generator, you will probably want to put them sequentially in a script run from your crontab so that the Deployer completes execution before the Reaper runs. It is actually possible for the Reaper to reap an instance that the Deployer is still deploying. 

Append the parameter --openstack to reap OpenStack instances

##### Generator

Fourth, you will probably want to setup the Generator component. The Generator component talks to AWS, OpenStack, and potentially a manually created JSON listing and merges them to produce a listing of valid VMs to schedule with.

A sample command is:

    java -jar youxia-generator/target/youxia-generator-*-jar-with-dependencies.jar --aws --openstack --json youxia-common/src/test/resources/io/cloudbindle/youxia/pawg/api/single_cluster.json

This will merge a listing of managed VMs from AWS, OpenStack, and a manually generated JSON file. This component requires no ports aside from connectivity to AWS and Openstack. 

##### Tagger

This component is optional. If you have legacy VMs in OpenStack that you wish to put under management of youxia, unlike AWS, it does not appear possible to edit tags (called metadata in Openstack) from the Openstack GUI. Therefore, we provide this tool that lets you tag instances using a comma-separated listing of their IDs. Substitute your ids as required.

    java -jar youxia-common/target/youxia-common-*-jar-with-dependencies.jar --instance-ids 768c2dfa-66c8-4e0b-8c87-c6d273285e91,a6bf4813-fc66-439d-a490-9b5618c1af05,73a1e754-3c37-43e3-ba55-aaa0e84984f2,25a1fe33-5637-44fc-84ed-6b88cc5871f6

##### Mock Decider

This component can be used to test clusters by scheduling HelloWorld workflows

    java -jar youxia-decider/target/youxia-decider-*-jar-with-dependencies.jar --instance-json cluster.json

##### BWA Blacklist Converter 

This component mutates the JSON for workflows stored in SimpleDB into the blacklist format used by BWA deciders

    java -cp youxia-reaper/target/youxia-reaper-*-jar-with-dependencies.jar io.cloudbindle.youxia.reaper.BWABlackListConverter   --output blacklist.txt

## Environment Specific Notes

### OpenStack

In general, the easiest way to determine your OpenStack youxia settings is to look in the OpenStack web interface. 

* your openstack\_username is what you use to login, make sure to use the format (tenant:username). If you do not know your tenant name, you can find it by navigating to the API Access tab in Access & Security in the OpenStack GUI. Download your RC file and find it there 
* the openstack\_endpoint is listed under Access & Security under "Identity"
* the openstack\_key\_name is just the name of your key under "Key Pairs"
* the openstack\_ssh\_key is the full path of the ssh key that you wish to use
* the openstack\_zone is actually the region name can be found using the "nova endpoints" command (you'll need the nova CLI)
* the image\_id can be found in the web interface by clicking into your desired image
* the security\_group is just the name 
* the network\_id is the ID when you click into Networks -> NetWorks -> your desired network
* arbitrary\_wait is set to a sane default, but if your openstack install is particularly slow, you'll need to lengthen it

#### ETRI

The following special steps need to be taken in order to setup with OpenStack

1. Grab and execute your OpenStack RC file from the web interface page "Access & Security" 
2. Install the OpenStack client from http://docs.openstack.org/user-guide/content/install_clients.html
3. For ETRI, I ran into this issue http://foogeee.blogspot.ca/2012/11/openstack-nova-client-doesnt-work.html
4. In order to determine the region, try the command "nova endpoints" and look for region under nova
5. Also for ETRI, do not specify a network (ETRI uses nova networking as opposed to neutron and thus does not require that a network be specified) 

### AWS

#### Frankfurt (eu-central-1)

Amazon does not have SimpleDB deployed, therefore the sample decider and reaper --persist functionality have to be turned off. 


## Other Links

See [Youxia](https://en.wikipedia.org/wiki/Youxia)
