package org.jenkinsci.plugins.workflow.support.steps.input;

import hudson.ExtensionPoint;
import hudson.model.Run;
import hudson.model.TaskListener;

public interface InputExtension extends ExtensionPoint {
    /**
     * Callback this when an inputStep is executing.
     * Just for notification, will ignore all exception and log them.
     * @param inputStep Instance of InputStep
     * @param run Current building run instance
     * @param userID Id of a user who triggered the action
     * @param listener Listener could let you print some logs
     * @param inputEvent Event type of notification
     */
    void notifyInput(InputStep inputStep, Run run, String userID, TaskListener listener, InputEvent inputEvent);

    enum InputEvent {
        /** waiting action from a user */
        STARTED,
        /** continue the pipeline */
        PROCEEDED,
        /** abort the waiting */
        ABORTED
    }
}
