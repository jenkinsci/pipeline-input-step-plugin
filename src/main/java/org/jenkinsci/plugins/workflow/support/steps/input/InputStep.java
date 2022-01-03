package org.jenkinsci.plugins.workflow.support.steps.input;

import hudson.Extension;
import hudson.Util;
import hudson.model.ParameterDefinition;
import hudson.model.PasswordParameterDefinition;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.Secret;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.acegisecurity.GrantedAuthority;
import org.jenkinsci.plugins.structs.describable.CustomDescribableModel;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

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
     * Optional user/group name who can approve this.
     */
    private String submitter;

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
        super(true);
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

    @DataBoundSetter public void setSubmitter(String submitter) {
        this.submitter = Util.fixEmptyAndTrim(submitter);
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
        return ok!=null ? ok : Messages.proceed();
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
        final Set<String> submitters = new HashSet<>();
        Collections.addAll(submitters, submitter.split(","));
        if (submitters.contains(a.getName()))
            return true;
        for (GrantedAuthority ga : a.getAuthorities()) {
            if (submitters.contains(ga.getAuthority()))
                return true;
        }
        return false;
    }

    @Override public StepExecution start(StepContext context) throws Exception {
        return new InputStepExecution(this, context);
    }


    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor implements CustomDescribableModel {

        @Override
        public String getFunctionName() {
            return "input";
        }

        @Override
        public String getDisplayName() {
            return Messages.wait_for_interactive_input();
        }

        @Override public Set<? extends Class<?>> getRequiredContext() {
            Set<Class<?>> context = new HashSet<>();
            Collections.addAll(context, Run.class, TaskListener.class, FlowNode.class);
            return Collections.unmodifiableSet(context);
        }

        /**
         * Compatibility hack for JENKINS-63516.
         */
        @Override
        public Map<String, Object> customInstantiate(Map<String, Object> map) {
            if (DescribableModel.of(PasswordParameterDefinition.class).getParameter("defaultValue") != null) {
                return map;
            }
            return copyMapReplacingEntry(map, "parameters", "parameters", List.class, parameters -> parameters.stream()
                    .map(parameter -> {
                        if (parameter instanceof UninstantiatedDescribable) {
                            UninstantiatedDescribable ud = (UninstantiatedDescribable) parameter;
                            if (null != ud.getSymbol() && ud.getSymbol().equals("password")) {
                                Map<String, Object> newArguments = copyMapReplacingEntry(ud.getArguments(), "defaultValue", "defaultValueAsSecret", String.class, Secret::fromString);
                                return ud.withArguments(newArguments);
                            }
                        }
                        return parameter;
                    })
                    .collect(Collectors.toList())
            );
        }

        /**
         * Compatibility hack for JENKINS-63516.
         */
        @Override
        public UninstantiatedDescribable customUninstantiate(UninstantiatedDescribable step) {
            if (DescribableModel.of(PasswordParameterDefinition.class).getParameter("defaultValue") != null) {
                return step;
            }
            Map<String, Object> newStepArgs = copyMapReplacingEntry(step.getArguments(), "parameters", "parameters", List.class, parameters -> parameters.stream()
                    .map(parameter -> {
                        if (parameter instanceof UninstantiatedDescribable) {
                            UninstantiatedDescribable ud = (UninstantiatedDescribable) parameter;
                            if (ud.getSymbol().equals("password")) {
                                Map<String, Object> newParamArgs = copyMapReplacingEntry(ud.getArguments(), "defaultValueAsSecret", "defaultValue", Secret.class, Secret::getPlainText);
                                return ud.withArguments(newParamArgs);
                            }
                        }
                        return parameter;
                    })
                    .collect(Collectors.toList())
            );
            return step.withArguments(newStepArgs);
        }

        /**
         * Copy a map, replacing the entry with the specified key if it matches the specified type.
         */
        private static <T> Map<String, Object> copyMapReplacingEntry(Map<String, ?> map, String oldKey, String newKey, Class<T> requiredValueType, Function<T, Object> replacer) {
            Map<String, Object> newMap = new TreeMap<>();
            for (Map.Entry<String, ?> entry : map.entrySet()) {
                if (entry.getKey().equals(oldKey) && requiredValueType.isInstance(entry.getValue())) {
                    newMap.put(newKey, replacer.apply(requiredValueType.cast(entry.getValue())));
                } else {
                    newMap.put(entry.getKey(), entry.getValue());
                }
            }
            return newMap;
        }
    }
}
