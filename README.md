# Pipeline: Input Step Plugin

Adds the Pipeline `input` step to wait for human input or approval. 
A basic Proceed or Abort option is provided in the stage view. 

The parameter entry screen can be accessed via a link at the bottom of the build console log or via link in the sidebar for a build.

Input Example with `Message Parameter`:

  input 'Proceed or Abort'


Input Example with `Custom identifier`:
Every input step has an unique identifier. It is used in the generated URL to proceed or abort.

A specific identifier could be used, for example, to mechanically respond to the input from some external process/tool.

   input id: '2', message: 'input-message'


Input Example with `Cancel Button Caption` and `OK Button Caption` (Optional):

   input message: 'input-message', cancel: 'Cancel', ok: 'OK'

Input Example with `Allowed Submitter`:
Usernames and/or external group names of those permitted to respond to the input, separated by ','. Spaces will be trimmed automatically, so "Alice, Bob, Charles" is the same as "Alice,Bob,Charles".
Note: Jenkins administrators are able to respond to the input regardless of the value of this parameter. Users with [**Job/cancel** permission](https://www.jenkins.io/doc/book/security/access-control/permissions/#job-permissions) may also respond with 'Abort' to the input.

   input message: '', submitter: 'jenkins-user, jenkins-user2'

Input Example with Parameter to store the `Approving Submitter`:

If specified, this is the name of the return value that will contain the username of the user that approves this input. The return value will be handled in a fashion similar to the parameters value.

   input message: 'input-message', submitter: 'jenkins-submitter, jenkins-submitter2', submitterParameter: 'approvers-id-to-be-stored'


Use the  [Pipeline Syntax Snippet Generator](https://www.jenkins.io/redirect/pipeline-snippet-generator) to select options for the `input` step.
For further understanding, check the [pipeline input step plugin documentation](https://www.jenkins.io/doc/pipeline/steps/pipeline-input-step/).



## Version History
Please refer to [the changelog](CHANGELOG.md)
