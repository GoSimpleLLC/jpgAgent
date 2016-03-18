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


import org.postgresql.ds.PGSimpleDataSource;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public enum Database
{
    INSTANCE;

    private final PGSimpleDataSource data_source;
    private int pid;
    private Connection main_connection;
    private Connection listener_connection;

    private Database()
    {
        data_source = new PGSimpleDataSource();
        data_source.setServerName(Config.INSTANCE.db_host);
        data_source.setPortNumber(Config.INSTANCE.db_port);
        data_source.setDatabaseName(Config.INSTANCE.db_database);
        data_source.setUser(Config.INSTANCE.db_user);
        data_source.setPassword(Config.INSTANCE.db_password);
        data_source.setApplicationName("jpgAgent: " + Config.INSTANCE.hostname);
    }

    /**
     * Returns the main connection used for all jpgAgent upkeep.
     *
     * @return
     */
    public Connection getMainConnection()
    {
        if (main_connection == null)
        {
            resetMainConnection();
        }
        return main_connection;
    }

    /**
     * Closes existing connection if necessary, and creates a new connection.
     */
    public void resetMainConnection()
    {
        try
        {
            if (main_connection != null)
            {
                main_connection.close();
            }
            main_connection = Database.INSTANCE.getConnection(Config.INSTANCE.db_database);

            String pid_sql = "SELECT pg_backend_pid();";
            try (Statement statement = main_connection.createStatement())
            {
                try (ResultSet result = statement.executeQuery(pid_sql))
                {
                    while (result.next())
                    {
                        pid = result.getInt("pg_backend_pid");
                    }
                }
            }
        }
        catch (final SQLException e)
        {
            Config.INSTANCE.logger.error(e.getMessage());
            main_connection = null;
        }
    }

    /**
     * Returns the main connection used for all jpgAgent upkeep.
     *
     * @return
     */
    public Connection getListenerConnection()
    {
        if (listener_connection == null)
        {
            resetListenerConnection();
        }
        return listener_connection;
    }

    /**
     * Closes existing connection if necessary, and creates a new connection.
     */
    public void resetListenerConnection()
    {
        try
        {
            if (listener_connection != null)
            {
                listener_connection.close();
            }
            listener_connection = Database.INSTANCE.getConnection(Config.INSTANCE.db_database);

            String listen_sql = "LISTEN jpgagent_kill_job;";
            try (Statement statement = listener_connection.createStatement())
            {
                statement.execute(listen_sql);
            }
        }
        catch (final SQLException e)
        {
            Config.INSTANCE.logger.error(e.getMessage());
            listener_connection = null;
        }
    }

    /**
     * Returns the pid of the main connection for jpgAgent.
     *
     * @return
     */
    public int getPid()
    {
        return pid;
    }

    /**
     * Returns a connection to the specified database with autocommit on.
     *
     * @param database
     * @return
     * @throws SQLException
     */
    public synchronized Connection getConnection(final String database) throws SQLException
    {
        data_source.setDatabaseName(database);

        return data_source.getConnection();
    }
}
