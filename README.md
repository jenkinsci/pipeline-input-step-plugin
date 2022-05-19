# Pipeline: Input Step Plugin

Adds the Pipeline `input` step to wait for human input or approval. 
A basic Proceed or Abort option is provided in the stage view. 

The parameter entry screen can be accessed via a link at the bottom of the build console log or via link in the sidebar for a build.

Input Example with `Message Parameter`:

[source,groovy]
----
input 'Proceed or Abort'
----

Input Example with `Custom ID`:
Every input step has an unique ID. It is used in the generated URL to proceed or abort.

A specific ID could be used, for example, to mechanically respond to the input from some external process/tool.
[source,groovy]
----
input id: '2', message: 'input-message'
----

Input Example with `OK Button Caption`(Optional):
[source,groovy]
----
input message: 'input-message', ok: 'OK'
----

Input Example with `Allowed Submitter`:
User IDs and/or external group names of person or people permitted to respond to the input, separated by ','. Spaces will be trimmed. This means that "alice, bob, blah " is the same as "alice,bob,blah".
Note: Jenkins administrators are able to respond to the input regardless of the value of this parameter.
[source,groovy]
----
input message: '', submitter: 'jenkins-user, jenkins-user2'
----

Input Example with Parameter to store the `Approving Submitter`:

If specified, this is the name of the return value that will contain the ID of the user that approves this input. The return value will be handled in a fashion similar to the parameters value.
[source,groovy]
----
input message: 'input-message', submitter: 'jenkins-submitter, jenkins-submitter2', submitterParameter: 'approvers-id-to-be-stored'
----

Use the  [Pipeline Syntax Snippet Generator](https://www.jenkins.io/redirect/pipeline-snippet-generator) to select options for the `input` step.
For further understanding, check the [pipeline input step plugin documentation](https://www.jenkins.io/doc/pipeline/steps/pipeline-input-step/).



## Version History
Please refer to [the changelog](CHANGELOG.md)
