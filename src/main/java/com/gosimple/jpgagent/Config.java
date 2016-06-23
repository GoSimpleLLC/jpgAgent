/*
 * Copyright (c) 2016, Adam Brusselback
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.gosimple.jpgagent;

import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Properties;


public enum Config
{
    INSTANCE;

    Config()
    {
        // Load the properties
        try(InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream("sql.properties"))
        {
            sql.load(input);
        }
        catch (Exception e)
        {
            // Quit since we couldn't load the sql.
            System.out.println("jpgAgent could not load it's sql properties file and has failed to start.");
            System.exit(-1);
        }
    }

    // Properties
    public final Properties sql = new Properties();
    // Create a logger.
    public final Logger logger = LoggerFactory.getLogger("jpgAgent");
    // Host name for the system running jpgAgent.
    public String hostname;
    // Tokens for email replacement
    public final String status_token = "~status~";
    public final String job_name_token = "~job_name~";
    public final String job_step_name_token = "~job_step_name~";

    @Option(name = "--help", help = true, required = false, usage = "Help")
    public boolean help = false;
    @Option(name = "--version", help = true, required = false, usage = "Version")
    public boolean version = false;
    @Option(name = "-h", required = true, usage = "Database host address.", metaVar = "String")
    public String db_host;
    @Option(name = "--port", required = false, usage = "Database host port.", metaVar = "Integer")
    public int db_port = 5432;
    @Option(name = "-u", required = true, usage = "Database user.", metaVar = "String")
    public String db_user;
    @Option(name = "-p", required = true, usage = "Database password.", metaVar = "String")
    public String db_password;
    @Option(name = "-d", required = true, usage = "jpgAgent database.", metaVar = "String")
    public String db_database;
    @Option(name = "-t", required = false, usage = "Job poll interval (ms).", metaVar = "Integer")
    public long job_poll_interval = 10000;
    @Option(name = "-r", required = false, usage = "Connection retry interval (ms).", metaVar = "Integer")
    public long connection_retry_interval = 30000;
    @Option(name = "-w", required = false, usage = "Size of the thread pool to execute tasks.  Each job and job step can take up to a thread in the pool at once.", metaVar = "Integer")
    public int thread_pool_size = 40;
    @Option(name = "--smtp-host", required = false, usage = "Server address used for smtp.", metaVar = "String")
    public String smtp_host;
    @Option(name = "--smtp-email", required = false, depends = {"--smtp-host"}, usage = "Email address used for smtp.", metaVar = "String")
    public String smtp_email;
    @Option(name = "--smtp-port", required = false, depends = {"--smtp-host"}, usage = "Server Port used for smtp.", metaVar = "String")
    public String smtp_port;
    @Option(name = "--smtp-user", required = false, depends = {"--smtp-host"}, usage = "User used for smtp.", metaVar = "String")
    public String smtp_user;
    @Option(name = "--smtp-password", required = false, depends = {"--smtp-host", "--smtp-user"}, usage = "Password used for smtp.", metaVar = "String")
    public String smtp_password;
    @Option(name = "--smtp-ssl", required = false, depends = {"--smtp-host"}, usage = "Is SSL enabled for the smtp connection.", metaVar = "Boolean")
    public Boolean smtp_ssl = true;


}
