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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
    private final List<JobStep> job_step_list = new ArrayList<>();
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


    public Job(final int job_id)
    {
        this.job_id = job_id;
        Config.INSTANCE.logger.debug("Instantiating Job begin.");
        final String job_sql =
                "SELECT jobname " +
                ", jobdesc " +
                "FROM pgagent.pga_job " +
                "WHERE true " +
                "AND jobid=?;";
        try (final PreparedStatement statement = Database.INSTANCE.getMainConnection().prepareStatement(job_sql))
        {
            statement.setInt(1, job_id);
            try (final ResultSet resultSet = statement.executeQuery())
            {
                if (resultSet.next())
                {
                    job_name = resultSet.getString("jobname");
                    job_comment = resultSet.getString("jobdesc");
                }
            }
        }
        catch (final SQLException e)
        {
            Config.INSTANCE.logger.error("An error occurred getting job info.");
            Config.INSTANCE.logger.error(e.getMessage());
        }

        processAnnotations();

        final String log_sql =
                "INSERT INTO pgagent.pga_joblog(jlgjobid, jlgstatus) " +
                "VALUES (?, ?) " +
                "RETURNING jlgid;";
        Config.INSTANCE.logger.debug("Inserting logging and marking job as being worked on.");
        try (final PreparedStatement log_statement = Database.INSTANCE.getMainConnection().prepareStatement(log_sql))
        {

            log_statement.setInt(1, this.job_id);
            log_statement.setString(2, JobStatus.RUNNING.getDbRepresentation());
            try (final ResultSet resultSet = log_statement.executeQuery())
            {
                while (resultSet.next())
                {
                    job_log_id = resultSet.getInt("jlgid");
                }
            }
        }
        catch (final SQLException e)
        {
            Config.INSTANCE.logger.error(e.getMessage());
        }

        buildSteps();
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
                    if (job_step.getStepStatus().equals(JobStep.StepStatus.FAIL)
                            && job_step.getOnError().equals(JobStep.OnError.FAIL))
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

        final String update_log_sql =
                "UPDATE pgagent.pga_joblog SET jlgstatus = ?, jlgduration=now() - jlgstart " +
                        "WHERE jlgid = ?;";
        final String update_job_sql =
                "UPDATE pgagent.pga_job SET jobagentid=NULL, jobnextrun=NULL " +
                        "WHERE jobid = ?;";
        try (final PreparedStatement update_job_statement = Database.INSTANCE.getMainConnection().prepareStatement(update_job_sql);
             final PreparedStatement update_log_statement = Database.INSTANCE.getMainConnection().prepareStatement(update_log_sql))
        {
            update_job_statement.setInt(1, job_id);
            update_job_statement.execute();

            update_log_statement.setString(1, this.job_status.getDbRepresentation());
            update_log_statement.setInt(2, this.job_log_id);
            update_log_statement.execute();
        }
        catch (SQLException e)
        {
            Config.INSTANCE.logger.error(e.getMessage());
        }

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

    /**
     * Build steps.
     */
    private void buildSteps()
    {
        Config.INSTANCE.logger.debug("Building steps.");
        final String step_sql =
                "SELECT jstid " +
                ", jstjobid " +
                ", jstname " +
                ", jstdesc " +
                ", jstkind " +
                ", jstcode " +
                ", jstconnstr " +
                ", jstdbname " +
                ", jstonerror " +
                "FROM pgagent.pga_jobstep " +
                "WHERE jstenabled " +
                "AND jstjobid=? " +
                "ORDER BY jstname, jstid";
        try (final PreparedStatement statement = Database.INSTANCE.getMainConnection().prepareStatement(step_sql))
        {
            statement.setInt(1, job_id);

            try (final ResultSet resultSet = statement.executeQuery())
            {
                while (resultSet.next())
                {
                    JobStep job_step = new JobStep(
                            this.job_log_id,
                            resultSet.getInt("jstjobid"),
                            this.job_name,
                            resultSet.getInt("jstid"),
                            resultSet.getString("jstname"),
                            resultSet.getString("jstdesc"),
                            JobStep.StepType.convertTo(resultSet.getString("jstkind")),
                            resultSet.getString("jstcode"),
                            resultSet.getString("jstconnstr"),
                            resultSet.getString("jstdbname"),
                            JobStep.OnError.convertTo(resultSet.getString("jstonerror"))
                    );
                    job_step_list.add(job_step);
                }
            }
        }
        catch (final SQLException e)
        {
            Config.INSTANCE.logger.error("An error occurred getting job steps.");
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
        for (Future future : future_map.values())
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
            final Future future = future_map.get(job_step);
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
        for (Future future : future_map.values())
        {
            if (!future.isDone())
            {
                future.cancel(true);
            }
        }
    }

    protected enum JobStatus
    {
        RUNNING("r"),
        FAIL("f"),
        SUCCEED("s"),
        ABORTED("d"),
        IGNORE("i");

        private final String db_representation;

        JobStatus(final String db_representation)
        {
            this.db_representation = db_representation;
        }

        public static JobStatus convertTo(final String db_string)
        {
            for (JobStatus job_status : JobStatus.values())
            {
                if (db_string.equals(job_status.db_representation))
                {
                    return job_status;
                }
            }
            return null;
        }

        public String getDbRepresentation()
        {
            return db_representation;
        }
    }

    public enum JobAnnotations implements AnnotationDefinition
    {
        JOB_TIMEOUT(Long.class),
        EMAIL_ON(String.class),
        EMAIL_SUBJECT(String.class),
        EMAIL_BODY(String.class),
        EMAIL_TO(String.class);

        final Class<?> annotation_value_type;

        JobAnnotations(final Class annotation_value_type)
        {
            this.annotation_value_type = annotation_value_type;
        }

        @Override
        public Class<?> getAnnotationValueType()
        {
            return this.annotation_value_type;
        }
    }
}
