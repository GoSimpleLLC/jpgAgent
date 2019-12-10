package com.gosimple.jpgagent.job.step;

public class JobStepResult {
    private StepStatus step_status;
    private int step_result;
    private String step_output;

    public JobStepResult(StepStatus step_status, int step_result, String step_output) {
        this.step_status = step_status;
        this.step_result = step_result;
        this.step_output = step_output;
    }

    public StepStatus getStepStatus() {
        return step_status;
    }

    public void setStepStatus(StepStatus step_status) {
        this.step_status = step_status;
    }

    public int getStepResult() {
        return step_result;
    }

    public void setStepResult(int step_result) {
        this.step_result = step_result;
    }

    public String getStepOutput() {
        return step_output;
    }

    public void setStepOutput(String step_output) {
        this.step_output = step_output;
    }
}
