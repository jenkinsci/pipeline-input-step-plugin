/*
 * The MIT License
 *
 * Copyright (c) 2013-2014, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.workflow.support.steps.input;

import com.cloudbees.plugins.credentials.CredentialsParameterDefinition;
import com.cloudbees.plugins.credentials.CredentialsParameterValue;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.gargoylesoftware.htmlunit.ElementNotFoundException;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlElementUtil;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.google.common.base.Predicate;
import hudson.model.BooleanParameterDefinition;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Job;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.User;
import hudson.model.queue.QueueTaskFuture;


import java.io.IOException;

import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.util.Secret;
import jenkins.model.IdStrategy;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import org.jvnet.hudson.test.MockAuthorizationStrategy;

import edu.umd.cs.findbugs.annotations.Nullable;
import org.jvnet.hudson.test.recipes.LocalData;

/**
 * @author Kohsuke Kawaguchi
 */
public class InputStepTest extends Assert {
    @Rule public JenkinsRule j = new JenkinsRule();

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    /**
     * Try out a parameter.
     */
    @Test
    public void parameter() throws Exception {


        //set up dummy security real
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        // job setup
        WorkflowJob foo = j.jenkins.createProject(WorkflowJob.class, "foo");
        foo.setDefinition(new CpsFlowDefinition(StringUtils.join(Arrays.asList(
                "echo('before');",
                "def x = input message:'Do you want chocolate?', id:'Icecream', ok: 'Purchase icecream', parameters: [[$class: 'BooleanParameterDefinition', name: 'chocolate', defaultValue: false, description: 'Favorite icecream flavor']], submitter:'alice';",
                "echo(\"after: ${x}\");"),"\n"),true));


        // get the build going, and wait until workflow pauses
        QueueTaskFuture<WorkflowRun> q = foo.scheduleBuild2(0);
        WorkflowRun b = q.getStartCondition().get();
        CpsFlowExecution e = (CpsFlowExecution) b.getExecutionPromise().get();

        while (b.getAction(InputAction.class)==null) {
            e.waitForSuspension();
        }

        // make sure we are pausing at the right state that reflects what we wrote in the program
        InputAction a = b.getAction(InputAction.class);
        assertEquals(1, a.getExecutions().size());

        InputStepExecution is = a.getExecution("Icecream");
        assertEquals("Do you want chocolate?", is.getInput().getMessage());
        assertEquals(1, is.getInput().getParameters().size());
        assertEquals("alice", is.getInput().getSubmitter());

        j.assertEqualDataBoundBeans(is.getInput().getParameters().get(0), new BooleanParameterDefinition("chocolate", false, "Favorite icecream flavor"));

        // submit the input, and run workflow to the completion
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login("alice");
        HtmlPage p = wc.getPage(b, a.getUrlName());
        j.submit(p.getFormByName(is.getId()), "proceed");
        assertEquals(0, a.getExecutions().size());
        q.get();

        // make sure the valid hyperlink of the approver is created in the build index page
        HtmlAnchor pu =null;

        try {
            pu = p.getAnchorByText("alice");
        }
        catch(ElementNotFoundException ex){
            System.out.println("valid hyperlink of the approved does not appears on the build index page");
        }

        assertNotNull(pu);

        // make sure 'x' gets assigned to false

        j.assertLogContains("after: false", b);

        //make sure the approver name corresponds to the submitter
        ApproverAction action = b.getAction(ApproverAction.class);
        assertNotNull(action);
        assertEquals("alice", action.getUserId());

        DepthFirstScanner scanner = new DepthFirstScanner();

        FlowNode nodeWithInputSubmittedAction = scanner.findFirstMatch(e.getCurrentHeads(), null, new Predicate<FlowNode>() {
            @Override
            public boolean apply(@Nullable FlowNode input) {
                return input != null && input.getAction(InputSubmittedAction.class) != null;
            }
        });
        assertNotNull(nodeWithInputSubmittedAction);
        InputSubmittedAction inputSubmittedAction = nodeWithInputSubmittedAction.getAction(InputSubmittedAction.class);
        assertNotNull(inputSubmittedAction);

        assertEquals("alice", inputSubmittedAction.getApprover());
        Map<String,Object> submittedParams = inputSubmittedAction.getParameters();
        assertEquals(1, submittedParams.size());
        assertTrue(submittedParams.containsKey("chocolate"));
        assertEquals(false, submittedParams.get("chocolate"));
    }

