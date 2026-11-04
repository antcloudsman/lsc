# Android build environment: JDK 17 + Gradle 8.7 + Android SDK (Linux).
# Source stays on the host and is bind-mounted; dependencies live in the image.
FROM gradle:8.7-jdk17

ENV ANDROID_HOME=/opt/android-sdk
ENV PATH="${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools"

RUN apt-get update && apt-get install -y --no-install-recommends unzip wget \
    && mkdir -p "${ANDROID_HOME}/cmdline-tools" \
    && cd "${ANDROID_HOME}/cmdline-tools" \
    && wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip \
    && unzip -q commandlinetools-linux-*.zip && rm commandlinetools-linux-*.zip \
    && mv cmdline-tools latest \
    && rm -rf /var/lib/apt/lists/*

RUN yes | sdkmanager --licenses >/dev/null \
    && sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"

WORKDIR /src
CMD ["gradle", "assembleDebug", "--no-daemon"]
