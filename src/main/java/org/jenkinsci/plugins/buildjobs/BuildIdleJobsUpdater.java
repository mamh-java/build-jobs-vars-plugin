package org.jenkinsci.plugins.buildjobs;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.AutoCompletionCandidates;
import hudson.model.BuildListener;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;


public class BuildIdleJobsUpdater extends Builder {
    private static final Logger LOGGER = Logger.getLogger(BuildIdleJobsSetter.class.getName());

    private String jobs;
    private int choicenumber;

    private List<ScheduledJobs> otherJobs; // 存放其他 job的。 可以自定义时间段来决定是否使用这些job

    @DataBoundConstructor
    public BuildIdleJobsUpdater(String jobs, int choicenumber, List<ScheduledJobs> otherJobs) {
        this.jobs = jobs;
        this.choicenumber = choicenumber;
        this.otherJobs = otherJobs;
    }

    public String getJobs() {
        return jobs;
    }

    public void setJobs(String jobs) {
        this.jobs = jobs;
    }

    public int getChoicenumber() {
        return choicenumber;
    }

    public void setChoicenumber(int choicenumber) {
        this.choicenumber = choicenumber;
    }

    public List<ScheduledJobs> getOtherJobs() {
        return otherJobs;
    }

    public void setOtherJobs(List<ScheduledJobs> otherJobs) {
        this.otherJobs = otherJobs;
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        MakeVariablesExcutor excutor = new MakeVariablesExcutor(choicenumber, jobs, otherJobs);
        Map<String, String> variables = new HashMap<>();
        excutor.makeVariables(build, listener, variables);

        build.addAction(new EnvironmentVarSetter(variables));
        return true;

    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        public DescriptorImpl() {
            load();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        public String getDisplayName() {
            return "Update Build  Jobs Variables";
        }

        /**
         * Autocompletion method
         * <p>
         * Copied from hudson.tasks.BuildTrigger.doAutoCompleteChildProjects(String value)
         *
         * @param value
         * @return
         */
        public AutoCompletionCandidates doAutoCompleteJobs(@QueryParameter String value, @AncestorInPath ItemGroup context) {
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
}
