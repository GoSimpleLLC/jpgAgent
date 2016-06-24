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

import com.gosimple.jpgagent.*;
import com.gosimple.jpgagent.annotation.AnnotationUtil;
import com.gosimple.jpgagent.database.Database;
import com.gosimple.jpgagent.email.EmailUtil;
import com.gosimple.jpgagent.job.step.*;
import com.gosimple.jpgagent.thread.CancellableRunnable;
import com.gosimple.jpgagent.thread.ThreadFactory;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

public class Job implements CancellableRunnable
{
    private final int job_id;
    private int job_log_id;
    private String job_name;
    private String job_comment;
    private JobStatus job_status;
    private List<JobStep> job_step_list;
    private final Map<JobStep, Future> future_map = new HashMap<>();
    private Long start_time;
    /*
     * Annotation settings
     */
    // Timeout setting to abort job if running longer than this value.
    private Long job_timeout = null;
    // List of status to send an email on
    private final List<JobStatus> email_on = new ArrayList<>();
    // Email to list
    private String[] email_to = null;
    // Email subject
    private String email_subject = null;
    // Email body
    private String email_body = null;


    public Job(final int job_id, final String job_name, final String job_comment, final int job_log_id)
    {
        Config.INSTANCE.logger.debug("Instantiating Job begin.");
        this.job_id = job_id;
        this.job_name = job_name;
        this.job_comment = job_comment;
        this.job_log_id = job_log_id;
        processAnnotations();
        Config.INSTANCE.logger.debug("Job instantiation complete.");
    }


    public void run()
    {
        try
        {
            Config.INSTANCE.logger.info("Job id: {} started.", job_id);
            this.start_time = System.currentTimeMillis();
            boolean failed_step = false;
            try
            {
                for (JobStep job_step : job_step_list)
                {
                    if (!job_step.canRunInParallel())
                    {
                        // Block until all steps submitted before are done.
                        waitOnRunningJobSteps();
                    }
                    // Submit task.
                    future_map.put(job_step, ThreadFactory.INSTANCE.submitTask(job_step));
                }
                // Block until all JobSteps are done.
                waitOnRunningJobSteps();

                for (JobStep job_step : job_step_list)
                {
                    if (job_step.getStepStatus().equals(StepStatus.FAIL)
                            && job_step.getOnError().equals(OnError.FAIL))
                    {
                        failed_step = true;
                    }
                }
            }
            catch (InterruptedException e)
            {
                job_status = JobStatus.ABORTED;
            }
            if (job_status == null && job_step_list.isEmpty())
            {
                job_status = JobStatus.IGNORE;
            }
            else if (job_status == null && failed_step)
            {
                job_status = JobStatus.FAIL;
            }
            else if (job_status == null)
            {
                job_status = JobStatus.SUCCEED;
            }
        }
        catch (Exception e)
        {
            job_status = JobStatus.FAIL;
            Config.INSTANCE.logger.error(e.getMessage());
        }

        clearJobAgent();

        // Update the log record with the result
        JobLog.finishLog(job_log_id, job_status);

        if(email_on.contains(job_status))
        {
            // Token replacement
            email_subject = email_subject.replaceAll(Config.INSTANCE.status_token, job_status.name());
            email_body = email_body.replaceAll(Config.INSTANCE.status_token, job_status.name());

            email_subject = email_subject.replaceAll(Config.INSTANCE.job_name_token, job_name);
            email_body = email_body.replaceAll(Config.INSTANCE.job_name_token, job_name);

            // Send email
            EmailUtil.sendEmailFromNoReply(email_to, email_subject, email_body);
        }
        Config.INSTANCE.logger.info("Job id: {} complete.", job_id);
    }

    private void clearJobAgent()
    {
        final String update_job_sql = Config.INSTANCE.sql.getProperty("sql.job.clear_job_agent");
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


    /**
     * Assign any values from annotations.
     */
    private void processAnnotations()
    {
        try
        {
            Map<String, String> annotations = AnnotationUtil.parseAnnotations(job_comment);
            if (annotations.containsKey(JobAnnotations.JOB_TIMEOUT.name()))
            {
                job_timeout = AnnotationUtil.parseValue(JobAnnotations.JOB_TIMEOUT, annotations.get(JobAnnotations.JOB_TIMEOUT.name()), Long.class);
            }
            if (annotations.containsKey(JobAnnotations.EMAIL_ON.name()))
            {
                for (String email_on_string : AnnotationUtil.parseValue(JobAnnotations.EMAIL_ON, annotations.get(JobAnnotations.EMAIL_ON.name()), String.class).split(";"))
                {
                    email_on.add(JobStatus.valueOf(email_on_string));
                }
            }
            if (annotations.containsKey(JobAnnotations.EMAIL_TO.name()))
            {
                email_to = AnnotationUtil.parseValue(JobAnnotations.EMAIL_TO, annotations.get(JobAnnotations.EMAIL_TO.name()), String.class).split(";");
            }
            if (annotations.containsKey(JobAnnotations.EMAIL_SUBJECT.name()))
            {
                email_subject = AnnotationUtil.parseValue(JobAnnotations.EMAIL_SUBJECT, annotations.get(JobAnnotations.EMAIL_SUBJECT.name()), String.class);
            }
            if (annotations.containsKey(JobAnnotations.EMAIL_BODY.name()))
            {
                email_body = AnnotationUtil.parseValue(JobAnnotations.EMAIL_BODY, annotations.get(JobAnnotations.EMAIL_BODY.name()), String.class);
            }
        }
        catch (Exception e)
        {
            Config.INSTANCE.logger.error("An issue with the annotations on job_id: " + job_id + " has stopped them from being processed.");
        }
    }

    /**
     * Waits on job steps that are running and responds to timeouts.
     * @throws InterruptedException
     */
    private void waitOnRunningJobSteps() throws InterruptedException
    {
        while(submittedJobStepsRunning())
        {
            submittedJobStepTimeout();
            if(isTimedOut())
            {
                cancelTask();
                Thread.currentThread().interrupt();
            }
            Thread.sleep(200);
        }
    }

    /**
     * Check if the job steps already submitted to run are complete.
     * @return
     */
    private boolean submittedJobStepsRunning()
    {
        boolean jobsteps_running = false;
        for (Future<?> future : future_map.values())
        {
            if (!future.isDone())
            {
                jobsteps_running = true;
                break;
            }
        }
        return  jobsteps_running;
    }

    /**
     * Cancels JobSteps which have timed out prior to finishing.
     */
    private void submittedJobStepTimeout()
    {
        for (JobStep job_step : future_map.keySet())
        {
            final Future<?> future = future_map.get(job_step);
            if(job_step.isTimedOut() && !future.isDone())
            {
                future.cancel(true);
            }
        }
    }

    /**
     * Returns if the job is timed out or not.
     * @return
     */
    public boolean isTimedOut()
    {
        if(null != job_timeout && null != start_time)
        {
            return System.currentTimeMillis() - start_time > job_timeout;
        }
        else
        {
            return false;
        }
    }

    /**
     * Should stop any long running process the thread was doing to exit gracefully as quickly as possible.
     */
    @Override
    public void cancelTask()
    {
        for (Future<?> future : future_map.values())
        {
            if (!future.isDone())
            {
                future.cancel(true);
            }
        }
    }

    public int getJobId()
    {
        return job_id;
    }

    public int getJobLogId()
    {
        return job_log_id;
    }

    public String getJobName()
    {
        return job_name;
    }

    public void setJobStepList(List<JobStep> job_step_list)
    {
        this.job_step_list = job_step_list;
    }
}
