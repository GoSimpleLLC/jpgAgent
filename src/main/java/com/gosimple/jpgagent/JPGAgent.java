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

import com.gosimple.jpgagent.database.Database;
import com.gosimple.jpgagent.job.Job;
import com.gosimple.jpgagent.job.JobBuilder;
import com.gosimple.jpgagent.job.step.JobStepBuilder;
import com.gosimple.jpgagent.thread.ExecutionUtil;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public class JPGAgent
{
    private static final Map<Integer, Future<?>> job_future_map = new HashMap<>();

    private static AtomicBoolean run_cleanup = new AtomicBoolean(true);

    public static void main(String[] args)
    {
        boolean set_args = setArguments(args);
        if (!set_args)
        {
            System.exit(-1);
        }

        Config.INSTANCE.logger.info("jpgAgent starting.");

        // Enter main loop
        while (true)
        {
            try
            {
                Config.INSTANCE.logger.debug("Check if main connection is valid.");
                if (Database.INSTANCE.getMainConnection() == null || !Database.INSTANCE.getMainConnection().isValid(1))
                {
                    Database.INSTANCE.resetMainConnection();
                }
                Config.INSTANCE.logger.debug("Check if listener connection is valid.");
                if (Database.INSTANCE.getListenerConnection() == null || !Database.INSTANCE.getListenerConnection().isValid(1))
                {
                    Database.INSTANCE.resetListenerConnection();
                }

                // Process all incoming notifications.
                processNotifications();

                // Run cleanup of zombie jobs.
                cleanup();

                // Actually run new jobs.
                runJobs();

                // Sleep for the allotted time before starting all over.
                Thread.sleep(Config.INSTANCE.job_poll_interval);
            }
            catch (final Exception e)
            {
                // If it fails, sleep and try and restart the loop
                Config.INSTANCE.logger.error("Error encountered in the main loop.");
                Config.INSTANCE.logger.error("Exception: " + e.toString());
                Config.INSTANCE.logger.error("Message: " + e.getMessage());
                Config.INSTANCE.logger.error("Stack trace: ");
                for(StackTraceElement stackTrace : e.getStackTrace())
                {
                    Config.INSTANCE.logger.error(stackTrace.toString());
                }
                runCleanup();
                try
                {
                    Thread.sleep(Config.INSTANCE.connection_retry_interval);
                }
                catch (InterruptedException ie)
                {
                    Config.INSTANCE.logger.error("Error sleeping main thread.");
                    Config.INSTANCE.logger.error("Exception: " + ie.toString());
                    Config.INSTANCE.logger.error(ie.getMessage());
                    Config.INSTANCE.logger.error("Stack trace: ");
                    for(StackTraceElement stackTrace : e.getStackTrace())
                    {
                        Config.INSTANCE.logger.error(stackTrace.toString());
                    }
                }
            }
        }
    }

    /**
     * Processes notifications which may have been issued on channels that jpgAgent is listening on.
     */
    private static void processNotifications() throws Exception
    {
        try (final Statement statement = Database.INSTANCE.getListenerConnection().createStatement();
             final ResultSet result_set = statement.executeQuery(Config.INSTANCE.sql.getProperty("sql.jpgagent.dummy")))
        {
            Config.INSTANCE.logger.debug("Kill jobs begin.");
            final PGConnection pg_connection = Database.INSTANCE.getListenerConnection().unwrap(PGConnection.class);
            final PGNotification[] notifications = pg_connection.getNotifications();

            if (null != notifications)
            {
                for (PGNotification notification : notifications)
                {
                    if (notification.getName().equals("jpgagent_kill_job"))
                    {
                        int job_id = Integer.parseInt(notification.getParameter());
                        if (job_future_map.containsKey(job_id))
                        {
                            Config.INSTANCE.logger.info("Killing job_id: {}.", job_id);
                            job_future_map.get(job_id).cancel(true);
                        }
                        else
                        {
                            Config.INSTANCE.logger.info("Kill request for job_id: {} was submitted, but the job was not running.", job_id);
                        }
                    }
                }
            }
        }
    }

    /**
     * Does cleanup and initializes jpgAgent to start running jobs again.
     * Only runs if run_cleanup is true.
     */
    private static void cleanup() throws Exception
    {
        if(run_cleanup.compareAndSet(true, false))
        {
            Config.INSTANCE.logger.debug("Running cleanup to clear old data and re-initialize to start processing.");

            final String cleanup_sql = Config.INSTANCE.sql.getProperty("sql.jpgagent.cleanup");

            final String register_agent_sql = Config.INSTANCE.sql.getProperty("sql.jpgagent.register_agent");

            try (final Statement statement = Database.INSTANCE.getMainConnection().createStatement();
                 final PreparedStatement register_agent_statement = Database.INSTANCE.getMainConnection().prepareStatement(register_agent_sql))
            {
                statement.execute(cleanup_sql);
                register_agent_statement.setInt(1, Database.INSTANCE.getPid());
                register_agent_statement.setString(2, Config.INSTANCE.hostname);
                register_agent_statement.setInt(3, Database.INSTANCE.getPid());
                register_agent_statement.setString(4, Config.INSTANCE.hostname);
                register_agent_statement.execute();
            }


            Config.INSTANCE.logger.debug("Cleanup of completed jobs started.");
            final List<Integer> job_ids_to_remove = new ArrayList<>();
            for (Integer job_id : job_future_map.keySet())
            {
                if (job_future_map.get(job_id).isDone())
                {
                    job_ids_to_remove.add(job_id);
                }
            }

            for (Integer job_id : job_ids_to_remove)
            {
                job_future_map.remove(job_id);
            }
            job_ids_to_remove.clear();

            Config.INSTANCE.logger.debug("Successfully cleaned up.");
        }
        else
        {
            Config.INSTANCE.logger.debug("Cleanup unnecessary.");
        }
    }

    private static void runJobs() throws Exception
    {
        Config.INSTANCE.logger.debug("Running jobs begin.");
        final String get_job_sql = Config.INSTANCE.sql.getProperty("sql.jpgagent.get_job");


        try (final PreparedStatement get_job_statement = Database.INSTANCE.getMainConnection().prepareStatement(get_job_sql))
        {
            get_job_statement.setInt(1, Database.INSTANCE.getPid());
            get_job_statement.setString(2, Config.INSTANCE.hostname);
            try (final ResultSet resultSet = get_job_statement.executeQuery())
            {
                while (resultSet.next())
                {
                    final int job_id = resultSet.getInt("jobid");
                    final Job job = JobBuilder.createJob(job_id, resultSet.getString("jobname"), resultSet.getString("jobdesc"));
                    job.setJobStepList(JobStepBuilder.createJobSteps(job));
                    Config.INSTANCE.logger.debug("Submitting job_id {} for execution.", job_id);
                    job_future_map.put(job_id, ExecutionUtil.INSTANCE.submitTask(job));
                }
            }
        }

        Config.INSTANCE.logger.debug("Running jobs complete.");
    }

    /**
     * Sets the arguments passed in from command line.
     * Returns true if successful, false if it encountered an error.
     *
     * @param args the arguments to parse
     * @return if parsing the arguments was successful or not
     */
    private static boolean setArguments(final String[] args)
    {
        final CmdLineParser parser = new CmdLineParser(Config.INSTANCE);

        try
        {
            parser.parseArgument(args);
        }
        catch (final CmdLineException e)
        {
            System.out.println(e.getMessage());
            parser.printUsage(System.out);
            return false;
        }

        if(Config.INSTANCE.help)
        {
            parser.printUsage(System.out);
            return false;
        }

        if(Config.INSTANCE.version)
        {
            System.out.println("jpgAgent version: " + JPGAgent.class.getPackage().getImplementationVersion());
            return false;
        }

        try
        {
            Config.INSTANCE.hostname = InetAddress.getLocalHost().getHostName();
        }
        catch (final UnknownHostException e)
        {
            Config.INSTANCE.logger.error("Unable to get host name to register.");
            Config.INSTANCE.logger.error(e.getMessage());
            return false;
        }
        return true;
    }

    public static void runCleanup()
    {
        run_cleanup.compareAndSet(false, true);
    }
}
