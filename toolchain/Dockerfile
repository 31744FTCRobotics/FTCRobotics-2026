# Headless build/deploy toolchain for the FTC Robot Controller SDK.
# JDK 21 matches the JDK bundled with current Android Studio (AGP 8.13 / Gradle 9.1 need >= 17).
FROM eclipse-temurin:21-jdk-noble

ARG CMDLINE_TOOLS_BUILD=16111833
ARG CMDLINE_TOOLS_SHA1=e025545c62a8e64c7559119566a569fb1dec5f60

# Versions required by FtcRobotController/build.common.gradle (compileSdkVersion 30)
# and AGP 8.13's default build-tools.
ARG SDK_PACKAGES="platform-tools platforms;android-30 build-tools;35.0.0"

ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    HOME=/home/builder
ENV PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}"

RUN apt-get update && \
    apt-get install -y --no-install-recommends ca-certificates curl git unzip usbutils && \
    rm -rf /var/lib/apt/lists/*

RUN curl --fail --location --retry 3 \
        "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_BUILD}_latest.zip" \
        --output /tmp/cmdline-tools.zip && \
    echo "${CMDLINE_TOOLS_SHA1}  /tmp/cmdline-tools.zip" | sha1sum --check - && \
    mkdir -p "${ANDROID_HOME}/cmdline-tools" && \
    unzip -q /tmp/cmdline-tools.zip -d "${ANDROID_HOME}/cmdline-tools" && \
    mv "${ANDROID_HOME}/cmdline-tools/cmdline-tools" "${ANDROID_HOME}/cmdline-tools/latest" && \
    rm /tmp/cmdline-tools.zip

# sdkmanager extracts binaries as owner-only executable (0744); the chmod
# lets the non-root runtime user execute adb and the build tools.
RUN yes | sdkmanager --licenses >/dev/null && \
    sdkmanager --install ${SDK_PACKAGES} && \
    rm -rf "${HOME}/.android" "${HOME}/.cache" && \
    chmod -R go=u-w "${ANDROID_HOME}"

# Containers run as the invoking host UID, so HOME must be writable by anyone.
# A named volume mounted here inherits these permissions and keeps the Gradle
# cache and adb keys between runs.
RUN mkdir -p "${HOME}" && chmod 1777 "${HOME}"

WORKDIR /work
