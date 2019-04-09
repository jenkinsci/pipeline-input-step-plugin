package org.jenkinsci.plugins.workflow.support.steps.input;

import hudson.ExtensionPoint;
import hudson.model.Run;

public interface InputExtension extends ExtensionPoint {
    /**
     * Callback this when an inputStep is executing.
     * Just for notification, will ignore all exception and log them.
     * @param inputStep Instance of InputStep
     * @param run Current building run instance
     * @param userID Id of a user who triggered the action
     * @param notifyEvent Event type of notification
     */
    void notifyInput(InputStep inputStep, Run run, String userID, NotifyEvent notifyEvent);

    /**
     * Should be unique, for distinguish different extension.
     * @return the name of current extension instance
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }

    enum NotifyEvent {
        /** waiting action from a user */
        START,
        /** continue the pipeline */
        PROCEED,
        /** abort the waiting */
        ABORT
    }
}
