package org.jenkinsci.plugins.buildjobs;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.AutoCompletionCandidates;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.Serializable;
import java.util.List;

public class ScheduledJobs extends AbstractDescribableImpl<ScheduledJobs> implements Serializable {
    private static final long serialVersionUID = 1L;

    private String jobname;
    private int startTime;
    private int endTime;


    @DataBoundConstructor
    public ScheduledJobs(String jobname, int startTime, int endTime) {
        this.jobname = jobname;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public String getJobname() {
        return jobname;
    }

    public void setJobname(String jobname) {
        this.jobname = jobname;
    }

    public int getStartTime() {
        return startTime;
    }

    public void setStartTime(int startTime) {
        this.startTime = startTime;
    }

    public int getEndTime() {
        return endTime;
    }

    public void setEndTime(int endTime) {
        this.endTime = endTime;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<ScheduledJobs> {
        @Override
        public String getDisplayName() {
            return "";
        }

        /**
         * Autocompletion method
         * <p>
         * Copied from hudson.tasks.BuildTrigger.doAutoCompleteChildProjects(String value)
         *
         * @param value
         * @return
         */
        public AutoCompletionCandidates doAutoCompleteJobname(@QueryParameter String value, @AncestorInPath ItemGroup context) {
            AutoCompletionCandidates candidates = new AutoCompletionCandidates();
            List<Job> jobs = Jenkins.get().getAllItems(Job.class);
            for (Job job : jobs) {
                String relativeName = job.getRelativeNameFrom(context);
                if (relativeName.startsWith(value)) {
                    if (job.hasPermission(Item.READ)) {
                        candidates.add(relativeName);
                    }
                }
            }
            return candidates;
        }
    }

    @Override
    public String toString() {
        return "ScheduledJobs{" +
                "jobname='" + jobname + '\'' +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                '}';
    }
}

