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

package com.gosimple.jpgagent.job.step;

import com.gosimple.jpgagent.Config;
import com.gosimple.jpgagent.database.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author Adam Brusselback.
 */
public class JobStepLog
{
    /**
     *
     * @param job_log_id the job_log_id that was created for the job.
     * @param step_id the step_id of the current step to be logged.
     * @return the {@code int} job_step_log_id that was created in the database
     */
    public static int startLog(final int job_log_id, final int step_id)
    {
        final String log_sql = Config.INSTANCE.sql.getProperty("sql.jobsteplog.start_log");
        Integer job_step_log_id = null;
        try (final PreparedStatement log_statement = Database.INSTANCE.getMainConnection().prepareStatement(log_sql))
        {
            log_statement.setInt(1, job_log_id);
            log_statement.setInt(2, step_id);
            log_statement.setString(3, StepStatus.RUNNING.getDbRepresentation());
            try (ResultSet resultSet = log_statement.executeQuery())
            {
                while (resultSet.next())
                {
                    job_step_log_id = resultSet.getInt("jslid");
                }
            }
        }
        catch (final SQLException e)
        {
            Config.INSTANCE.logger.error("Could not save job step log to database.");
            Config.INSTANCE.logger.error("Exception: " + e.toString());
            Config.INSTANCE.logger.error("Message: " + e.getMessage());
        }

        // If unable to return a job_step_log_id throw an exception.
        if(job_step_log_id == null)
        {
            throw new IllegalStateException("Unable to return a job step log id for an unknown reason.");
        }

        return job_step_log_id;
    }

    public static void finishLog(final int job_step_log_id, final JobStepResult step_result)
    {
        final String log_sql = Config.INSTANCE.sql.getProperty("sql.jobsteplog.finish_log");
        try (PreparedStatement update_log_statement = Database.INSTANCE.getMainConnection().prepareStatement(log_sql))
        {
            update_log_statement.setString(1, step_result.getStepStatus().getDbRepresentation());
            update_log_statement.setInt(2, step_result.getStepResult());
            update_log_statement.setString(3, step_result.getStepOutput());
            update_log_statement.setInt(4, job_step_log_id);
            update_log_statement.execute();
        }
        catch (final SQLException e)
        {
            Config.INSTANCE.logger.error("Could not save job step log to database.");
            Config.INSTANCE.logger.error("Exception: " + e.toString());
            Config.INSTANCE.logger.error("Message: " + e.getMessage());
        }
    }
}
