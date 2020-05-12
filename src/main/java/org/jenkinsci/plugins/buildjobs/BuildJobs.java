package org.jenkinsci.plugins.buildjobs;

import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.AbstractProject;
import hudson.model.AutoCompletionCandidates;
import hudson.model.BallColor;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.tasks.BuildWrapperDescriptor;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildWrapper;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

public class BuildJobs extends SimpleBuildWrapper {
    private static final Logger LOGGER = Logger.getLogger(BuildJobs.class.getName());

    private String jobs;
    private int choicenumber;

    private List<ScheduledJobs> otherJobs; // 存放其他 job的。 可以自定义时间段来决定是否使用这些job

    @DataBoundConstructor
    public BuildJobs(String jobs, int choicenumber, List<ScheduledJobs> otherJobs) {
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
    public void setUp(Context context, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException {
        LOGGER.info("public void setUp: " + this.jobs);
        Map<String, String> variables = new HashMap<>();
        makeVariables(build, listener, variables);
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            context.env(entry.getKey(), entry.getValue());
        }
    }

    public static class ScheduledJobs extends AbstractDescribableImpl<ScheduledJobs> implements Serializable {
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

    private void makeVariables(Run<?, ?> build, TaskListener listener, Map<String, String> variables) {

        listener.getLogger().println("the current jobs list is " + jobs + "");

        String newjobs = evaluateMacro(build, listener, jobs);

        listener.getLogger().println("the new jobs list is " + newjobs + "");

        Map<String, Integer> map = getAllJobRunningNumber(newjobs);


        listener.getLogger().println("the running jobs map  is " + map + "");

        variables.put("BUILD_ALL_JOBS", map.toString());

        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            String key = entry.getKey();
            Integer value = entry.getValue();
            if (value == 0) {
                variables.put("BUILD_IDELE_JOBS", key); // 空闲的job
            }
            if (value == -1) {
                variables.put("BUILD_DISABLE_JOBS", key);  // 禁用的job
            }
        }
    }

    private String evaluateMacro(Run<?, ?> build, TaskListener listener, String template) {
        try {
            File workspace = build.getRootDir();
            return TokenMacro.expandAll(build, new FilePath(workspace), listener, template);
        } catch (InterruptedException | IOException | MacroEvaluationException e) {
            LOGGER.info(e.getMessage());
            return null;
        }
    }

    /**
     * 获取所有job的运行状态，对同一个job，有个一个在运行就是1， 后面有个一个等待的任务就加1.
     *
     * @return
     */
    private Map<String, Integer> getAllJobRunningNumber(String jobsstr) {
        //先计算出每个job对应的队列里面的等待的个数，0表示队列中没有等待的。1表示这个job中有个一个任务在排队。
        HashMap<String, Integer> map = new HashMap<>();

        HashMap<String, Integer> queueMap = getQueueStatus(); // 获取当前队列状态。每个job在队列中的个数。

        Iterable<String> iterable = Splitter.on(',').trimResults().omitEmptyStrings().split(jobsstr);
        List<String> jobs = ImmutableSet.copyOf(Iterables.filter(iterable, Predicates.not(Predicates.isNull()))).asList();

        for (String job : jobs) {
            TopLevelItem topLevelItem = Jenkins.get().getItem(job);
            if (topLevelItem instanceof AbstractProject) {
                String name = topLevelItem.getName();
                AbstractProject abstractProject = (AbstractProject) topLevelItem;
                BallColor color = abstractProject.getIconColor();

                Integer qn = queueMap.getOrDefault(name, 0);
                if (color.getIconName().contains("anime")) { // 这里判断之后换个方式。
                    //包含anime说明是 正在运行的job
                    if (color.getIconName().contains("disabled")) {
                        map.put(name, -1);//被禁用的job
                    } else {
                        map.put(name, 1 + qn);
                    }
                } else if (color.getIconName().contains("disabled")) {
                    map.put(name, -1);//被禁用的job
                } else { //其他情况,没有运行的job的
                    if (qn > 0) { //如果队列中等待的大于0 说明有问题,这个job所在节点可能断线了
                        map.put(name, -1);
                    } else {
                        map.put(name, 0);
                    }
                }
            } // end if
        } //end for()


        return map;

    }

    private HashMap<String, Integer> getQueueStatus() {
        HashMap<String, Integer> queueMap = new HashMap<>();
        Queue.Item[] items = Jenkins.get().getQueue().getItems();
        for (Queue.Item queueItem : items) {
            if (queueItem.task instanceof AbstractProject) {
                AbstractProject abstractProject = (AbstractProject) queueItem.task;
                String name = abstractProject.getName();
                Integer orDefault = queueMap.getOrDefault(name, 0); //获取map中原有的值
                queueMap.put(name, orDefault + 1);//在原来map中的再加上1，在重新放入map中。
            }
        }
        return queueMap;
    }


//    public List getJobRunningStatus(List<String>> jobs) {
//        List statusList = new ArrayList();
//        if (jobs == null) {
//            return statusList;
//        }
//        //todo 这里需要参考python代码优化
//        Map<String, Integer> allJobRunningNumber = getAllJobRunningNumber(jobs);
//
//        TreeMap<Integer, Map> status = new TreeMap<>((o1, o2) -> o2 - o1);
//        for (int level = 0; level < jobs.size(); level++) {
//            List<String> job = jobs.get(level);
//
//            for (String jobname : job) {
//                int num = allJobRunningNumber.getOrDefault(jobname, -1);
//                if (num < 0) {
//                    continue;
//                }
//
//                Map map = status.getOrDefault(num,
//                        new TreeMap<Integer, List>((o1, o2) -> o2 - o1));
//
//                List list = (List) map.getOrDefault(level, new ArrayList<>());
//                list.add(jobname + " : " + num);
//
//                map.put(level, list);
//
//                status.put(num, map);
//            }
//        } //end for
//
//        for (Map<Integer, Map> maps : status.values()) {
//            for (Object mm : maps.values()) {
//                List list = (List) mm;
//                Collections.shuffle(list);
//                statusList.addAll(list);
//            }
//        }
//        return statusList;
//    }

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
