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

package com.gosimple.jpgagent.job;

import com.gosimple.jpgagent.Config;
import com.gosimple.jpgagent.database.Database;
import com.gosimple.jpgagent.job.step.JobStep;
import com.gosimple.jpgagent.job.step.JobStepBuilder;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class JobBuilder
{
    private Integer job_id;
    private String job_name;
    private String job_comment;
    private Integer job_log_id;

    public JobBuilder setJobId(Integer job_id)
    {
        this.job_id = job_id;
        return this;
    }

    public JobBuilder setJobName(String job_name)
    {
        this.job_name = job_name;
        return this;
    }

    public JobBuilder setJobComment(String job_comment)
    {
        this.job_comment = job_comment;
        return this;
    }

    public JobBuilder setJobLogId(Integer job_log_id)
    {
        this.job_log_id = job_log_id;
        return this;
    }

    public Job createJob()
    {
        return new Job(job_id, job_name, job_comment, job_log_id);
    }

    public static Job createJob(final int job_id, final String job_name, final String job_comment)
    {
        return new JobBuilder().setJobId(job_id).setJobName(job_name).setJobComment(job_comment).setJobLogId(JobLog.startLog(job_id)).createJob();
    }
}