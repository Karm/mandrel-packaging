package jenkins.jobs

matrixJob('mandrel-master-linux-build-matrix') {
    axes {
        labelExpression('LABEL', ['el8_aarch64', 'el8'])
        text('JDK_VERSION',
                'jdk11',
                'jdk17'
        )
    }
    displayName('Linux Build Matrix :: master')
    description('''
Linux build matrix for master branch.
    ''')
    logRotator {
        numToKeep(30)
    }
    parameters {
        choiceParam(
                'REPOSITORY',
                [
                        'https://github.com/graalvm/mandrel.git',
                        'https://github.com/jerboaa/graal.git',
                        'https://github.com/Karm/graal.git',
                        'https://github.com/zakkak/mandrel.git',
                ],
                'Mandrel repo'
        )
        choiceParam(
                'HEADS_OR_TAGS',
                [
                        'heads',
                        'tags',
                ],
                'To be used with the repository, e.g. to use a certain head or a tag.'
        )
        stringParam(
                'BRANCH_OR_TAG',
                'graal/master',
                'e.g. your PR branch or a specific tag.'
        )
        choiceParam(
                'PACKAGING_REPOSITORY',
                [
                        'https://github.com/mandrel/mandrel-packaging.git',
                        'https://github.com/Karm/mandrel-packaging.git',
                        'https://github.com/zakkak/mandrel-packaging.git'
                ],
                'Mandrel packaging scripts.'
        )
        choiceParam(
                'PACKAGING_REPOSITORY_HEADS_OR_TAGS',
                [
                        'heads',
                        'tags',
                ],
                'To be used with the repository, e.g. to use a certain head or a tag.'
        )
        stringParam(
                'PACKAGING_REPOSITORY_BRANCH_OR_TAG',
                'master',
                'e.g. master if you use heads or some tag if you use tags.'
        )
        stringParam(
                'MANDREL_VERSION_SUBSTRING',
                'master-SNAPSHOT',
                'It must not contain spaces as it is used in tarball name too.'
        )
    }
    multiscm {
        git {
            remote {
                url('${PACKAGING_REPOSITORY}')
            }
            branches('refs/${PACKAGING_REPOSITORY_HEADS_OR_TAGS}/${PACKAGING_REPOSITORY_BRANCH_OR_TAG}')
            extensions {
                localBranch('${PACKAGING_REPOSITORY_BRANCH_OR_TAG}')
            }
        }
        git {
            remote {
                url('${REPOSITORY}')
            }
            branches('refs/${HEADS_OR_TAGS}/${BRANCH_OR_TAG}')
            extensions {
                localBranch('${BRANCH_OR_TAG}')
                relativeTargetDirectory('mandrel')
            }
        }
        git {
            remote {
                url('https://github.com/graalvm/mx.git')
            }
            branches('*/master')
            extensions {
                localBranch('master')
                relativeTargetDirectory('mx')
            }
        }
    }
    triggers {
        scm('H H/2 * * *')
    }
    steps {
        shell('echo MANDREL_VERSION_SUBSTRING=${MANDREL_VERSION_SUBSTRING}')
        buildDescription(/MANDREL_VERSION_SUBSTRING=([^\s]*)/, '\\1')
        shell('''
            case $JDK_VERSION in
                jdk11)
                    export OPENJDK="openjdk-11.0.13_8";;
                jdk17)
                    export OPENJDK="jdk-17.0.1+12";;
                *)
                    echo "UNKNOWN JDK version: $JDK_VERSION"
                    exit 1
            esac
            ./jenkins/jobs/scripts/mandrel_linux_build.sh
        ''')
    }
    publishers {
        groovyPostBuild('''
            if(manager.logContains(".*MANDREL_VERSION_SUBSTRING.*-Final.*")) {
                def build = Thread.currentThread()?.executable
                build.rootBuild.keepLog(true)
                build.rootBuild.description="${build.environment.MANDREL_VERSION_SUBSTRING}"
            }
        ''', Behavior.DoNothing)
        archiveArtifacts('*.tar.gz,MANDREL.md,*.sha1,*.sha256')
        wsCleanup()
        postBuildCleanup {
            cleaner {
                psCleaner {
                    killerType('org.jenkinsci.plugins.proccleaner.PsRecursiveKiller')
                }
            }
        }
        extendedEmail {
            recipientList('karm@redhat.com,fzakkak@redhat.com')
            triggers {
                failure {
                    sendTo {
                        recipientList()
                        attachBuildLog(true)
                    }
                }
            }
        }
        downstreamParameterized {
            trigger(['mandrel-linux-quarkus-tests', 'mandrel-linux-integration-tests']) {
                condition('SUCCESS')
                parameters {
                    currentBuild()
                    matrixSubset('(MANDREL_VERSION=="master" && JDK_VERSION=="${JDK_VERSION}" && LABEL=="${LABEL}")')
                }
            }
        }
    }
}