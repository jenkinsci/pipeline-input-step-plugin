package org.jenkinsci.plugins.workflow.support.steps.input;

import hudson.Util;
import hudson.model.ParameterDefinition;
import org.apache.commons.lang.StringUtils;

import java.util.Collections;
import java.util.List;

/**
 * Holds the information needed to query for input
 */
public class InputPromptDefinition {
    private final String message;

    private String id;

    private String submitter;

    private List<ParameterDefinition> parameters = Collections.emptyList();

    private String ok;

    public InputPromptDefinition(String message) {
        this.message = message;
    }

    public InputPromptDefinition(InputStep st) {
        this.message = st.getMessage();
        this.id = st.getId();
        this.parameters = st.getParameters();
        this.ok = st.getId();
    }

    public String getMessage() {
        return message;
    }

    // TODO pull this and InputStep version out to common function
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
     * Optional ID that uniquely identifies this input from all others.
     */
    public String getId() {
        if (id==null)
            id = capitalize(Util.getDigestOf(message));
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /**
     * Optional user/group name who can approve this.
     */
    public String getSubmitter() {
        return submitter;
    }

    public void setSubmitter(String submitter) {
        this.submitter = Util.fixEmptyAndTrim(submitter);
    }

    /**
     * Either a single {@link ParameterDefinition} or a list of them.
     */
    public List<ParameterDefinition> getParameters() {
        return parameters;
    }

    public void setParameters(List<ParameterDefinition> parameters) {
        this.parameters = parameters;
    }

    /**
     * Caption of the OK button.
     */
    public String getOk() {
        return ok;
    }

    public void setOk(String ok) {
        this.ok = Util.fixEmptyAndTrim(ok);
    }
}
