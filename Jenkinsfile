// CI for the werunplugged Ackpine fork: 18 library modules, consumed by the UP Store as Maven dependencies from
// Artifactory (unplugged-libraries), so it publishes with Gradle rather than through DeploymentHelper/AndroidBuilderTest,
// which uploads a single file.
//
// The version is the upstream one from version.json plus ackpine.version.qualifier from gradle.properties, e.g.
// 0.25.4-unplugged.1. To release, increase the qualifier by hand. Only the main branch publishes: a merge to main (or
// master, the fork's default branch) publishes every library, unless that version is already in Artifactory. Every
// other branch runs the ABI check, build and tests only.
//
// Job setup: a Multibranch Pipeline for werunplugged/up_ackpine, with the artifactory-credentials credential. The
// repository is public: don't enable "Discover pull requests from forks", or anyone's code would run on the agent.
pipeline {
    agent {
        dockerfile {
            dir 'ci'
            label 'bs1'
            // Gradle daemon (-Xmx6g in gradle.properties) + Kotlin daemon (3g) + test workers.
            args '-m 12g --cpus=4'
        }
    }
    options {
        ansiColor('xterm')
        disableConcurrentBuilds()
        timeout(time: 90, unit: 'MINUTES')
    }
    environment {
        // The container runs as the agent's uid, which has no home directory in the image, so give Gradle, AGP and
        // Robolectric (which reads user.home) one inside the container. Nothing is shared between builds.
        HOME = '/ci/home'
        GRADLE_USER_HOME = '/ci/gradle'
        ANDROID_USER_HOME = '/ci/home/.android'
        JAVA_TOOL_OPTIONS = '-Duser.home=/ci/home'
        // Pinned so that an ANDROID_HOME set on the agent can't override the image's SDK.
        ANDROID_HOME = '/usr/local/android-sdk'
        UNPLUGGED_REPOSITORY_URL = 'https://unplugged.jfrog.io/artifactory/unplugged-libraries'
    }
    stages {
        stage('Resolve Version') {
            steps {
                script {
                    // A pull request builds as PR-<n>; CHANGE_BRANCH is its source branch.
                    def branch = env.CHANGE_BRANCH ?: env.BRANCH_NAME ?: ''
                    // UNP-9252 also publishes, as a regular version, until the PR reaches master.
                    def isMainBranch = (env.CHANGE_BRANCH ? false : branch in ['main', 'master']) || branch == 'UNP-9252'
                    env.PUBLISH = isMainBranch.toString()
                    env.GRADLE_ARGS = '--no-daemon --console=plain -Packpine.publishing.sign=false -Pkotlin.daemon.jvmargs=-Xmx3g'
                    echo "🌿 Branch: ${branch} | publish: ${env.PUBLISH == 'true' ? 'yes, if the version is new' : 'no, only the main branch publishes'}"
                }
            }
        }

        stage('Build & Test') {
            steps {
                // The test fixtures are signed with the repository's debug keystore, as in upstream's CI: AGP doesn't
                // create one for a release signing config in a fresh environment.
                sh 'mkdir -p "$ANDROID_USER_HOME" && cp .github/debug.keystore "$ANDROID_USER_HOME/debug.keystore"'
                sh './gradlew $GRADLE_ARGS :checkAckpineAbi :buildAckpine test'
            }
        }

        stage('Publish to Artifactory') {
            when { environment name: 'PUBLISH', value: 'true' }
            steps {
                script {
                    env.ACKPINE_VERSION = sh(
                        script: './gradlew $GRADLE_ARGS -q :ackpine-core:properties --property version | sed -n "s/^version: //p"',
                        returnStdout: true
                    ).trim()
                    if (!env.ACKPINE_VERSION) {
                        error('❌ Could not determine the Ackpine version')
                    }
                    echo "📦 Version ${env.ACKPINE_VERSION}"
                }
                withCredentials([
                    usernamePassword(credentialsId: 'artifactory-credentials', usernameVariable: 'ORG_GRADLE_PROJECT_unpluggedUsername', passwordVariable: 'ORG_GRADLE_PROJECT_unpluggedPassword')
                ]) {
                    // Never overwrite a published version: builds that already resolved it would keep the old artifacts.
                    // Most pushes don't change the version, so a version that is fully there means nothing to release.
                    // The search covers every module ("*" also matches compress-android's 1.28.0-<version>): none there
                    // publishes, all there skips, only some there (a publish that failed halfway) fails, as does a
                    // failed check. The credentials go to curl on stdin so they never appear in a process list.
                    sh '''
                        set +x
                        rm -f published.txt
                        search="https://unplugged.jfrog.io/artifactory/api/search/gavc?g=com.unplugged.ackpine&v=*$ACKPINE_VERSION&repos=unplugged-libraries"
                        status=$(printf 'user = "%s:%s"\\n' "$ORG_GRADLE_PROJECT_unpluggedUsername" "$ORG_GRADLE_PROJECT_unpluggedPassword" \
                            | curl -s -K - -o search.json -w '%{http_code}' "$search")
                        if [ "$status" != 200 ]; then
                            echo "❌ Can't check whether $ACKPINE_VERSION is already in Artifactory (HTTP $status)"
                            exit 1
                        fi
                        expected=$(grep -cE '^[[:space:]]*library[(]' build.gradle.kts)
                        found=$(grep -oE '/com/unplugged/ackpine/[^/]+/' search.json | sort -u | wc -l)
                        if [ "$found" -eq 0 ]; then
                            ./gradlew $GRADLE_ARGS -Packpine.publishing.unplugged.url="$UNPLUGGED_REPOSITORY_URL" publishAllPublicationsToUnpluggedRepository
                            touch published.txt
                        elif [ "$found" -ge "$expected" ]; then
                            echo "⏭️ $ACKPINE_VERSION is already in Artifactory. To release, increase ackpine.version.qualifier in gradle.properties."
                        else
                            echo "❌ Only $found of $expected libraries of $ACKPINE_VERSION are in Artifactory (an earlier publish failed halfway). Increase ackpine.version.qualifier and publish again."
                            exit 1
                        fi
                    '''
                }
                script {
                    env.JFROG_URL = "https://unplugged.jfrog.io/ui/repos/tree/General/unplugged-libraries/com/unplugged/ackpine/ackpine-core/${env.ACKPINE_VERSION}"
                    if (fileExists('published.txt')) {
                        env.PUBLISHED = 'true'
                        currentBuild.description = "📦 New version ${env.ACKPINE_VERSION} published to JFrog (all 18 libraries, under com/unplugged/ackpine):\n${env.JFROG_URL}"
                    } else {
                        currentBuild.description = "⏭️ ${env.ACKPINE_VERSION} is already in JFrog, nothing published. To release, increase ackpine.version.qualifier."
                    }
                }
            }
        }
    }
    post {
        always {
            junit allowEmptyResults: true, testResults: '**/build/test-results/**/TEST-*.xml'
            cleanWs notFailBuild: true
        }
        success {
            script {
                if (env.PUBLISHED == 'true') {
                    echo "📦 New version ${env.ACKPINE_VERSION} is in JFrog: ${env.JFROG_URL}"
                } else if (env.PUBLISH != 'true') {
                    currentBuild.description = '✅ Built and tested'
                }
            }
        }
    }
}
