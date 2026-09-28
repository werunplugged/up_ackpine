// CI for the werunplugged Ackpine fork.
//
// Every branch: ABI check, build and host tests.
// unplugged-develop only: publishes every library to Artifactory (unplugged-libraries) as
// ru.solrudev.ackpine:<artifact>:<upstream version>-unplugged.<build number>, e.g. 0.25.4-unplugged.12
// (ackpine-compress-android is prefixed with its Commons Compress version: 1.28.0-0.25.4-unplugged.12).
//
// Job setup: a Multibranch Pipeline for werunplugged/up_ackpine on an agent with Docker (the image is built from
// ci/Dockerfile), with access to the same Artifactory credentials as up_hypatia: artifactory-credentials,
// artifactory-contextUrl and artifactory-repoKey-libs. The repository is public, so don't enable "Discover pull
// requests from forks": those builds would run anyone's code on the agent.
pipeline {
    options {
        ansiColor('xterm')
        disableConcurrentBuilds()
        timeout(time: 90, unit: 'MINUTES')
    }
    environment {
        // The container runs as the agent's uid, which has no home directory in the image, so give Gradle, AGP and
        // Robolectric (which uses user.home) one inside the container. Nothing is shared between builds.
        HOME = '/ci/home'
        GRADLE_USER_HOME = '/ci/gradle'
        ANDROID_USER_HOME = '/ci/home/.android'
        JAVA_TOOL_OPTIONS = '-Duser.home=/ci/home'
        // Pinned so that an ANDROID_HOME set on the agent can't override the image's SDK.
        ANDROID_HOME = '/usr/local/android-sdk'
        GRADLE_ARGS = "--no-daemon --console=plain -Packpine.version.qualifier=unplugged.${env.BUILD_NUMBER} -Packpine.publishing.sign=false -Pkotlin.daemon.jvmargs=-Xmx3g"
    }
    agent {
        dockerfile {
            dir 'ci'
            label 'bs1'
            // Gradle daemon (-Xmx6g in gradle.properties) + Kotlin daemon (3g) + test workers.
            args '-m 12g --cpus=4'
        }
    }
    stages {
        stage('Build & test') {
            steps {
                // As in upstream's CI: the test fixtures are signed with the repository's debug keystore, and AGP
                // doesn't create one for a release signing config in a fresh environment.
                sh 'mkdir -p "$ANDROID_USER_HOME" && cp .github/debug.keystore "$ANDROID_USER_HOME/debug.keystore"'
                sh './gradlew $GRADLE_ARGS :checkAckpineAbi :buildAckpine test'
            }
        }
        stage('Publish to Artifactory') {
            when { branch 'unplugged-develop' }
            steps {
                script {
                    env.ACKPINE_VERSION = sh(
                        script: './gradlew $GRADLE_ARGS -q :ackpine-core:properties --property version > ackpine-version.txt && sed -n "s/^version: //p" ackpine-version.txt',
                        returnStdout: true
                    ).trim()
                    if (!env.ACKPINE_VERSION) {
                        error('Could not determine the Ackpine version')
                    }
                }
                withCredentials([
                    usernamePassword(credentialsId: 'artifactory-credentials', usernameVariable: 'ORG_GRADLE_PROJECT_unpluggedUsername', passwordVariable: 'ORG_GRADLE_PROJECT_unpluggedPassword'),
                    string(credentialsId: 'artifactory-contextUrl', variable: 'ARTIFACTORY_CONTEXT_URL'),
                    string(credentialsId: 'artifactory-repoKey-libs', variable: 'ARTIFACTORY_REPO_KEY')
                ]) {
                    // Refuse to overwrite a published version, e.g. after the job was recreated and its build numbers
                    // restarted: builds that already resolved that version would keep the old artifacts.
                    sh '''
                        set +x
                        url="${ARTIFACTORY_CONTEXT_URL%/}/$ARTIFACTORY_REPO_KEY"
                        pom="$url/ru/solrudev/ackpine/ackpine-core/$ACKPINE_VERSION/ackpine-core-$ACKPINE_VERSION.pom"
                        status=$(printf 'user = "%s:%s"\\n' "$ORG_GRADLE_PROJECT_unpluggedUsername" "$ORG_GRADLE_PROJECT_unpluggedPassword" \
                            | curl -s -K - -o /dev/null -w '%{http_code}' -I "$pom")
                        if [ "$status" != 404 ]; then
                            echo "ackpine-core $ACKPINE_VERSION already exists in Artifactory or can't be checked (HTTP $status)"
                            exit 1
                        fi
                        ./gradlew $GRADLE_ARGS -Packpine.publishing.unplugged.url="$url" publishAllPublicationsToUnpluggedRepository
                    '''
                }
                script {
                    currentBuild.description = "Published ${env.ACKPINE_VERSION}"
                }
            }
        }
    }
    post {
        always {
            junit allowEmptyResults: true, testResults: '**/build/test-results/**/TEST-*.xml'
            cleanWs notFailBuild: true
        }
    }
}
