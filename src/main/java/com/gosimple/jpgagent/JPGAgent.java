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

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

public class JPGAgent
{
    private static final Map<Integer, Future> job_future_map = new HashMap<>();

    public static void main(String[] args)
    {
        Config.INSTANCE.logger.info("jpgAgent starting.");
        boolean set_args = setArguments(args);
        if (!set_args)
        {
            System.exit(-1);
        }
        Database.INSTANCE.resetMainConnection();

        while (true)
        {
            try
            {
                Config.INSTANCE.logger.debug("Check if connection is valid.");
                if (Database.INSTANCE.getMainConnection() == null || !Database.INSTANCE.getMainConnection().isValid(1))
                {
                    Database.INSTANCE.resetMainConnection();
                }

                if (!processNotifications())
                {
                    try
                    {
                        Thread.sleep(Config.INSTANCE.job_poll_interval);
                    }
                    catch (InterruptedException ie)
                    {
                        Config.INSTANCE.logger.error(ie.getMessage());
                    }
                    continue;
                }

                if (!cleanup())
                {
                    try
                    {
                        Thread.sleep(Config.INSTANCE.job_poll_interval);
                    }
                    catch (InterruptedException ie)
                    {
                        Config.INSTANCE.logger.error(ie.getMessage());
                    }
                    continue;
                }

                if (!runJobs())
                {
                    try
                    {
                        Thread.sleep(Config.INSTANCE.job_poll_interval);
                    }
                    catch (InterruptedException ie)
                    {
                        Config.INSTANCE.logger.error(ie.getMessage());
                    }

                    continue;
                }

                try
                {
                    Thread.sleep(Config.INSTANCE.job_poll_interval);
                }
                catch (InterruptedException ie)
                {
                    Config.INSTANCE.logger.error(ie.getMessage());
                }
            }
            catch (final SQLException e)
            {
                // If it fails, sleep and try and restart the loop
                Config.INSTANCE.logger.error("Unable to get database connection.");
                Config.INSTANCE.logger.error(e.getMessage());
                try
                {
                    Thread.sleep(Config.INSTANCE.connection_retry_interval);
                }
                catch (InterruptedException ie)
                {
                    Config.INSTANCE.logger.error(ie.getMessage());
                }
            }
            catch (final Exception e)
            {
                // If it fails, sleep and try and restart the loop
                Config.INSTANCE.logger.error("Connection has been lost.");
                try
                {
                    Thread.sleep(Config.INSTANCE.connection_retry_interval);
                }
                catch (InterruptedException ie)
                {
                    Config.INSTANCE.logger.error(ie.getMessage());
                }
            }
        }
    }

