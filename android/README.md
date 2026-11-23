# Upright Stocks — Native Android (Kotlin)

This folder contains the native Android app. The existing web app at the repository root is left unchanged.

## 1. Open this branch in Codespaces

Open the repository, select the `kotlin-native` branch, then choose **Code → Codespaces → Create codespace on kotlin-native**.

## 2. Install the Android build tools

Run these commands in the Codespaces terminal:

```bash
sudo apt-get update
sudo apt-get install -y openjdk-17-jdk unzip wget
```

Download Gradle 8.13 and add it to this Codespace session:

```bash
cd /workspaces/upright-stocks/android
wget -q https://services.gradle.org/distributions/gradle-8.13-bin.zip
unzip -q gradle-8.13-bin.zip -d "$HOME"
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH="$HOME/gradle-8.13/bin:$PATH"
gradle --version
```

Install Android command-line tools from Google's official download page:
https://developer.android.com/studio#command-line-tools-only

After downloading the Linux command-line tools ZIP in the Codespace, place it in this folder as `commandlinetools-linux.zip`, then run:

```bash
mkdir -p "$HOME/android-sdk/cmdline-tools"
unzip -q commandlinetools-linux.zip -d "$HOME/android-sdk/cmdline-tools"
mv "$HOME/android-sdk/cmdline-tools/cmdline-tools" "$HOME/android-sdk/cmdline-tools/latest"
export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
```

## 3. Generate the Gradle wrapper and build

Still inside the `android` folder:

```bash
gradle wrapper --gradle-version 8.13
./gradlew assembleDebug
```

The APK will be created at:

```
app/build/outputs/apk/debug/app-debug.apk
```

Use the Codespaces file explorer to download that APK, then transfer it to your Android phone for installation. You do not need Android Studio or a local Android SDK.

## 4. Market-data connection

The Compose dashboard and HTTP client are included. The app deliberately does not display invented or unofficial prices. Before automatic NSE prices can appear, configure a backend that is authorized to supply NSE market data for display in an Android app.

The app expects this backend endpoint:

`GET https://YOUR-BACKEND.example.com/api/stocks`

Expected JSON:

```json
{
  "asOf": "2026-11-23T10:30:00+05:30",
  "stocks": [
    {
      "symbol": "RELIANCE",
      "company": "Reliance Industries",
      "price": 1450.25,
      "change": 12.4,
      "changePercent": 0.86
    }
  ]
}
```

Once the backend is deployed, replace the placeholder `API_BASE_URL` in `app/build.gradle.kts` with its base URL and rebuild.

**Security:** Never put the market-data provider's API key in the Android app. Store it on the backend as a server-side secret. Confirm that the provider's licence permits displaying the data in your app.
