package org.jenkinsci.plugins.buildjobs;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.AutoCompletionCandidates;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildWrapperDescriptor;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildWrapper;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class BuildIdleJobsSetter extends SimpleBuildWrapper {
    private static final Logger LOGGER = Logger.getLogger(BuildIdleJobsSetter.class.getName());

    private String jobs;
    private int choicenumber;

    private List<ScheduledJobs> otherJobs; // 存放其他 job的。 可以自定义时间段来决定是否使用这些job

    @DataBoundConstructor
    public BuildIdleJobsSetter(String jobs, int choicenumber, List<ScheduledJobs> otherJobs) {
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
    public void setUp(Context context, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars env) throws IOException, InterruptedException {
        Map<String, String> variables = new HashMap<>();
        MakeVariablesExcutor excutor = new MakeVariablesExcutor(choicenumber, jobs, otherJobs);
        excutor.makeVariables(build, listener, variables);
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            context.env(entry.getKey(), entry.getValue());
        }
    }

    @Extension
    public static class DescriptorImpl extends BuildWrapperDescriptor {
        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return true;
        }

        @Override
        public String getDisplayName() {
            //build 和 jobs至今多个空格，2个空格，这样这个插件就能排在前面执行了
            return "Set Build  Jobs Variables";
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