    /**
     * Processes notifications which may have been issued on channels that jpgAgent is listening on.
     *
     * @return
     */
    private static boolean processNotifications()
    {
        try (final Statement statement = Database.INSTANCE.getListenerConnection().createStatement();
             final ResultSet result_set = statement.executeQuery("SELECT 1;"))
        {
            Config.INSTANCE.logger.debug("Kill jobs begin.");
            final PGConnection pg_connection = Database.INSTANCE.getListenerConnection().unwrap(PGConnection.class);
            final PGNotification notifications[] = pg_connection.getNotifications();

            if (null != notifications)
            {
                for (int i = 0; i < notifications.length; i++)
                {
                    if (notifications[i].getName().equals("jpgagent_kill_job"))
                    {
                        int job_id = Integer.valueOf(notifications[i].getParameter());
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
        catch (Exception e)
        {
            Config.INSTANCE.logger.error(e.getMessage());
            return false;
        }
        return true;
    }

    /**
     * Does cleanup and initializes jpgAgent to start running jobs again.
     * Returns true if successful, false if an error was encountered.
     *
     * @return
     */
    private static boolean cleanup()
    {
        Config.INSTANCE.logger.debug("Running cleanup to clear old data and re-initialize to start processing.");

        final String cleanup_sql =
                "CREATE TEMP TABLE pga_tmp_zombies(jagpid INTEGER); " +

                        "INSERT INTO pga_tmp_zombies (jagpid) " +
                        "SELECT jagpid " +
                        "FROM pgagent.pga_jobagent AG " +
                        "LEFT JOIN pg_stat_activity PA ON jagpid=pid " +
                        "WHERE pid IS NULL; " +

                        "UPDATE pgagent.pga_joblog SET jlgstatus='d' WHERE jlgid IN (" +
                        "SELECT jlgid " +
                        "FROM pga_tmp_zombies z " +
                        "INNER JOIN pgagent.pga_job j " +
                        "ON z.jagpid=j.jobagentid " +
                        "INNER JOIN pgagent.pga_joblog l " +
                        "ON j.jobid = l.jlgjobid " +
                        "WHERE l.jlgstatus='r'); " +

                        "UPDATE pgagent.pga_jobsteplog SET jslstatus='d' WHERE jslid IN ( " +
                        "SELECT jslid " +
                        "FROM pga_tmp_zombies z " +
                        "INNER JOIN pgagent.pga_job j " +
                        "ON z.jagpid=j.jobagentid " +
                        "INNER JOIN pgagent.pga_joblog l " +
                        "ON j.jobid = l.jlgjobid " +
                        "INNER JOIN pgagent.pga_jobsteplog s " +
                        "ON  l.jlgid = s.jsljlgid " +
                        "WHERE s.jslstatus='r'); " +

                        "UPDATE pgagent.pga_jobsteplog SET jslstatus='d' " +
                        "WHERE jslid IN ( " +
                        "SELECT jslid " +
                        "FROM pgagent.pga_joblog l " +
                        "INNER JOIN  pgagent.pga_jobsteplog s " +
                        "ON l.jlgid = s.jsljlgid " +
                        "WHERE TRUE " +
                        "AND l.jlgstatus <> 'r' " +
                        "AND s.jslstatus = 'r'); " +

                        "UPDATE pgagent.pga_job SET jobagentid=NULL, jobnextrun=NULL " +
                        "WHERE jobagentid IN (SELECT jagpid FROM pga_tmp_zombies); " +

                        "DELETE FROM pgagent.pga_jobagent " +
                        "WHERE jagpid IN (SELECT jagpid FROM pga_tmp_zombies);" +

                        "DROP TABLE pga_tmp_zombies; ";


        final String register_agent_sql =
                "INSERT INTO pgagent.pga_jobagent (jagpid, jagstation) SELECT ?, ? " +
                        "WHERE NOT EXISTS (" +
                        "SELECT 1" +
                        "FROM pgagent.pga_jobagent " +
                        "WHERE jagpid = ? " +
                        "AND jagstation = ?);";

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
        catch (final SQLException e)
        {
            Config.INSTANCE.logger.error("Encountered SQL exception.");
            Config.INSTANCE.logger.error(e.getMessage());
            return false;
        }


        Config.INSTANCE.logger.debug("Cleanup of completed jobs started.");
        final List<Integer> job_ids_to_remove = new ArrayList<>();
        for (Integer job_id : job_future_map.keySet())
        {
            if (job_future_map.get(job_id).isDone())
            {
                job_ids_to_remove.add(job_id);

                final String update_job_sql =
                        "UPDATE pgagent.pga_job SET jobagentid=NULL, jobnextrun=NULL " +
                                "WHERE jobid = ?;";

                try (final PreparedStatement update_job_statement = Database.INSTANCE.getMainConnection().prepareStatement(update_job_sql))
                {
                    update_job_statement.setInt(1, job_id);
                    update_job_statement.execute();
                }
                catch (SQLException e)
                {
                    Config.INSTANCE.logger.error(e.getMessage());
                }
            }
        }

        for (Integer job_id : job_ids_to_remove)
        {
            job_future_map.remove(job_id);
        }
        job_ids_to_remove.clear();

        Config.INSTANCE.logger.debug("Successfully cleaned up.");

        return true;
    }

    private static boolean runJobs()
    {
        Config.INSTANCE.logger.debug("Running jobs begin.");
        final String get_job_sql =
                "UPDATE pgagent.pga_job " +
                        "SET jobagentid=?, joblastrun=now() " +
                        "WHERE jobenabled " +
                        "AND jobagentid IS NULL " +
                        "AND jobnextrun <= now() " +
                        "AND (jobhostagent = '' OR jobhostagent = ?) " +
                        "RETURNING jobid; ";


        try (final PreparedStatement get_job_statement = Database.INSTANCE.getMainConnection().prepareStatement(get_job_sql))
        {
            get_job_statement.setInt(1, Database.INSTANCE.getPid());
            get_job_statement.setString(2, Config.INSTANCE.hostname);
            try (final ResultSet resultSet = get_job_statement.executeQuery())
            {
                while (resultSet.next())
                {
                    final int job_id = resultSet.getInt("jobid");
                    final Job job = new Job(job_id);
                    Config.INSTANCE.logger.debug("Submitting job_id {} for execution.", job_id);
                    job_future_map.put(job_id, ThreadFactory.INSTANCE.submitTask(job));
                }
            }
        }
        catch (final SQLException e)
        {
            Config.INSTANCE.logger.error("Encountered SQL exception.");
            Config.INSTANCE.logger.error(e.getMessage());
            return false;
        }

        Config.INSTANCE.logger.debug("Running jobs complete.");
        return true;
    }

    /**
     * Sets the arguments passed in from command line.  Returns true if successful, false if it encountered an error.
     *
     * @param args
     * @return
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
            Config.INSTANCE.logger.error(e.getMessage());
            return false;
        }

        try
        {
            Config.INSTANCE.hostname = InetAddress.getLocalHost().getCanonicalHostName();
        }
        catch (final UnknownHostException e)
        {
            Config.INSTANCE.logger.error("Had trouble getting a host name to register.");
            Config.INSTANCE.logger.error(e.getMessage());
            return false;
        }
        return true;
    }
}