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

import com.gargoylesoftware.htmlunit.ElementNotFoundException;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.google.common.base.Predicate;
import hudson.model.BooleanParameterDefinition;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.queue.QueueTaskFuture;


import java.util.List;

import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
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

import org.jvnet.hudson.test.MockAuthorizationStrategy;

import javax.annotation.Nullable;

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
        foo.setDefinition(new CpsFlowDefinition("input id: 'InputX', message: 'OK?', ok: 'Yes', submitter: 'alice,bob'", true));

        runAndAbort(webClient, foo, "alice", true);   // alice should work coz she's declared as 'submitter'
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
        j.waitForMessage("input", b);

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
        j.waitForMessage("input", b);

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

}
