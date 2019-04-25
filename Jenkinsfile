/*
 * Copyright (c) Bosch Software Innovations GmbH 2019.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 */

def VERSION
def IS_SNAPSHOT_VERSION

pipeline {
    agent {
        kubernetes {
            label 'antenna-build-pod'
                        yaml """
apiVersion: v1
kind: Pod
spec:
  restartPolicy: Never
  volumes:
  - name: maven-p2
    emptyDir: {}
  containers:
  - name: maven
    image: maven:3.6.0-jdk-8
    command:
    - cat
    tty: true
    volumeMounts:
    - name: maven-p2
      mountPath: /home/jenkins/.m2
    resources:
        requests:
            memory: "4096Mi"
        limits:
            memory: "4096Mi"
"""
        }
    }
    environment {
        MAVEN_OPTS = '-Xms4G -Xmx4G'
    }
    parameters {
        choice(
            choices: ['build', 'build_and_push'],
            description: '',
            name: 'REQUESTED_ACTION')
        booleanParam(
            name: 'BUILD_WITH_ANTENNA_P2',
            defaultValue: false,
            description: '')
        choice(
            choices: ['eclipse-jarsigner:sign', ''],
            description: '',
            name: 'SIGNING_COMMAND')
        booleanParam(
            name: 'RUN_TESTS',
            defaultValue: true,
            description: '')
    }
    stages {
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // we need to know the version of our project, for that we use the maven-help-plugin
        stage('determine version') {
            steps {
                container('maven') {
                    script {
                        VERSION = sh (
                            script: 'mvn org.apache.maven.plugins:maven-help-plugin:3.1.0:evaluate -Dexpression=project.version -q -DforceStdout',
                            returnStdout: true
                        ).trim()
                        IS_SNAPSHOT_VERSION = (VERSION ==~ /.*-SNAPSHOT/)
                    }
                }
                sh "echo \"VERSION is: ${VERSION} with IS_SNAPSHOT_VERSION=${IS_SNAPSHOT_VERSION}\""

                // TODO: currently we have `/home/jenkins/.m2` as a volume, but we also bend the repository to be in the source folder
                sh 'rm -rf localRepository'
                sh 'mkdir -p localRepository'
            }
        }

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // handle the p2 dependency part (the stuff below ./antena-p2)
        // this either gets created (if flag is true) or removed
        stage('build deps for p2') {
            when {
                environment name: 'BUILD_WITH_ANTENNA_P2', value: 'true'
            }
            steps {
                sh 'rm -rf repository'
                sh 'mkdir -p repository'
                container('maven') {
                    dir ('antenna-p2') {
                        dir ('dependencies') {
                            // see: https://stackoverflow.com/questions/48327214/xtext-maven-build-fails-under-jenkins-docker/51278987
                            sh 'mvn -Dmaven.repo.local=$(readlink -f ../../localRepository) --batch-mode package'
                        }
                        sh 'mvn -Dmaven.repo.local=$(readlink -f ../localRepository) --batch-mode package'
                    }
                }
            }
        }
        stage('cleanup deps for p2') {
            when {
                environment name: 'BUILD_WITH_ANTENNA_P2', value: 'false'
            }
            steps {
                sh 'rm -rf repository'
                sh 'mkdir -p repository'
                sh 'rm -rf antenna-p2/repository_manager/target'
            }
        }

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // build antenna
        stage('build') {
            steps {
                sh 'rm -rf repository'
                sh 'mkdir -p repository'
                container('maven') {
                    // build antenna and also deploy it to an output repository
                    sh """
                      mvn -Dmaven.repo.local=\$(readlink -f localRepository) \
                          --batch-mode \
                          install -DskipTests ${params.SIGNING_COMMAND}
                      mvn -Dmaven.repo.local=\$(readlink -f localRepository) \
                          --batch-mode \
                          install -DskipTests ${params.SIGNING_COMMAND} deploy \
                          -pl "!antenna-testing,!antenna-testing/antenna-core-common-testing,!antenna-testing/antenna-frontend-stubs-testing,!antenna-testing/antenna-rule-engine-testing" \
                          -DaltDeploymentRepository=localRepositoryFolder::default::file:\$(readlink -f ./repository)
                    """
                }
            }
        }

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // run tests and try to execute antenna
        stage('test') {
            when {
                environment name: 'RUN_TESTS', value: 'true'
            }
            steps {
                container('maven') {
                    // run maven tests
                    sh '''
                      mvn -Dmaven.repo.local=$(readlink -f localRepository) \
                        --batch-mode \
                        test
                    '''

                    // test as maven plugin
                    sh '''
                      tmpdir="$(mktemp -d)"
                      locRep="$(readlink -f localRepository)"
                      cp -r example-projects/example-project example-projects/example-policies $tmpdir
                      cd $tmpdir/example-project
                      mvn -Dmaven.repo.local="$locRep" \
                        --batch-mode \
                        package
                      cd -
                    '''

                    // test as CLI tool
                    sh '''
                      tmpdir="$(mktemp -d)"
                      cp -r example-projects/example-project example-projects/example-policies $tmpdir
                      .travis/runCLI.sh $tmpdir/example-project
                      java -jar antenna-testing/antenna-frontend-stubs-testing/target/antenna-test-project-asserter.jar ExampleTestProject $tmpdir/example-project/target
                    '''
                }
                sh 'ls repository/org/eclipse/sw360/antenna/'
            }
        }

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // push generated repository to download.eclipse.org
        // depending on the version, the result gets pushed to
        //   - https://download.eclipse.org/antenna/snapshots (and all old snapshot builds will be deleted) or
        //   - https://download.eclipse.org/antenna/releases
        stage ('push repository to download.eclipse.org to antenna/snapshots') {
            when {
                expression {
                    wantsToPush = params.REQUESTED_ACTION == 'build_and_push'
                    return wantsToPush && IS_SNAPSHOT_VERSION
                }
            }
            steps {
                sh """
                  if [[ "${params.SIGNING_COMMAND}x" == "x" ]]; then
                    echo "pushing is only allowed if signing was done"
                    exit 1
                  fi
                """
                container('maven') {
                    sh 'find repository -iname \'*.jar\' -print -exec jarsigner -verify {} \\;'
                }
                sshagent ( ['projects-storage.eclipse.org-bot-ssh']) {
                    sh '''
                      ssh -o StrictHostKeyChecking=no \
                          genie.antenna@projects-storage.eclipse.org \
                          rm -rf /home/data/httpd/download.eclipse.org/antenna/snapshots
                      ssh -o StrictHostKeyChecking=no \
                          genie.antenna@projects-storage.eclipse.org \
                          mkdir -p /home/data/httpd/download.eclipse.org/antenna/snapshots
                      scp -o StrictHostKeyChecking=no \
                          -r ./repository/* \
                          genie.antenna@projects-storage.eclipse.org:/home/data/httpd/download.eclipse.org/antenna/snapshots
                    '''
                }
                sh 'Snapshot release is published at https://download.eclipse.org/antenna/snapshots'
            }
        }
        stage ('push repository to download.eclipse.org to antenna/releases') {
            when {
                expression {
                    wantsToPush = params.REQUESTED_ACTION == 'build_and_push'
                    return wantsToPush && ! IS_SNAPSHOT_VERSION
                }
            }
            steps {
                sh """
                  if [[ "${params.SIGNING_COMMAND}x" == "x" ]]; then
                    echo "pushing is only allowed if signing was done"
                    exit 1
                  fi
                """
                container('maven') {
                    sh 'find repository -iname \'*.jar\' -print -exec jarsigner -verify {} \\;'
                }
                sshagent ( ['projects-storage.eclipse.org-bot-ssh']) {
                    sh '''
                      ssh -o StrictHostKeyChecking=no \
                          genie.antenna@projects-storage.eclipse.org \
                          mkdir -p /home/data/httpd/download.eclipse.org/antenna/releases
                      scp -o StrictHostKeyChecking=no \
                          -r ./repository/* \
                          genie.antenna@projects-storage.eclipse.org:/home/data/httpd/download.eclipse.org/antenna/releases
                    '''
                }
                sh 'Release is published at https://download.eclipse.org/antenna/releases'
            }
        }
    }
}
