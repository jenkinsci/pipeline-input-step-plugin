/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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

import hudson.model.Executor;
import hudson.model.Result;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.actions.PauseAction;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.BuildWatcherExtension;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;

class InputStepRestartTest {

    @SuppressWarnings("unused")
    @RegisterExtension
    private static final BuildWatcherExtension BUILD_WATCHER = new BuildWatcherExtension();

    @RegisterExtension
    private final JenkinsSessionExtension sessions = new JenkinsSessionExtension();

    @Issue("JENKINS-25889")
    @Test
    void restart() throws Throwable {
        sessions.then(j -> {
                WorkflowJob p = j.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("input 'paused'", true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                j.waitForMessage("paused", b);
        });
        sessions.then(j -> {
                WorkflowRun b = j.jenkins.getItemByFullName("p", WorkflowJob.class).getBuildByNumber(1);
                proceed(b, j);
                j.assertBuildStatusSuccess(j.waitForCompletion(b));
                sanity(b);
        });
    }
    
    private static void proceed(WorkflowRun b, JenkinsRule j) throws Exception {
        InputAction a = b.getAction(InputAction.class);
        assertNotNull(a);
        assertEquals(1, a.getExecutions().size());
        j.submit(j.createWebClient().getPage(b, a.getUrlName()).getFormByName(a.getExecutions().get(0).getId()), "proceed");
    }

    private void sanity(WorkflowRun b) throws Exception {
        List<PauseAction> pauses = new ArrayList<>();
        for (FlowNode n : new FlowGraphWalker(b.getExecution())) {
            pauses.addAll(PauseAction.getPauseActions(n));
        }
        assertEquals(1, pauses.size());
        assertFalse(pauses.get(0).isPaused());
        String xml = FileUtils.readFileToString(new File(b.getRootDir(), "build.xml"), StandardCharsets.UTF_8);
        assertFalse(xml.contains(InputStepExecution.class.getName()), xml);
    }

    @Issue("JENKINS-37154")
    @Test
    void interrupt() throws Throwable {
        sessions.then(j -> {
                WorkflowJob p = j.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("catchError {input 'paused'}", true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                j.waitForMessage("paused", b);
        });
        sessions.then(j -> {
                WorkflowRun b = j.jenkins.getItemByFullName("p", WorkflowJob.class).getBuildByNumber(1);
                assertNotNull(b);
                assertTrue(b.isBuilding());
                Executor executor = await().until(b::getExecutor, notNullValue());
                executor.interrupt();
                j.assertBuildStatus(Result.ABORTED, j.waitForCompletion(b));
                sanity(b);
        });
    }

}
