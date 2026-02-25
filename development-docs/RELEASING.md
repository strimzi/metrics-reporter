# Releasing Metrics Reporter

This document describes how to release a new version of Metrics Reporter.

## Regular releases

### Create release branch

Before releasing a new major or minor version of Metrics Reporter, the release branch has to be created.
The release branch should be named as `release-<Major>.<Minor>.x`.
For example for release 1.2.0, the branch should be named `release-1.2.x`.
The release branch is normally created from the `main` branch.
This is normally done locally and the branch is just pushed into the GitHub repository.

When releasing a new patch version, the release branch should already exist.
You just need to cherry-pick bug fixes or add them through PRs.

### Prepare the release

For any new release - major, minor or patch - we need to prepare the release.
The release preparation includes the version.

Update the placeholder version, `1.0.0-SNAPSHOT`, to the version to be released (for example `0.2.0`) in the following files:

- client-metrics-reporter/pom.xml
- client-metrics-reporter/src/test/java/io/strimzi/kafka/metrics/prometheus/MetricsUtils.java
- pom.xml
- server-metrics-reporter/pom.xml

Review and commit the changes and push them into the repository.

The build workflow should automatically start for any new commit pushed into the release branch.

### Running the release workflow

Wait until the build workflow is (successfully) finished for the last commit in the release branch.

Then run the release workflow manually from the GitHub Actions UI:
1. Go to the **Actions** tab in the GitHub repository
2. Select the **Release** workflow from the left sidebar
3. Click **Run workflow** button
4. Fill in the required parameters:
   * **Release Version**: for example `1.2.0`

The release workflow will push the images to the registry and publish the Java artifacts to Maven Central.

> **Note for RCs:**
> 
> Release candidates are built with the same release workflow as the final releases.
> When starting the workflow, use the RC name as the release version.
> For example `1.2.0-rc1` or `1.2.0-rc2`.
> For release workflows, you should skip the suffixed build since it is not needed.

### Creating the release

After the release workflow is finished, the release has to be created:

1. Tag the right commit from the release branch with the release name (e.g. `git tag 1.2.0`) and push it to GitHub.
2. Go to **Maven Central** > **Publish** to publish the release artifacts.
3. On GitHub, create the release and attach the ZIP / TAR.GZ artifacts from Maven.

### Post release (_only for GA, not for RCs_)

In the Strimzi Metrics Reporter repo:
* Close the current GitHub [milestone](https://github.com/strimzi/metrics-reporter/milestones) and create the next one.

In the Strimzi Cluster Operator repo create a PR into main to update:
* The `strimzi-metrics-reporter.version` property in the `docker-images/artifacts/kafka-thirdparty-libs/<VERSION>/pom.xml` files.
* The `CHANGELOG.md` to state that the Strimzi Cluster Operator files have been updated to a new version.

In the Strimzi HTTP Bridge repo create a PR into main to update:
* The `strimzi-metrics-reporter.version` property in the `pom.xml` files.
* The `CHANGELOG.md` to state that the Strimzi HTTP Bridge files have been updated to a new version.

### Announcements

Announce the release on following channels:
* Mailing lists
* Slack
* Social accounts (if the release is significant enough)


