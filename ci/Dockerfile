# CI image for the werunplugged Ackpine fork: JDK 21 (Ackpine's toolchain) and the Android SDK packages its build
# uses. The Jenkinsfile builds it with `agent { dockerfile }`, so changes here take effect on the next pipeline run.
FROM amazoncorretto:21-al2023

# procps-ng provides the `ps` Jenkins needs to stop a build on abort or timeout.
RUN yum -y update \
    && yum -y install unzip findutils which tar gzip procps-ng \
    && yum clean all

# Same SDK location as the up_hypatia image.
ENV ANDROID_HOME=/usr/local/android-sdk \
    CMDLINE_TOOLS_URL=https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip

RUN mkdir -p "$ANDROID_HOME/cmdline-tools" \
    && cd "$ANDROID_HOME/cmdline-tools" \
    && curl -fsSL -o tools.zip "$CMDLINE_TOOLS_URL" \
    && unzip -q tools.zip \
    && rm tools.zip \
    && mv cmdline-tools latest

# compileSdk 36 and build-tools 36.1.0 (gradle/settings AckpineSettingsPlugin), compileSdk 35 for a test fixture.
RUN yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null \
    && "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" \
        "platforms;android-36" "platforms;android-35" "build-tools;36.1.0" "platform-tools"

# Jenkins runs the container as the agent's uid, which has no home directory here, so everything the build writes to
# must be writable by any user: the SDK (AGP installs missing packages on demand) and /ci, which the Jenkinsfile uses
# as the home directory for Gradle, AGP and Robolectric.
RUN chmod -R a+rwX "$ANDROID_HOME" \
    && mkdir -p /ci/gradle /ci/home \
    && chmod -R 777 /ci

CMD ["/bin/bash"]
