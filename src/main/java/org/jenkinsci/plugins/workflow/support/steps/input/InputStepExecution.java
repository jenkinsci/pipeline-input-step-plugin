package org.jenkinsci.plugins.workflow.support.steps.input;

import com.cloudbees.plugins.credentials.CredentialsParameterValue;
import com.cloudbees.plugins.credentials.builds.CredentialsParameterBinder;
import hudson.FilePath;
import hudson.Util;
import hudson.console.HyperlinkNote;
import hudson.model.Failure;
import hudson.model.FileParameterValue;
import hudson.model.Job;
import hudson.model.ModelObject;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.SecurityRealm;
import hudson.util.HttpResponses;
import jenkins.model.IdStrategy;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.acegisecurity.Authentication;
import org.acegisecurity.GrantedAuthority;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.support.actions.PauseAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.interceptor.RequirePOST;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.util.Timer;
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;
import org.jenkinsci.plugins.workflow.steps.StepContext;

/**
 * @author Kohsuke Kawaguchi
 */
public class InputStepExecution extends AbstractStepExecutionImpl implements ModelObject {

    private static final Logger LOGGER = Logger.getLogger(InputStepExecution.class.getName());

    /**
     * Result of the input.
     */
    private Outcome outcome;

    final InputStep input;

    InputStepExecution(InputStep input, StepContext context) {
        super(context);
        this.input = input;
    }

    @Override
    public boolean start() throws Exception {
        Run<?, ?> run = getRun();
        TaskListener listener = getListener();
        FlowNode node = getNode();

        // record this input
        getPauseAction().add(this);

        // This node causes the flow to pause at this point so we mark it as a "Pause Node".
        node.addAction(new PauseAction("Input"));

        String baseUrl = '/' + run.getUrl() + getPauseAction().getUrlName() + '/';
        //JENKINS-40594 submitterParameter does not work without at least one actual parameter
        if (input.getParameters().isEmpty() && input.getSubmitterParameter() == null) {
            String thisUrl = baseUrl + Util.rawEncode(getId()) + '/';
            listener.getLogger().printf("%s%n%s or %s%n", input.getMessage(),
                    POSTHyperlinkNote.encodeTo(thisUrl + "proceedEmpty", input.getOk()),
                    POSTHyperlinkNote.encodeTo(thisUrl + "abort", "Abort"));
        } else {
            // TODO listener.hyperlink(…) does not work; why?
            // TODO would be even cooler to embed the parameter form right in the build log (hiding it after submission)
            listener.getLogger().println(HyperlinkNote.encodeTo(baseUrl, "Input requested"));
        }
        return false;
    }

    @Override
    public void stop(Throwable cause) throws Exception {
        // JENKINS-37154: we might be inside the VM thread, so do not do anything which might block on the VM thread
        Timer.get().submit(new Runnable() {
            @Override public void run() {
                try (ACLContext context = ACL.as(ACL.SYSTEM)) {
                    doAbort();
                } catch (IOException | InterruptedException x) {
                    LOGGER.log(Level.WARNING, "failed to abort " + getContext(), x);
                }
            }
        });
    }

    public String getId() {
        return input.getId();
    }

    public InputStep getInput() {
        return input;
    }

    public Run<?, ?> getRun() throws IOException, InterruptedException {
        return getContext().get(Run.class);
    }

    private FlowNode getNode() throws InterruptedException, IOException {
        return getContext().get(FlowNode.class);
    }

    private TaskListener getListener() throws IOException, InterruptedException {
        return getContext().get(TaskListener.class);
    }

    /**
     * If this input step has been decided one way or the other.
     */
    public boolean isSettled() {
        return outcome!=null;
    }

    /**
     * Gets the {@link InputAction} that this step should be attached to.
     */
    private InputAction getPauseAction() throws IOException, InterruptedException {
        Run<?, ?> run = getRun();
        InputAction a = run.getAction(InputAction.class);
        if (a==null)
            run.addAction(a=new InputAction());
        return a;
    }

    @Override
    public String getDisplayName() {
        String message = getInput().getMessage();
        if (message.length()<32)    return message;
        return message.substring(0,32)+"...";
    }


