package org.jenkinsci.plugins.workflow.support.steps.input;

import hudson.AbortException;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.Util;
import hudson.model.FileParameterDefinition;
import hudson.model.ParameterDefinition;
import hudson.model.PasswordParameterDefinition;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.ParameterDefinition.ParameterDescriptor;
import hudson.util.FormValidation;
import hudson.util.FormValidation.Kind;
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
import jenkins.util.SystemProperties;
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
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * {@link Step} that pauses for human input.
 *
 * @author Kohsuke Kawaguchi
 */
@ExportedBean(defaultVisibility = 2)
public class InputStep extends AbstractStepImpl implements Serializable {

    private static final boolean ALLOW_POTENTIALLY_UNSAFE_IDS = SystemProperties.getBoolean(InputStep.class.getName() + ".ALLOW_UNSAFE_IDS");

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
     * Caption of the Cancel button.
     */
    private String cancel;

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
        String _id = capitalize(Util.fixEmpty(id));
        if (isIdConsideredUnsafe(_id)) {
            throw new IllegalArgumentException("InputStep id is required to be URL safe, but the provided id " + _id +" is not safe");
        }
        this.id = _id;
    }

    @Exported
    public String getId() {
        if (id==null)
            id = capitalize(Util.getDigestOf(message));
        return id;
    }

    @Exported
    public String getSubmitter() {
        return submitter;
    }

    @DataBoundSetter public void setSubmitter(String submitter) {
        this.submitter = Util.fixEmptyAndTrim(submitter);
    }

    @Exported
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
     * Caption of the Cancel button.
     */
    @Exported
    public String getCancel() {
        return cancel!=null ? cancel : Messages.abort();
    }

    @DataBoundSetter public void setCancel(String cancel) {
        this.cancel = Util.fixEmptyAndTrim(cancel);
    }

    /**
     * Caption of the OK button.
     */
    @Exported
    public String getOk() {
        return ok!=null ? ok : Messages.proceed();
    }

    @DataBoundSetter public void setOk(String ok) {
        this.ok = Util.fixEmptyAndTrim(ok);
    }

    @Exported
    public List<ParameterDefinition> getParameters() {
        return parameters;
    }

    @DataBoundSetter public void setParameters(List<ParameterDefinition> parameters) {
        this.parameters = parameters;
    }

    @Exported
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

    /**
     * check if potentialId is considered unsafe for use as an id.
     * Even if it is unsafe this returns {@code false} if  {@link #ALLOW_POTENTIALLY_UNSAFE_IDS} is {@code true}
     * @return {@code true} iff the id is unsafe and the escape hatch is not set
     */
    private boolean isIdConsideredUnsafe(String potentialId) {
        if (ALLOW_POTENTIALLY_UNSAFE_IDS) {
            /// it is still unsafe
            return false;
        }
        return !getDescriptor().doCheckId(potentialId).kind.equals(Kind.OK);
    }

    private Object readResolve() throws AbortException {
        if (isIdConsideredUnsafe(this.id)) {
            throw new AbortException("InputStep id is required to be URL safe, but the provided id " + this.id +" is not safe");
        }
        return this;
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

        /** For the pipeline syntax generator page. */
        public List<ParameterDescriptor> getParametersDescriptors() {
            // See SECURITY-2705 on why we ban FileParemeterDefinition
            return ExtensionList.lookup(ParameterDescriptor.class).stream().
                    filter(descriptor -> descriptor.clazz != FileParameterDefinition.class).
                    collect(Collectors.toList());
        }

        /**
         * checks that the id is a valid ID.
         * @param id the id to check
         */
        @Restricted(NoExternalUse.class)// jelly
        public FormValidation doCheckId(@QueryParameter String id) {
            // https://www.rfc-editor.org/rfc/rfc3986.txt
            // URLs may only contain ascii
            // and only some parts are allowed
            //      segment       = *pchar
            //      segment-nz    = 1*pchar
            //      segment-nz-nc = 1*( unreserved / pct-encoded / sub-delims / "@" )
            //                      ; non-zero-length segment without any colon ":"
            //      pchar         = unreserved / pct-encoded / sub-delims / ":" / "@"
            //      unreserved    = ALPHA / DIGIT / "-" / "." / "_" / "~"
            //      sub-delims    = "!" / "$" / "&" / "'" / "(" / ")"
            //                      / "*" / "+" / "," / ";" / "="
            
            // but we are not allowing pct-encoded here.
            // additionally "." and ".." should be rejected.
            // and as we are using html / javascript in places we disallow "'"
            // and to prevent escaping hell disallow "&"

            // as well as anything unsafe we disallow . and .. (but we can have a dot inside the string so foo.bar is ok)
            // also Jenkins dissallows ; in the request parameter so don't allow that either.
            if (id == null || id.isEmpty()) {
                // the id will be provided by a hash of the message
                return FormValidation.ok();
            }
            if (id.equals(".")) {
                return FormValidation.error("The ID is required to be URL safe and is limited to the characters a-z A-Z, the digits 0-9 and additionally the characters ':' '@' '=' '+' '$' ',' '-' '_' '.' '!' '~' '*' '(' ')'.");
            }
            if (id.equals("..")) {
                return FormValidation.error("The ID is required to be URL safe and is limited to the characters a-z A-Z, the digits 0-9 and additionally the characters ':' '@' '=' '+' '$' ',' '-' '_' '.' '!' '~' '*' '(' ')'.");
            }
            if (!id.matches("^[a-zA-Z0-9[-]._~!$()*+,:@=]+$")) { // escape the - inside another [] so it does not become a range of , - _
                return FormValidation.error("The ID is required to be URL safe and is limited to the characters a-z A-Z, the digits 0-9 and additionally the characters ':' '@' '=' '+' '$' ',' '-' '_' '.' '!' '~' '*' '(' ')'.");
            }
            return FormValidation.ok();
        }
    }

}
