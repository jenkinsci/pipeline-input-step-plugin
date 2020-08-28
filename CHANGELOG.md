# Changelog

## 2.12

Released 2020-08-28

- Fix: Make password parameters work with the `input` step in Jenkins 2.236 and newer ([JENKINS-63516](https://issues.jenkins-ci.org/browse/JENKINS-63516))
- Improvement: Document that Jenkins administrators are always able to approve `input` steps ([JENKINS-56016](https://issues.jenkins-ci.org/browse/JENKINS-56016))
- Improvement: Migrate documentation from Wiki to GitHub ([PR 43](https://github.com/jenkinsci/pipeline-input-step-plugin/pull/43))
- Internal: Update parent POM and dependencies ([PR 38](https://github.com/jenkinsci/pipeline-input-step-plugin/pull/38), [PR 40](https://github.com/jenkinsci/pipeline-input-step-plugin/pull/40), [PR 42](https://github.com/jenkinsci/pipeline-input-step-plugin/pull/42))
- Internal: Fix flaky test ([PR 45](https://github.com/jenkinsci/pipeline-input-step-plugin/pull/45))

## 2.11

Released 2019-08-27

-   [JENKINS-47699](https://issues.jenkins-ci.org/browse/JENKINS-47699):
    Allow user-scope credentials to be used as `input` step parameters.
-   Internal: Replace uses of deprecated APIs ([PR
    37](https://github.com/jenkinsci/pipeline-input-step-plugin/pull/37))

## 2.10

Released 2019-03-18

-   Trim submitter names before making comparisons to avoid issues with
    whitespace ([PR
    30](https://github.com/jenkinsci/pipeline-input-step-plugin/pull/30))
-   Add internationalization support ([PR
    23](https://github.com/jenkinsci/pipeline-input-step-plugin/pull/23))
-   Internal: Update dependencies and fix resulting test failures so
    that the plugin's tests pass successfully when run using the PCT
    ([PR
    31](https://github.com/jenkinsci/pipeline-input-step-plugin/pull/31))

## 2.9

Released 2018-12-14

-   [JENKINS-55181](https://issues.jenkins-ci.org/browse/JENKINS-55181)
    Compare the ID of the user attempting to submit an input step
    against the list of valid submitters using the IdStrategy and
    GroupIdStrategy configured by the current security realm.
    Previously, these comparisons were always case-sensitive.

## 2.8

Released 2017-08-07

-   [Fix security issue](https://jenkins.io/security/advisory/2017-08-07/)

## 2.7

Released 2017-04-26

-   [JENKINS-43856](https://issues.jenkins-ci.org/browse/JENKINS-43856) `NoSuchMethodError`
    from Blue Ocean code after the change in 2.6.

-   [JENKINS-40594](https://issues.jenkins-ci.org/browse/JENKINS-40594) The `submitterParameter`
    option added in 2.4 did not show the correct kind of hyperlink in
    the console unless `parameters` was also specified.

## 2.6

Released 2017-04-24

-   [JENKINS-40926](https://issues.jenkins-ci.org/browse/JENKINS-40926) API
    to retrieve parameters of a particular submission. No user-visible
    change.

## 2.5

Released 2016-11-09

-   2.4 was corrupt.

## 2.4

Released 2016-11-09

**Corrupt, use 2.5 instead**

-   [JENKINS-31425](https://issues.jenkins-ci.org/browse/JENKINS-31425)
    The `submitter` parameter now accepts a comma-separated list.
-   [JENKINS-31396](https://issues.jenkins-ci.org/browse/JENKINS-31396)
    `submitterParameter` parameter added.
-   [JENKINS-38380](https://issues.jenkins-ci.org/browse/JENKINS-38380)
    Interrupting an `input` step, for example with `timeout`, did not
    generally work in a secured system.

## 2.3

Released 2016-10-21

-   [JENKINS-39168](https://issues.jenkins-ci.org/browse/JENKINS-39168)
    Exception thrown under some conditions from fix in 2.2.

## 2.2

Released 2016-10-20

-   More
    [JENKINS-37154](https://issues.jenkins-ci.org/browse/JENKINS-37154)
    tuning timeouts

## 2.1

Released 2016-08-08

-   [JENKINS-37154](https://issues.jenkins-ci.org/browse/JENKINS-37154)
    Deadlock under some conditions when aborting a build waiting in
    `input`.

## 2.0

Released 2016-04-05

-   First release under per-plugin versioning scheme. See [1.x
    changelog](https://github.com/jenkinsci/workflow-plugin/blob/82e7defa37c05c5f004f1ba01c93df61ea7868a5/CHANGES.md)
    for earlier releases.
-   Includes the `input` step formerly in [Pipeline Supporting APIs Plugin](https://plugins.jenkins.io/workflow-support/).
