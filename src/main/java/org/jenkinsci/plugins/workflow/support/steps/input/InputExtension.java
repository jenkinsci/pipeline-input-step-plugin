package org.jenkinsci.plugins.workflow.support.steps.input;

import hudson.ExtensionPoint;
import hudson.model.Run;

public interface InputExtension extends ExtensionPoint {
    /**
     * Callback this when an inputStep is executing.
     * Just for notification, will ignore all exception and log them.
     * @param inputStep instance of InputStep
     * @param run Current building run instance
     */
    void notifyInput(InputStep inputStep, Run run);

    /**
     * Should be unique, for distinguish different extension.
     * @return the name of current extension instance
     */
    String getName();
}
