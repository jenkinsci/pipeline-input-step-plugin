package org.jenkinsci.plugins.workflow.support.steps.input;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.Run;
import jenkins.model.RunAction2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionList;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

/**
 * Records the pending inputs required.
 */
public class InputAction implements RunAction2 {

    private static final Logger LOGGER = Logger.getLogger(InputAction.class.getName());

    /** JENKINS-37154: number of seconds to block in {@link #loadExecutions} before we give up */
    @SuppressWarnings("FieldMayBeFinal")
    private static /* not final */ int LOAD_EXECUTIONS_TIMEOUT = Integer.getInteger(InputAction.class.getName() + ".LOAD_EXECUTIONS_TIMEOUT", 10);

    private transient List<InputStepExecution> executions = new ArrayList<InputStepExecution>();
    @SuppressFBWarnings(value="IS2_INCONSISTENT_SYNC", justification="CopyOnWriteArrayList")
    private List<String> ids = new CopyOnWriteArrayList<String>();

    private transient Run<?,?> run;

    /** ID to InputPromptDefinition */
    private Map<String,InputPromptDefinition> inputDefinitions;

    private transient ReadWriteLock lock = new ReentrantReadWriteLock();

    @Override
    public void onAttached(Run<?, ?> r) {
        this.run = r;
    }

    public Map<String, InputPromptDefinition> getInputDefinitions() {
        try {
            lock.readLock().lock();
            if (inputDefinitions == null) {
                loadExecutions();
            }
            return inputDefinitions;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void onLoad(Run<?, ?> r) {
        this.run = r;
        try {
            lock.readLock().lock();
            if (ids == null) {
                // Loading from before JENKINS-25889 fix. Load the IDs and discard the executions, which lack state anyway.
                assert executions != null && !executions.contains(null) : executions;
                ids = new ArrayList<String>();
                for (InputStepExecution execution : executions) {
                    ids.add(execution.getId());
                }
                executions = null;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @SuppressFBWarnings(value="EC_UNRELATED_TYPES_USING_POINTER_EQUALITY", justification="WorkflowRun implements Queue.Executable")
    private void loadExecutions() {
        try {
            lock.readLock().lock(); // Might not be necessary
            if (executions == null) {
                lock.writeLock().lock();
                executions = new ArrayList<InputStepExecution>();
                try {
                    FlowExecution execution = null;
                    for (FlowExecution _execution : FlowExecutionList.get()) {
                        if (_execution.getOwner().getExecutable() == run) {
                            execution = _execution;
                            break;
                        }
                    }
                    if (execution != null) {
                        // JENKINS-37154 sometimes we must block here in order to get accurate results
                        for (StepExecution se : execution.getCurrentExecutions(true).get(LOAD_EXECUTIONS_TIMEOUT, TimeUnit.SECONDS)) {
                            if (se instanceof InputStepExecution) {
                                InputStepExecution ise = (InputStepExecution) se;
                                if (ids.contains(ise.getId())) {
                                    executions.add(ise);
                                }
                            }
                        }
                        if (executions.size() < ids.size()) {
                            LOGGER.log(Level.WARNING, "some input IDs not restored from {0}", run);
                        }
                    } else {
                        LOGGER.log(Level.WARNING, "no flow execution found for {0}", run);
                    }
                } catch (Exception x) {
                    LOGGER.log(Level.WARNING, null, x);
                }
            }
        } finally {
            lock.readLock().unlock();
            lock.writeLock().unlock();
        }
    }

    public Run<?, ?> getRun() {
        return run;
    }

    @Override
    public String getIconFileName() {
        if (ids == null || ids.isEmpty()) {
            return null;
        } else {
            return "help.png";
        }
    }

    @Override
    public String getDisplayName() {
        if (ids == null || ids.isEmpty()) {
            return null;
        } else {
            return "Paused for Input";
        }
    }

    @Override
    public String getUrlName() {
        return "input";
    }

    public void add(@Nonnull InputStepExecution step) throws IOException {
        try {
            loadExecutions();
            lock.writeLock().lock();
            this.executions.add(step);
            ids.add(step.getId());
            run.save();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public synchronized InputStepExecution getExecution(String id) {
        try {
            lock.readLock().lock();
            loadExecutions();
            for (InputStepExecution e : executions) {
                if (e.input.getId().equals(id))
                    return e;
            }
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<InputStepExecution> getExecutions() {
        try {
            lock.readLock().lock();
            loadExecutions();
            return new ArrayList<InputStepExecution>(executions);
        } finally {
            lock.writeLock().lock();
        }
    }

    /**
     * Called when {@link InputStepExecution} is completed to remove it from the active input list.
     */
    public void remove(InputStepExecution exec) throws IOException {
        try {
            loadExecutions();
            lock.writeLock().lock();
            executions.remove(exec);
            ids.remove(exec.getId());
            run.save();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Bind steps just by their ID names.
     */
    public InputStepExecution getDynamic(String token) {
        return getExecution(token);
    }
}
