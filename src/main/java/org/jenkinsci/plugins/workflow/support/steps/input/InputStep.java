package org.jenkinsci.plugins.workflow.support.steps.input;

import hudson.Extension;
import hudson.Util;
import hudson.model.ParameterDefinition;
import jenkins.model.Jenkins;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.acegisecurity.Authentication;
import org.acegisecurity.GrantedAuthority;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * {@link Step} that pauses for human input.
 *
 * @author Kohsuke Kawaguchi
 */
public class InputStep extends AbstractStepImpl implements Serializable {
    private final String message;

    /**
     * Optional ID that uniquely identifies this input from all others.
     */
    private String id;

    /**
     * Optional user/group name who can approve this
     */
    private String submitter;

    /**
     * Optional user/group name who did approval (true) or not (false).
     */
    private Map<String, Boolean> submittersApprovals;

    /**
     * Optional parameter name to stored the user who responded to the input.
     */
    private String submitterParameter;

    /**
     * Either a single {@link ParameterDefinition} or a list of them.
     */
    private List<ParameterDefinition> parameters = Collections.emptyList();

    /**
     * Caption of the OK button.
     */
    private String ok;

    @DataBoundConstructor
    public InputStep(String message) {
        if (message==null)
            message = "Pipeline has paused and needs your input before proceeding";
        this.message = message;
    }

    @DataBoundSetter
    public void setId(String id) {
        this.id = capitalize(Util.fixEmpty(id));
    }

    public String getId() {
        if (id==null)
            id = capitalize(Util.getDigestOf(message));
        return id;
    }

    public String getSubmitter() {
        return submitter;
    }

    public Map<String, Boolean> getSubmittersApprovals() {
        return submittersApprovals;
    }

    @DataBoundSetter public void setSubmitter(String submitter) {
        this.submitter = Util.fixEmptyAndTrim(submitter);
        this.submittersApprovals = initSubmittersApprovals(this.submitter);
    }

    private Map<String, Boolean> initSubmittersApprovals(String submitters){
        if(submitters == null){
            return null;
        }
        String submitters_list = submitters.replaceAll("[,&|()]", " ").trim();
        if(submitters_list.isEmpty()){
            return null;
        }
        Map<String, Boolean> initApprovals = Maps.newHashMap();
        for (String u : submitters_list.split("\\s+")){
            initApprovals.put(u, false);
        }
        return initApprovals;
    }

    public String getSubmitterParameter() { return submitterParameter; }

    @DataBoundSetter public void setSubmitterParameter(String submitterParameter) {
        this.submitterParameter = Util.fixEmptyAndTrim(submitterParameter);
    }

    private String capitalize(String id) {
        if (id==null)
            return null;
        if (id.length()==0)
            throw new IllegalArgumentException();
        // a-z as the first char is reserved for InputAction
        char ch = id.charAt(0);
        if ('a'<=ch && ch<='z')
            id = ((char)(ch-'a'+'A')) + id.substring(1);
        return id;
    }

    /**
     * Caption of the OK button.
     */
    public String getOk() {
        return ok!=null ? ok : "Proceed";
    }

    @DataBoundSetter public void setOk(String ok) {
        this.ok = Util.fixEmptyAndTrim(ok);
    }

    public List<ParameterDefinition> getParameters() {
        return parameters;
    }

    @DataBoundSetter public void setParameters(List<ParameterDefinition> parameters) {
        this.parameters = parameters;
    }

    public String getMessage() {
        return message;
    }

    @Deprecated
    public boolean canSubmit() {
        Authentication a = Jenkins.getAuthentication();
        return canSettle(a);
    }

    /**
     * Checks if the given user can settle this input.
     */
    @Deprecated
    public boolean canSettle(Authentication a) {
        if (submitter==null)
            return true;
        final Set<String> submitters = Sets.newHashSet(submitter.split(","));
        if (submitters.contains(a.getName()))
            return true;
        for (GrantedAuthority ga : a.getAuthorities()) {
            if (submitters.contains(ga.getAuthority()))
                return true;
        }
        return false;
    }


    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(InputStepExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "input";
        }

        @Override
        public String getDisplayName() {
            return "Wait for interactive input";
        }
    }
}
