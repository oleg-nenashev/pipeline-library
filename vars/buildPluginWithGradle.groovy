#!/usr/bin/env groovy

/**
 * Simple wrapper step for building a plugin with Gradle.
 */
def call(Map params = [:]) {
    // Faster build and reduces IO needs
    properties([
        durabilityHint('PERFORMANCE_OPTIMIZED'),
        buildDiscarder(logRotator(numToKeepStr: '5')),
    ])

    def repo = params.containsKey('repo') ? params.repo : null
    def failFast = params.containsKey('failFast') ? params.failFast : true
    def timeoutValue = params.containsKey('timeout') ? params.timeout : 60
    if(timeoutValue > 180) {
      echo "Timeout value requested was $timeoutValue, lowering to 180 to avoid Jenkins project's resource abusive consumption"
      timeoutValue = 180
    }

    boolean publishingIncrementals = false
    boolean archivedArtifacts = false
    Map tasks = [failFast: failFast]
    buildPlugin.getConfigurations(params, true).each { config ->
        String label = config.platform
        String jdk = config.jdk
        String jenkinsVersion = config.jenkins
        String javaLevel = config.javaLevel

        if ("windows".equals(label) && "true".equals(env.PIPELINE_LIBRARY_SKIP_WINDOWS)) {
            echo "Skipping ${stageIdentifier}, because `PIPELINE_LIBRARY_SKIP_WINDOWS` environment variable is set"
            return;
        }

        String stageIdentifier = "${label}-${jdk}${jenkinsVersion ? '-' + jenkinsVersion : ''}"
        
        tasks[stageIdentifier] = {
            node(label) {
                timeout(timeoutValue) {
                    // Archive artifacts once with pom declared baseline
                    boolean doArchiveArtifacts = !jenkinsVersion && !archivedArtifacts
                    if (doArchiveArtifacts) {
                        archivedArtifacts = true
                    }

                    stage("Checkout (${stageIdentifier})") {
                        infra.checkout(repo)
                    }

                    stage("Build (${stageIdentifier})") {
                        if (javaLevel != null) {
                            echo "WARNING: java.level is not supported in the  buildGradlePlugin(). This parmeter will be ignored"
                        }
                        //TODO(oleg-nenashev): Once supported by Gradle JPI Plugin, pass jenkinsVersion
                        if (jenkinsVersion != null) {
                            echo "WARNING: Jenkins version is not supported in buildGradlePlugin(). This parmeter will be ignored"
                        }
                        List<String> gradleOptions = [
                                '--no-daemon',
                                'cleanTest',
                                'build',
                        ]
                        String command = "gradlew ${gradleOptions.join(' ')}"
                        if (isUnix()) {
                            command = "./" + command
                        }
                        infra.runWithJava(command, jdk)
                    }

                    stage("Archive (${stageIdentifier})") {
                        junit testReports '**/build/test-results/**/*.xml'
                        
                        //TODO(oleg-nenashev): Add static analysis results publishing like in buildPlugin() for Maven 
                        
                        // TODO do this in a finally-block so we capture all test results even if one branch aborts early
                        if (failFast && currentBuild.result == 'UNSTABLE') {
                            error 'There were test failures; halting early'
                        }
                        if (doArchiveArtifacts) {
                            archiveArtifacts artifacts: '**/build/libs/*.hpi,**/build/libs/*.jpi', fingerprint: true
                        }
                    }
                }
            }
        }
    }

    timestamps {
        parallel(tasks)
    }
}
