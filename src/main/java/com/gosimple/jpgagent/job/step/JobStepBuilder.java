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
import com.gosimple.jpgagent.job.Job;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class JobStepBuilder
{
    private Job job;
    private int step_id;
    private String step_name;
    private String step_description;
    private StepType step_type;
    private String code;
    private String connection_string;
    private String database_name;
    private OnError on_error;

    public JobStepBuilder setJob(Job job)
    {
        this.job = job;
        return this;
    }

    public JobStepBuilder setStepId(int step_id)
    {
        this.step_id = step_id;
        return this;
    }

    public JobStepBuilder setStepName(String step_name)
    {
        this.step_name = step_name;
        return this;
    }

    public JobStepBuilder setStepDescription(String step_description)
    {
        this.step_description = step_description;
        return this;
    }

    public JobStepBuilder setStepType(StepType step_type)
    {
        this.step_type = step_type;
        return this;
    }

    public JobStepBuilder setCode(String code)
    {
        this.code = code;
        return this;
    }

    public JobStepBuilder setConnectionString(String connection_string)
    {
        this.connection_string = connection_string;
        return this;
    }

    public JobStepBuilder setDatabaseName(String database_name)
    {
        this.database_name = database_name;
        return this;
    }

    public JobStepBuilder setOnError(OnError on_error)
    {
        this.on_error = on_error;
        return this;
    }

    public JobStep createJobStep()
    {
        return new JobStep(job, step_id, step_name, step_description, step_type, code, connection_string, database_name, on_error);
    }

    /**
     * Returns a list of job steps to
     * @param job
     * @return
     */
    public static List<JobStep> createJobSteps(final Job job)
    {
        Config.INSTANCE.logger.debug("Building steps.");
        List<JobStep> job_step_list = new ArrayList<>();
        final String step_sql = Config.INSTANCE.sql.getProperty("sql.jobstepbuilder.build_step");
        try (final PreparedStatement statement = Database.INSTANCE.getMainConnection().prepareStatement(step_sql))
        {
            statement.setInt(1, job.getJobId());

            try (final ResultSet resultSet = statement.executeQuery())
            {
                while (resultSet.next())
                {
                    JobStep job_step = new JobStepBuilder()
                            .setJob(job)
                            .setStepId(resultSet.getInt("jstid"))
                            .setStepName(resultSet.getString("jstname"))
                            .setStepDescription(resultSet.getString("jstdesc"))
                            .setStepType(StepType.convertTo(resultSet.getString("jstkind")))
                            .setCode(resultSet.getString("jstcode"))
                            .setConnectionString(resultSet.getString("jstconnstr"))
                            .setDatabaseName(resultSet.getString("jstdbname"))
                            .setOnError(OnError.convertTo(resultSet.getString("jstonerror")))
                            .createJobStep();
                    job_step_list.add(job_step);
                }
            }
        }
        catch (final SQLException e)
        {
            Config.INSTANCE.logger.error("An error occurred getting job steps.");
            Config.INSTANCE.logger.error(e.getMessage());
        }
        return job_step_list;
    }
}