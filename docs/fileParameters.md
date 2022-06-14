# File Parameters

Prior to `449.v77f0e8b_845c4` the `input` step allowed the specification of all parameters including `FileParametersValue`s.
This was both flawed (it caused errors persisting the build, and only worked on the controller thus required builds to run on the controller which is insecure), as well as being insecure in itself (potentially allowing overwriting of arbitrary files).

As the support is now been disabled if you were using this idiom before, you now need to update your pipelines to continue working.

## Migration

To migrate we suggest the [File Parameter plugin](https://plugins.jenkins.io/file-parameters/).

Where a pipeline was before doing something similar to the following:

```groovy
def file = input message: 'Please provide a file', parameters: [file('myFile.txt')]
node('built-in') {
    // do something with the file stored in $file
}
```

it can be changed to use the following syntax

```groovy
def fileBase64 = input message: 'Please provide a file', parameters: [base64File('file')]
node {
    withEnv(["fileBase64=$fileBase64"]) {
        sh 'echo $fileBase64 | base64 -d > myFile.txt'
        // powershell '[IO.File]::WriteAllBytes("myFile.txt", [Convert]::FromBase64String($env:fileBase64))'
    }
    // do something with the file stored in ./myFile.txt
}
```

Please see the [File Parameter plugin documentaion](https://github.com/jenkinsci/file-parameters-plugin#usage-with-input) for more details