    /**
     * Called from the form via browser to submit/abort this input step.
     */
    @RequirePOST
    public HttpResponse doSubmit(StaplerRequest request) throws IOException, ServletException, InterruptedException {
        if (request.getParameter("proceed")!=null) {
            doProceed(request);
        } else {
            doAbort();
        }

        // go back to the Run console page
        return HttpResponses.redirectTo("../../console");
    }

    /**
     * REST endpoint to submit the input.
     */
    @RequirePOST
    public HttpResponse doProceed(StaplerRequest request) throws IOException, ServletException, InterruptedException {
        preSubmissionCheck();
        Map<String,Object> v = parseValue(request);
        return proceed(v);
    }

    /**
     * Processes the acceptance (approval) request.
     * This method is used by both {@link #doProceedEmpty()} and {@link #doProceed(StaplerRequest)}
     *
     * @param params A map that represents the parameters sent in the request
     * @return A HttpResponse object that represents Status code (200) indicating the request succeeded normally.
     */
    public HttpResponse proceed(@CheckForNull Map<String,Object> params) throws IOException, InterruptedException {
        User user = User.current();
        String approverId = null;
        if (user != null){
            approverId = user.getId();
            getRun().addAction(new ApproverAction(approverId));
            getListener().getLogger().println("Approved by " + hudson.console.ModelHyperlinkNote.encodeTo(user));
        }
        getNode().addAction(new InputSubmittedAction(approverId, params));

        Object v;
        if (params != null && params.size() == 1) {
            v = params.values().iterator().next();
        } else {
            v = params;
        }
        outcome = new Outcome(v, null);
        postSettlement();
        getContext().onSuccess(v);

        return HttpResponses.ok();
    }

    @Deprecated
    @SuppressWarnings("unchecked")
    public HttpResponse proceed(Object v) throws IOException, InterruptedException {
        if (v instanceof Map) {
            return proceed(new HashMap<String,Object>((Map) v));
        } else if (v == null) {
            return proceed(null);
        } else {
            return proceed(Collections.singletonMap("parameter", v));
        }
    }

    /**
     * Used from the Proceed hyperlink when no parameters are defined.
     */
    @RequirePOST
    public HttpResponse doProceedEmpty() throws IOException, InterruptedException {
        preSubmissionCheck();

        return proceed(null);
    }

    /**
     * REST endpoint to abort the workflow.
     */
    @RequirePOST
    public HttpResponse doAbort() throws IOException, InterruptedException {
        preAbortCheck();

        FlowInterruptedException e = new FlowInterruptedException(Result.ABORTED, new Rejection(User.current()));
        outcome = new Outcome(null,e);
        postSettlement();
        getContext().onFailure(e);

        // TODO: record this decision to FlowNode

        return HttpResponses.ok();
    }

    /**
     * Check if the current user can abort/cancel the run from the input.
     */
    private void preAbortCheck() throws IOException, InterruptedException {
        if (isSettled()) {
            throw new Failure("This input has been already given");
        } if (!canCancel() && !canSubmit()) {
            if (input.getSubmitter() != null) {
                throw new Failure("You need to be '" + input.getSubmitter() + "' (or have Job/Cancel permissions) to cancel this.");
            } else {
                throw new Failure("You need to have Job/Cancel permissions to cancel this.");
            }
        }
    }

    /**
     * Check if the current user can submit the input.
     */
    public void preSubmissionCheck() throws IOException, InterruptedException {
        if (isSettled())
            throw new Failure("This input has been already given");
        if (!canSubmit()) {
            if (input.getSubmitter() != null) {
                throw new Failure("You need to be " + input.getSubmitter() + " to submit this.");
            } else {
                throw new Failure("You need to have Job/Build permissions to submit this.");
            }
        }
    }

    private void postSettlement() throws IOException, InterruptedException {
        try {
            getPauseAction().remove(this);
            getRun().save();
        } catch (IOException | InterruptedException | TimeoutException x) {
            LOGGER.log(Level.WARNING, "failed to remove InputAction from " + getContext(), x);
        } finally {
            FlowNode node = getNode();
            if (node != null) {
                try {
                    PauseAction.endCurrentPause(node);
                } catch (IOException x) {
                    LOGGER.log(Level.WARNING, "failed to end PauseAction in " + getContext(), x);
                }
            } else {
                LOGGER.log(Level.WARNING, "cannot set pause end time for {0} in {1}", new Object[] {getId(), getContext()});
            }
        }
    }

