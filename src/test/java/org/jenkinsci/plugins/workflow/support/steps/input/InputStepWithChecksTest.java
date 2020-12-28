package org.jenkinsci.plugins.workflow.support.steps.input;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.queue.QueueTaskFuture;
import io.jenkins.plugins.checks.api.ChecksConclusion;
import io.jenkins.plugins.checks.api.ChecksDetails;
import io.jenkins.plugins.checks.api.ChecksOutput;
import io.jenkins.plugins.checks.api.ChecksStatus;
import io.jenkins.plugins.checks.util.CapturingChecksPublisher;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.TestExtension;

import java.net.URI;
import java.util.List;

public class InputStepWithChecksTest extends Assert {

    private final String CHECKS_NAME = "Input Checks";
    private final String INPUT_ID = "InputChecks";
    private final String USER = "bob";

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @TestExtension
    public static final CapturingChecksPublisher.Factory PUBLISHER_FACTORY = new CapturingChecksPublisher.Factory();

    @Before
    public void setUpUsers() {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ, Job.READ, Job.BUILD).everywhere().to(USER));
    }

    @After
    public void cleanupFactory() {
        PUBLISHER_FACTORY.getPublishedChecks().clear();
    }

    private List<ChecksDetails> runAndSubmit(String script) throws Exception {
        return run(script, false);
    }

    private List<ChecksDetails> runAndAbort(String script) throws Exception {
        return run(script, true);
    }

    private List<ChecksDetails> run(String script, boolean abort) throws Exception {

        WorkflowJob job = j.createProject(WorkflowJob.class);
        job.setDefinition(new CpsFlowDefinition(script, true));

        QueueTaskFuture<WorkflowRun> q = job.scheduleBuild2(0);
        WorkflowRun run = q.getStartCondition().get();
        CpsFlowExecution e = (CpsFlowExecution) run.getExecutionPromise().get();

        while (run.getAction(InputAction.class) == null) {
            e.waitForSuspension();
        }

        List<ChecksDetails> checksDetails = PUBLISHER_FACTORY.getPublishedChecks();

        assertEquals(2, PUBLISHER_FACTORY.getPublishedChecks().size());

        ChecksDetails waiting = checksDetails.get(1);
        assertTrue(waiting.getDetailsURL().isPresent());

        String url = waiting.getDetailsURL().get();

        assertTrue(url.contains(j.getURL().getHost()));

        String inputUrl = j.getURL().toURI().relativize(new URI(url)).toString();

        JenkinsRule.WebClient c = j.createWebClient();
        c.login(USER);
        HtmlPage p = c.goTo(inputUrl);
        j.submit(p.getFormByName(INPUT_ID), abort ? "abort" : "proceed");

        q.get();
        j.assertBuildStatus(abort ? Result.ABORTED : Result.SUCCESS, j.waitForCompletion(run));

        return PUBLISHER_FACTORY.getPublishedChecks();
    }

    @Test
    public void publishChecksWithNoParameters() throws Exception {
        String script = "" +
                "withChecks('" + CHECKS_NAME + "') {\n" +
                "  input message: 'Can you hear me?', id: '" + INPUT_ID + "'\n" +
                "}";

        List<ChecksDetails> checksDetails = runAndSubmit(script);

        assertEquals(3, checksDetails.size());

        ChecksDetails started = checksDetails.get(0);
        assertTrue(started.getName().isPresent());
        assertEquals(CHECKS_NAME, started.getName().get());

        ChecksDetails waiting = checksDetails.get(1);
        assertTrue(waiting.getName().isPresent());
        assertEquals(CHECKS_NAME, waiting.getName().get());
        assertEquals(ChecksStatus.COMPLETED, waiting.getStatus());
        assertEquals(ChecksConclusion.ACTION_REQUIRED, waiting.getConclusion());
        assertTrue(waiting.getDetailsURL().isPresent());
        assertTrue(waiting.getOutput().isPresent());

        ChecksOutput waitingOutput = waiting.getOutput().get();
        assertTrue(waitingOutput.getTitle().isPresent());
        assertEquals("Input requested", waitingOutput.getTitle().get());
        assertTrue(waitingOutput.getSummary().isPresent());
        assertEquals("Can you hear me?", waitingOutput.getSummary().get());
        assertFalse(waitingOutput.getText().isPresent());

        ChecksDetails complete = checksDetails.get(2);
        assertTrue(complete.getName().isPresent());
        assertEquals(CHECKS_NAME, complete.getName().get());
        assertEquals(ChecksStatus.COMPLETED, complete.getStatus());
        assertEquals(ChecksConclusion.SUCCESS, complete.getConclusion());
        assertTrue(complete.getOutput().isPresent());

        ChecksOutput completeOutput = complete.getOutput().get();
        assertTrue(completeOutput.getTitle().isPresent());
        assertEquals("Input provided", completeOutput.getTitle().get());
        assertTrue(completeOutput.getSummary().isPresent());
        assertEquals("Approved by bob", completeOutput.getSummary().get());
        assertFalse(completeOutput.getText().isPresent());
    }

    @Test
    public void publishCheckWithParameters() throws Exception {
        String defaultValue = "A Sensible Default";
        String paramName = "STRING_PARAM";
        String script = "" +
                "withChecks('" + CHECKS_NAME + "') {\n" +
                "  input message: 'Can you hear me?',\n" +
                "    id: '" + INPUT_ID + "',\n" +
                "    parameters: [string(defaultValue: '" + defaultValue + "', name: '" + paramName + "')]\n" +
                "}";

        List<ChecksDetails> checksDetails = runAndSubmit(script);
        assertEquals(3, checksDetails.size());

        ChecksDetails waiting = checksDetails.get(1);
        assertTrue(waiting.getOutput().isPresent());

        ChecksOutput waitingOutput = waiting.getOutput().get();
        assertFalse(waitingOutput.getText().isPresent());

        ChecksDetails complete = checksDetails.get(2);
        assertTrue(complete.getOutput().isPresent());

        ChecksOutput completeOutput = complete.getOutput().get();
        assertTrue(completeOutput.getText().isPresent());
        assertEquals(String.format("%s: %s", paramName, defaultValue), completeOutput.getText().get());
    }

    @Test
    public void publishCheckWithAbort() throws Exception {
        String script = "" +
                "withChecks('" + CHECKS_NAME + "') {\n" +
                "  input message: 'Can you hear me?', id: '" + INPUT_ID + "'\n" +
                "}";

        List<ChecksDetails> checksDetails = runAndAbort(script);

        assertEquals(3, checksDetails.size());

        ChecksDetails complete = checksDetails.get(2);
        assertEquals(ChecksStatus.COMPLETED, complete.getStatus());
        assertEquals(ChecksConclusion.CANCELED, complete.getConclusion());
        assertTrue(complete.getOutput().isPresent());

        ChecksOutput completeOutput = complete.getOutput().get();
        assertTrue(completeOutput.getSummary().isPresent());
        assertEquals("occurred while executing withChecks step.", completeOutput.getSummary().get());
        assertTrue(completeOutput.getText().isPresent());
        assertEquals("Rejected by bob", completeOutput.getText().get());
    }
}