    @Test
    @Issue("JENKINS-26363")
    public void test_cancel_run_by_input() throws Exception {
        JenkinsRule.WebClient webClient = j.createWebClient();
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().
        // Only give "alice" and "bob" basic privs. That's normally not enough to Job.CANCEL, only for the fact that "alice"
        // and "bob" are listed as the submitter.
            grant(Jenkins.READ, Job.READ).everywhere().to("alice", "bob").
        // Give "charlie" basic privs + Job.CANCEL.  That should allow user3 cancel.
            grant(Jenkins.READ, Job.READ, Job.CANCEL).everywhere().to("charlie"));

        final WorkflowJob foo = j.jenkins.createProject(WorkflowJob.class, "foo");
        foo.setDefinition(new CpsFlowDefinition("input id: 'InputX', message: 'OK?', ok: 'Yes', submitter: 'alice'", true));

        runAndAbort(webClient, foo, "alice", true);   // alice should work coz she's declared as 'submitter'
        runAndAbort(webClient, foo, "bob", false);    // bob shouldn't work coz he's not declared as 'submitter' and doesn't have Job.CANCEL privs
        runAndAbort(webClient, foo, "charlie", true); // charlie should work coz he has Job.CANCEL privs
    }

    @Test
    @Issue("SECURITY-576")
    public void needBuildPermission() throws Exception {
        JenkinsRule.WebClient webClient = j.createWebClient();
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().
                // Only give "alice" basic privs. She can not proceed since she doesn't have build permissions.
                grant(Jenkins.READ, Job.READ).everywhere().to("alice").
                // Give "bob" basic privs + Job.BUILD.  That should allow bob proceed.
                grant(Jenkins.READ, Job.READ, Job.BUILD).everywhere().to("bob"));

        final WorkflowJob foo = j.jenkins.createProject(WorkflowJob.class, "foo");
        foo.setDefinition(new CpsFlowDefinition("input id: 'InputX', message: 'OK?', ok: 'Yes'", true));

        // alice should not work coz she doesn't have Job.BUILD privs
        runAndContinue(webClient, foo, "alice", false);

        // bob should work coz he has Job.BUILD privs.
        runAndContinue(webClient, foo, "bob", true);
    }

    @Test
    @Issue("JENKINS-31425")
    public void test_submitters() throws Exception {
        JenkinsRule.WebClient webClient = j.createWebClient();
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().
                // Only give "alice" basic privs. That's normally not enough to Job.CANCEL, only for the fact that "alice"
                // is listed as the submitter.
                        grant(Jenkins.READ, Job.READ).everywhere().to("alice").
                // Only give "bob" basic privs. That's normally not enough to Job.CANCEL, only for the fact that "bob"
                // is listed as the submitter.
                        grant(Jenkins.READ, Job.READ).everywhere().to("bob").
                // Give "charlie" basic privs.  That's normally not enough to Job.CANCEL, and isn't listed as submiter.
                        grant(Jenkins.READ, Job.READ).everywhere().to("charlie").
                // Add an admin user that should be able to approve the job regardless)
                        grant(Jenkins.ADMINISTER).everywhere().to("admin"));

        final WorkflowJob foo = j.jenkins.createProject(WorkflowJob.class, "foo");
        foo.setDefinition(new CpsFlowDefinition("input id: 'InputX', message: 'OK?', ok: 'Yes', submitter: 'alice,BoB'", true));

        runAndAbort(webClient, foo, "alice", true);   // alice should work coz she's declared as 'submitter'
        assertEquals(IdStrategy.CASE_INSENSITIVE, j.jenkins.getSecurityRealm().getUserIdStrategy());
        runAndAbort(webClient, foo, "bob", true);    // bob should work coz he's declared as 'submitter'
        runAndContinue(webClient, foo, "bob", true);    // bob should work coz he's declared as 'submitter'
        runAndAbort(webClient, foo, "charlie", false); // charlie shouldn't work coz he's not declared as 'submitter' and doesn't have Job.CANCEL privs
        runAndContinue(webClient, foo, "admin", true); // admin should work because... they can do anything
    }

    @Test
    @Issue({"JENKINS-31396","JENKINS-40594"})
    public void test_submitter_parameter() throws Exception {
        //set up dummy security real
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        // job setup
        WorkflowJob foo = j.jenkins.createProject(WorkflowJob.class, "foo");
        foo.setDefinition(new CpsFlowDefinition(StringUtils.join(Arrays.asList(
                "def x = input message:'Do you want chocolate?', id:'Icecream', ok: 'Purchase icecream', submitter:'alice,bob', submitterParameter: 'approval';",
                "echo(\"after: ${x}\");"),"\n"),true));

        // get the build going, and wait until workflow pauses
        QueueTaskFuture<WorkflowRun> q = foo.scheduleBuild2(0);
        WorkflowRun b = q.getStartCondition().get();
        j.waitForMessage("Input requested", b);

        // make sure we are pausing at the right state that reflects what we wrote in the program
        InputAction a = b.getAction(InputAction.class);
        assertEquals(1, a.getExecutions().size());

        InputStepExecution is = a.getExecution("Icecream");
        assertEquals("Do you want chocolate?", is.getInput().getMessage());
        assertEquals("alice,bob", is.getInput().getSubmitter());

        // submit the input, and run workflow to the completion
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login("alice");
        HtmlPage console_page = wc.getPage(b, "console");
        assertFalse(console_page.asXml().contains("proceedEmpty"));
        HtmlPage p = wc.getPage(b, a.getUrlName());
        j.submit(p.getFormByName(is.getId()), "proceed");
        assertEquals(0, a.getExecutions().size());
        q.get();

        // make sure 'x' gets 'alice'
        j.assertLogContains("after: alice", b);
    }

    @Test
    @Issue("JENKINS-31396")
    public void test_submitter_parameter_no_submitter() throws Exception {
        //set up dummy security real
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        // job setup
        WorkflowJob foo = j.jenkins.createProject(WorkflowJob.class, "foo");
        foo.setDefinition(new CpsFlowDefinition(StringUtils.join(Arrays.asList(
                "def x = input message:'Do you want chocolate?', id:'Icecream', ok: 'Purchase icecream', submitterParameter: 'approval';",
                "echo(\"after: ${x}\");"),"\n"),true));

        // get the build going, and wait until workflow pauses
        QueueTaskFuture<WorkflowRun> q = foo.scheduleBuild2(0);
        WorkflowRun b = q.getStartCondition().get();
        j.waitForMessage("Input requested", b);

        // make sure we are pausing at the right state that reflects what we wrote in the program
        InputAction a = b.getAction(InputAction.class);
        assertEquals(1, a.getExecutions().size());

        InputStepExecution is = a.getExecution("Icecream");
        assertEquals("Do you want chocolate?", is.getInput().getMessage());

        // submit the input, and run workflow to the completion
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login("alice");
        HtmlPage p = wc.getPage(b, a.getUrlName());
        j.submit(p.getFormByName(is.getId()), "proceed");
        assertEquals(0, a.getExecutions().size());
        q.get();

        // make sure 'x' gets 'alice'
        j.assertLogContains("after: alice", b);
    }

    private void runAndAbort(JenkinsRule.WebClient webClient, WorkflowJob foo, String loginAs, boolean expectAbortOk) throws Exception {
        // get the build going, and wait until workflow pauses
        QueueTaskFuture<WorkflowRun> queueTaskFuture = foo.scheduleBuild2(0);
        WorkflowRun run = queueTaskFuture.getStartCondition().get();
        CpsFlowExecution execution = (CpsFlowExecution) run.getExecutionPromise().get();

        while (run.getAction(InputAction.class) == null) {
            execution.waitForSuspension();
        }

        webClient.login(loginAs);

        InputAction inputAction = run.getAction(InputAction.class);
        InputStepExecution is = inputAction.getExecution("InputX");
        HtmlPage p = webClient.getPage(run, inputAction.getUrlName());

        try {
            j.submit(p.getFormByName(is.getId()), "abort");
            assertEquals(0, inputAction.getExecutions().size());
            queueTaskFuture.get();

            assertTrue(expectAbortOk);
            j.assertBuildStatus(Result.ABORTED, j.waitForCompletion(run));
        } catch (Exception e) {
            assertFalse(expectAbortOk);
            j.waitForMessage("Yes or Abort", run);
            run.doStop();
            j.assertBuildStatus(Result.ABORTED, j.waitForCompletion(run));
        }
    }

    private void runAndContinue(JenkinsRule.WebClient webClient, WorkflowJob foo, String loginAs, boolean expectContinueOk) throws Exception {
        // get the build going, and wait until workflow pauses
        QueueTaskFuture<WorkflowRun> queueTaskFuture = foo.scheduleBuild2(0);
        WorkflowRun run = queueTaskFuture.getStartCondition().get();
        CpsFlowExecution execution = (CpsFlowExecution) run.getExecutionPromise().get();

        while (run.getAction(InputAction.class) == null) {
            execution.waitForSuspension();
        }

        webClient.login(loginAs);

        InputAction inputAction = run.getAction(InputAction.class);
        InputStepExecution is = inputAction.getExecution("InputX");
        HtmlPage p = webClient.getPage(run, inputAction.getUrlName());

        try {
            j.submit(p.getFormByName(is.getId()), "proceed");
            assertEquals(0, inputAction.getExecutions().size());
            queueTaskFuture.get();

            assertTrue(expectContinueOk);
            j.assertBuildStatusSuccess(j.waitForCompletion(run)); // Should be successful.
        } catch (Exception e) {
            assertFalse(expectContinueOk);
            j.waitForMessage("Yes or Abort", run);
            run.doStop();
            j.assertBuildStatus(Result.ABORTED, j.waitForCompletion(run));
        }
    }

    @Issue("JENKINS-38380")
    @Test public void timeoutAuth() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.ADMINISTER).everywhere().to("ops"));
        WorkflowJob p = j.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("timeout(time: 1, unit: 'SECONDS') {input message: 'OK?', submitter: 'ops'}", true));
        j.assertBuildStatus(Result.ABORTED, p.scheduleBuild2(0).get());
    }

    @Issue("JENKINS-47699")
    @Test
    public void userScopedCredentials() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        final User alpha = User.getById("alpha", true);
        final String alphaSecret = "correct horse battery staple";
        final String alphaId = registerUserSecret(alpha, alphaSecret);
        final User beta = User.getOrCreateByIdOrFullName("beta");
        final String betaSecret = "hello world bad password";
        final String betaId = registerUserSecret(beta, betaSecret);
        final User gamma = User.getOrCreateByIdOrFullName("gamma");
        final String gammaSecret = "proton mass decay string";
        final String gammaId = registerUserSecret(gamma, gammaSecret);
        final User delta = User.getOrCreateByIdOrFullName("delta");
        final String deltaSecret = "fundamental arithmetic theorem prover";
        final String deltaId = registerUserSecret(delta, deltaSecret);

        final WorkflowJob p = j.createProject(WorkflowJob.class);
        p.addProperty(new ParametersDefinitionProperty(
                new CredentialsParameterDefinition("deltaId", null, null, StringCredentialsImpl.class.getName(), true)
        ));
        p.setDefinition(new CpsFlowDefinition("node {\n" +
                stringCredentialsInput("AlphaCreds", "alphaId") +
                stringCredentialsInput("BetaCreds", "betaId") +
                stringCredentialsInput("GammaCreds", "gammaId") +
                "  withCredentials([\n" +
                "      string(credentialsId: 'alphaId', variable: 'alphaSecret'),\n" +
                "      string(credentialsId: 'betaId', variable: 'betaSecret'),\n" +
                "      string(credentialsId: 'gammaId', variable: 'gammaSecret'),\n" +
                "      string(credentialsId: 'deltaId', variable: 'deltaSecret')\n" +
                "  ]) {\n" +
                "    if (alphaSecret != '" + alphaSecret + "') {\n" +
                "      error 'invalid alpha credentials'\n" +
                "    }\n" +
                "    if (betaSecret != '" + betaSecret + "') {\n" +
                "      error 'invalid beta credentials'\n" +
                "    }\n" +
                "    if (gammaSecret != '" + gammaSecret + "') {\n" +
                "      error 'invalid gamma credentials'\n" +
                "    }\n" +
                "    if (deltaSecret != '" + deltaSecret + "') {\n" +
                "      error 'invalid delta credentials'\n" +
                "    }\n" +
                "  }\n" +
                "}", true));

        // schedule a parameterized build
        final QueueTaskFuture<WorkflowRun> runFuture;
        try (ACLContext ignored = ACL.as(delta)) {
            runFuture = p.scheduleBuild2(0,
                    new CauseAction(new Cause.UserIdCause()),
                    new ParametersAction(new CredentialsParameterValue("deltaId", deltaId, null))
            );
            assertNotNull(runFuture);
        }
        final JenkinsRule.WebClient wc = j.createWebClient();
        final WorkflowRun run = runFuture.waitForStart();
        final CpsFlowExecution execution = (CpsFlowExecution) run.getExecutionPromise().get();

        selectUserCredentials(wc, run, execution, alphaId, "alpha", "AlphaCreds");
        selectUserCredentials(wc, run, execution, betaId, "beta", "BetaCreds");
        selectUserCredentials(wc, run, execution, gammaId, "gamma", "GammaCreds");

        j.assertBuildStatusSuccess(runFuture);
    }

    @Issue("JENKINS-63516")
    @Test
    public void passwordParameters() throws Exception {
        WorkflowJob p = j.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition(
                "def password = input(message: 'Proceed?', id: 'MyId', parameters: [\n" +
                "  password(name: 'myPassword', defaultValue: 'mySecret', description: 'myDescription')\n" +
                "])\n" +
                "echo('Password is ' + password)", true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        while (b.getAction(InputAction.class) == null) {
            Thread.sleep(100);
        }
        InputAction action = b.getAction(InputAction.class);
        assertEquals(1, action.getExecutions().size());
        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage page = wc.getPage(b, action.getUrlName());
        j.submit(page.getFormByName(action.getExecution("MyId").getId()), "proceed");
        j.assertBuildStatusSuccess(j.waitForCompletion(b));
        j.assertLogContains("Password is mySecret", b);
    }

    @LocalData
    @Test public void serialForm() throws Exception {
        WorkflowJob p = j.jenkins.getItemByFullName("p", WorkflowJob.class);
        WorkflowRun b = p.getBuildByNumber(1);
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.getPage(new WebRequest(wc.createCrumbedUrl("job/p/1/input/9edfbbe09847e1bfee4f8d2b0abfd1c3/proceedEmpty"), HttpMethod.POST));
        j.assertBuildStatusSuccess(j.waitForCompletion(b));
    }

    private void selectUserCredentials(JenkinsRule.WebClient wc, WorkflowRun run, CpsFlowExecution execution, String credentialsId, String username, String inputId) throws Exception {
        while (run.getAction(InputAction.class) == null) {
            execution.waitForSuspension();
        }
        wc.login(username);
        final InputAction action = run.getAction(InputAction.class);
        final HtmlForm form = wc.getPage(run, action.getUrlName()).getFormByName(action.getExecution(inputId).getId());
        HtmlElementUtil.click(form.getInputByName("includeUser"));
        form.getSelectByName("_.value").setSelectedAttribute(credentialsId, true);
        j.submit(form, "proceed");
    }

    private static String registerUserSecret(User user, String value) throws IOException {
        try (ACLContext ignored = ACL.as(user)) {
            final String credentialsId = UUID.randomUUID().toString();
            CredentialsProvider.lookupStores(user).iterator().next().addCredentials(Domain.global(),
                    new StringCredentialsImpl(CredentialsScope.USER, credentialsId, null, Secret.fromString(value)));
            return credentialsId;
        }
    }

    private static String stringCredentialsInput(String id, String name) {
        return "input id: '" + id + "', message: '', parameters: [credentials(credentialType: 'org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl', defaultValue: '', description: '', name: '" + name + "', required: true)]\n";
    }

}
