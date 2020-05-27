package org.jenkinsci.plugins.buildjobs;

import hudson.EnvVars;
import hudson.model.EnvironmentContributingAction;
import hudson.model.Run;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.Map;

public class EnvironmentVarSetter implements EnvironmentContributingAction {
    Map<String, String> variables ;

    public EnvironmentVarSetter(Map<String, String> variables) {
        this.variables = variables;
    }

    @CheckForNull
    @Override
    public String getIconFileName() {
        return null;
    }

    @CheckForNull
    @Override
    public String getDisplayName() {
        return null;
    }

    @CheckForNull
    @Override
    public String getUrlName() {
        return null;
    }

    @Override
    public void buildEnvironment(@Nonnull Run<?, ?> run, @Nonnull EnvVars env) {
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            env.put(entry.getKey(), entry.getValue());
        }
    }
}
