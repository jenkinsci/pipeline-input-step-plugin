/*
 * The MIT License
 *
 * Copyright 2025 CloudBees, Inc.
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.PrefixedOutputStream;
import org.jvnet.hudson.test.RealJenkinsRule;
import org.jvnet.hudson.test.TailLog;

public final class InputStepRestart2Test {

    @Rule public final RealJenkinsRule rr = new RealJenkinsRule();

    @Issue("JENKINS-37998")
    @Test public void nonGracefulRestart() throws Throwable {
        try (var tail = new TailLog(rr, "p", 1).withColor(PrefixedOutputStream.Color.YELLOW)) {
            rr.startJenkins();
            rr.runRemotely(r -> {
                var p = r.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("input 'paused at input'; sleep 10", true));
                var b = p.scheduleBuild2(0).waitForStart();
                r.waitForMessage("paused at input", b);
                b.getAction(InputAction.class).getExecutions().get(0).proceed(null);
                r.waitForMessage("Sleeping for 10 sec", b);
            });
            rr.stopJenkins/*Forcibly*/();
            rr.startJenkins();
            tail.waitForCompletion();
        }
        rr.runRemotely(r -> {
            var p = r.jenkins.getItemByFullName("p", WorkflowJob.class);
            var b = p.getBuildByNumber(1);
            assertThat(b.getAction(InputAction.class).getExecutions(), empty());
        });
    }

}
