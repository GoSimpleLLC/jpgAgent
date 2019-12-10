# jpgAgent
jpgAgent is a job scheduler for PostgreSQL written in Java. It is low overhead, and aims to be fully
compatible with pgAgent.

The reason for writing a pgAgent compatible job scheduler is to be able to utilize the tools already in place
for pgAgent in the pgAdmin UI, minimizing the pain of switching for existing pgAgent users (uses the same database schema),
and provide a more stable and feature rich implementation of the agent.


## Requires:
jpgAgent requires Java 8+ and PostgreSQL 9.2+

## Additional features:
### Kill a running job
We support killing a job through Listen/Notify channels in Postgres.  It was implemented this way to be the easiest
to use without extending the UI to support it.

    NOTIFY jpgagent_kill_job, 'job_id_here';

### Annotations
Annotations can be added at the job, and job step level.
Annotations are added in the job comment field, or job step description field, must be on their own line, and in the correct format.

Annotations that take a time measurement support different suffixes for the value [ms, s, m, h].

Annotations that take a JOB_STATUS support the values of: [FAIL;SUCCEED;ABORTED;IGNORE]

Annotations that take a JOB_STEP_STATUS support the values of: [FAIL;SUCCEED;ABORTED;IGNORE]



#### Job

    @JOB_TIMEOUT=30 s;
    @EMAIL_ON=FAIL[;{JOB_STATUS}];
    @EMAIL_TO=test@test.com;test2@example.com;
    @EMAIL_SUBJECT=Important Subject;
    @EMAIL_BODY=Job failure:<br>Job name - ~job_name~<br><br>You can even use HTML formatting.

Definitions:
    
    @JOB_TIMEOUT If the job takes longer than specified to complete, the job will abort, and abort all 
    steps that have not completed yet. The steps that did complete are not affected.
    
    @EMAIL_ON Only send an email on this list of JOB_STATUS.
    
    @EMAIL_TO Email address('s) to send the message to.
    
    @EMAIL_SUBJECT The subject of the email to be sent. Can contain html formatting.
    Tokens available: ~status~, ~job_name~.
    
    @EMAIL_BODY The body of the email to be sent. Can contain html formatting.
    Tokens available: ~status~, ~job_name~.
    
#### Job Step
    
    @JOB_STEP_TIMEOUT=5 s;
    @RUN_IN_PARALLEL=true;
    @DATABASE_HOST=192.168.1.105;
    @DATABASE_NAME=data_warehouse;
    @DATABASE_LOGIN=username;
    @DATABASE_PASSWORD=securepass;
    @DATABASE_AUTH_QUERY=SELECT user, pass FROM auth_table WHERE active;
    @EMAIL_ON=FAIL;ABORTED;IGNORE;
    @EMAIL_TO=test@test.com;test2@example.com;
    @EMAIL_SUBJECT=Step: ~job_step_name~ ;
    @EMAIL_BODY=Step status: ~status~ <br>Job name - ~job_name~
    
Definitions:

    @RUN_IN_PARALLEL This annotation allows the step it's defined on to run in parallel with the 
    previous step (regardless of the annotations on the previous step).  You can set up some somewhat 
    intricate job flows with this.
    
    @JOB_STEP_TIMEOUT If the step takes longer than specified to complete, the step will abort leaving
    the rest of the job to finish normally.
    
    @DATABASE_HOST If specified, use this database host name to connect instead of the connection info
    specified for jpgAdmin.
    
    @DATABASE_NAME If specified, use this database name to connect instead of the database selected 
    in the job step.  This can be used when the host you are trying to run the job step on has
    a database that is not the server that the PGAgent database is on (the UI only allows you to
    pick from a predefined list).
    
    @DATABASE_LOGIN If specified, use this database login to connect instead of the connection info
    specified for jpgAdmin.
    
    @DATABASE_PASSWORD If specified, use this database password to connect instead of the connection
    info specified for jpgAdmin.
    
    @DATABASE_AUTH_QUERY If specified, use this query to run the job step for each set of credentials returned.  
    This will start a new transaction for each credential returned.  The query must return two columns, the 
    first being user, the second being password.
    
    @EMAIL_ON Only send an email on this list of JOB_STEP_STATUS.
    
    @EMAIL_TO Email address('s) to send the message to.
    
    @EMAIL_SUBJECT The subject of the email to be sent. Can contain html formatting.
    Tokens available for use: ~status~, ~job_name~, ~job_step_name~.
    
    @EMAIL_BODY The body of the email to be sent. Can contain html formatting.
    Tokens available for use: ~status~, ~job_name~, ~job_step_name~.
   
    

## Config options:
    --disable-prepared-statement Boolean : Disables prepared statements for all
                                           database connections. Useful if there
                                           is a pooler in front of the database.
                                           (default: false)
    --help                               : Help (default: true)
    --port Integer                       : Database host port. (default: 5432)
    --smtp-email String                  : Email address used for smtp.
    --smtp-host String                   : Server address used for smtp.
    --smtp-password String               : Password used for smtp.
    --smtp-port String                   : Server Port used for smtp.
    --smtp-ssl Boolean                   : Is SSL enabled for the smtp connection. (default: true)
    --smtp-user String                   : User used for smtp.
    --version                            : Version (default: false)
    -d String                            : jpgAgent database.
    -h String                            : Database host address.
    -p String                            : Database password.
    -r Integer                           : Connection retry interval (ms). (default: 30000)
    -t Integer                           : Job poll interval (ms). (default: 10000)
    -u String                            : Database user.

     
### Arguments file
You can create a file which contains your arguments, and pass that into the program instead.  This will protect the password from showing up in logs.
The file can be created anywhere on your filesystem, and must contain the arguments in this format:

        -d=postgres
        -h=127.0.0.1
        -u=test
        -p=password

## Example run

        java -server -jar /path/to/jar/jpgAgent.jar -d postgres -h 127.0.0.1 -u test -p password 
or        

        java -server -jar /path/to/jar/jpgAgent.jar @/usr/jpgagent/args
        
## Special considerations
The remote connection parameter in the job step configuration is not supported, we instead use annotations to allow connections to remote servers.  Reasoning for this 
is due to the extended functionality of our annotations, the single connection string for a remote server was too limiting.

If the database you are trying to connect to is not on the database server jpgAgent is configured for, you can pick any database in the list (usually the maintenance db)
and configure the one you actually want to connect to in an annotation.