    private boolean canCancel() throws IOException, InterruptedException {
        return !Jenkins.get().isUseSecurity() || getRun().getParent().hasPermission(Job.CANCEL);
    }

    private boolean canSubmit() throws IOException, InterruptedException {
        Authentication a = Jenkins.getAuthentication();
        return canSettle(a);
    }

    /**
     * Checks if the given user can settle this input.
     */
    private boolean canSettle(Authentication a) throws IOException, InterruptedException {
        String submitter = input.getSubmitter();
        if (submitter==null)
            return getRun().getParent().hasPermission(Job.BUILD);
        if (!Jenkins.get().isUseSecurity() || Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
            return true;
        }
        final Set<String> submitters = new HashSet<>();
        Collections.addAll(submitters, submitter.split(","));
        final SecurityRealm securityRealm = Jenkins.get().getSecurityRealm();
        if (isMemberOf(a.getName(), submitters, securityRealm.getUserIdStrategy()))
            return true;
        for (GrantedAuthority ga : a.getAuthorities()) {
            if (isMemberOf(ga.getAuthority(), submitters, securityRealm.getGroupIdStrategy()))
                return true;
        }
        return false;
    }

    /**
     * Checks if the provided userId is contained in the submitters list, using {@link SecurityRealm#getUserIdStrategy()} comparison algorithm.
     * Main goal is to respect here the case sensitivity settings of the current security realm
     * (which default behavior is case insensitivity).
     *
     * @param userId the id of the user if it is matching one of the submitters using {@link IdStrategy#equals(String, String)}
     * @param submitters the list of authorized submitters
     * @param idStrategy the idStrategy impl to use for comparison
     * @return true is userId was found in submitters, false if not.
     *
     * @see {@link jenkins.model.IdStrategy#CASE_INSENSITIVE}.
     */
    private boolean isMemberOf(String userId, Set<String> submitters, IdStrategy idStrategy) {
        for (String submitter : submitters) {
            if (idStrategy.equals(userId, StringUtils.trim(submitter))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parse the submitted {@link ParameterValue}s
     */
    private Map<String,Object> parseValue(StaplerRequest request) throws ServletException, IOException, InterruptedException {
        Map<String, Object> mapResult = new HashMap<String, Object>();
        List<ParameterDefinition> defs = input.getParameters();
        Set<ParameterValue> vals = new HashSet<>(defs.size());

        Object params = request.getSubmittedForm().get("parameter");
        if (params!=null) {
            for (Object o : JSONArray.fromObject(params)) {
                JSONObject jo = (JSONObject) o;
                String name = jo.getString("name");

                ParameterDefinition d=null;
                for (ParameterDefinition def : defs) {
                    if (def.getName().equals(name))
                        d = def;
                }
                if (d == null)
                    throw new IllegalArgumentException("No such parameter definition: " + name);

                ParameterValue v = d.createValue(request, jo);
                if (v == null) {
                    continue;
                }
                vals.add(v);
                mapResult.put(name, convert(name, v));
            }
        }

        Run<?, ?> run = getRun();
        CredentialsParameterBinder binder = CredentialsParameterBinder.getOrCreate(run);
        String userId = Jenkins.getAuthentication().getName();
        for (ParameterValue val : vals) {
            if (val instanceof CredentialsParameterValue) {
                binder.bindCredentialsParameter(userId, (CredentialsParameterValue) val);
            }
        }
        run.replaceAction(binder);

        // If a destination value is specified, push the submitter to it.
        String valueName = input.getSubmitterParameter();
        if (valueName != null && !valueName.isEmpty()) {
            mapResult.put(valueName, userId);
        }

        if (mapResult.isEmpty()) {
            return null;
        } else {
            return mapResult;
        }
    }

    private Object convert(String name, ParameterValue v) throws IOException, InterruptedException {
        if (v instanceof FileParameterValue) {
            FileParameterValue fv = (FileParameterValue) v;
            FilePath fp = new FilePath(getRun().getRootDir()).child(name);
            fp.copyFrom(fv.getFile());
            return fp;
        } else {
            return v.getValue();
        }
    }

    private static final long serialVersionUID = 1L;
}
