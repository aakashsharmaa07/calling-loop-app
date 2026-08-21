# Call Loop - Native Android Application

**Call Loop** is a native Android application designed for personal testing and authorized automated call retries. It allows users to repeatedly place an outgoing phone call to a single selected phone number with configurable maximum retry attempts and delay intervals between calls.

---

## 1. Requirements

* **Android Studio**: Android Studio Hedgehog (2023.1.1) or newer recommended.
* **JDK**: Java 17 or higher (embedded in Android Studio).
* **Android Device / Emulator**: Running Android 8.0 (API level 26) or higher. Physical device with active SIM card recommended for placing actual telephone calls.
* **Gradle**: Gradle 8.2 with Android Gradle Plugin (AGP) 8.2.2.

---

## 2. Project Architecture & Technologies

* **Language**: Kotlin 1.9.22
* **UI Framework**: Jetpack Compose with Material 3 Design
* **Architecture**: MVVM (Model-View-ViewModel) + Coroutines & Flow + DataStore
* **Background Service**: Android Foreground Service (`CallLoopService`) for background execution and persistent notification control
* **Telephony APIs**: Android `TelephonyManager`, `TelephonyCallback` (API 31+), `PhoneStateListener` (legacy), and `Intent.ACTION_CALL`
* **Local Persistence**: Jetpack DataStore Preferences

---

## 3. How to Open the Project in Android Studio

1. Launch **Android Studio**.
2. Click **Open** (or **File > Open**).
3. Navigate to the project directory:
   `C:\Users\aakas\OneDrive\Desktop\Calling app`
4. Click **OK** to open the project.

---

## 4. How to Sync Gradle

1. Once the project opens in Android Studio, Gradle will automatically start syncing dependencies.
2. If prompt appears or sync does not start automatically, click **Sync Project with Gradle Files** (elephant icon in the top toolbar or via **File > Sync Project with Gradle Files**).
3. Wait for the sync to complete. Ensure you are connected to the internet to download dependencies.

---

## 5. How to Run on a Physical Android Phone

1. Enable **Developer Options** on your physical Android phone:
   * Go to **Settings > About Phone**.
   * Tap **Build Number** 7 times until you see *"You are now a developer!"*.
2. Enable **USB Debugging**:
   * Go to **Settings > System > Developer Options**.
   * Turn on **USB Debugging**.
3. Connect your Android phone to your computer via USB cable.
4. Accept the **Allow USB Debugging** prompt on your phone.
5. In Android Studio, select your physical device from the target device dropdown menu next to the **Run** button.
6. Click **Run 'app'** (green play button `Shift + F10`).
7. When prompted on your phone, grant the required **Phone** (`CALL_PHONE`), **Phone State** (`READ_PHONE_STATE`), and **Call Log** (`READ_CALL_LOG`) permissions.

---

## 6. Required Permissions

The app requests only the minimum permissions necessary for automated dialing and status monitoring:

| Permission | Purpose |
| :--- | :--- |
| `CALL_PHONE` | Allows placing outgoing phone calls directly when START is pressed. |
| `READ_PHONE_STATE` | Monitors call states (Off-hook, Ringing, Idle) to track attempt execution. |
| `READ_CALL_LOG` | Determines if a disconnected attempt was actually answered vs hung up without answer. |
| `FOREGROUND_SERVICE` | Keeps the retry countdown and call loop running when app is backgrounded. |
| `FOREGROUND_SERVICE_PHONE_CALL` | Foreground service type classification on Android 14+ (API 34). |
| `POST_NOTIFICATIONS` | Shows persistent ongoing call status & instant STOP notification control on Android 13+. |

> [!NOTE]
> The app uses Android's official system Contact Picker (`ActivityResultContracts.PickContact()`). Selecting a contact from your address book does **NOT** require `READ_CONTACTS` permission.

---

## 7. How the Call Loop Works

1. **Setup**: Enter a target phone number or pick one from contacts. Set max attempts (1–20) and delay interval (5s to 10m).
2. **Start**: Tap **START CALL LOOP**. The app starts `CallLoopService` as a Foreground Service and launches attempt 1 via `Intent.ACTION_CALL`.
3. **Observation**: The app registers a telephony callback (`CallStateMonitor`) to observe call states (`OFFHOOK`, `RINGING`, `IDLE`).
4. **Answered Call Detection**: If the call connects and remains off-hook (or has positive duration in call log), the app detects **CALL ANSWERED — LOOP STOPPED** and immediately terminates the retry loop.
5. **Unanswered / Disconnected**: If the call ends without being answered, the app updates the attempt counter and initiates a live countdown (`Next call in 00:XX`).
6. **Next Attempt**: When countdown reaches 0, the next attempt is placed automatically.
7. **Termination**: The loop continues until:
   * The call is answered, OR
   * Maximum attempts are reached, OR
   * The user presses **STOP CALL LOOP**.

---

## 8. How Maximum Attempts Work

* Configurable between **1 and 20 attempts** (enforced in UI & ViewModel).
* Each actual call placed increments `Attempt X / Y`.
* When attempt `Y / Y` completes without being answered, the app displays **Maximum attempts reached** and stops without placing further calls.

---

## 9. How Delay Works

* Configurable delay options: 5s, 10s, 15s, 30s (Default), 1m, 2m, 5m, 10m.
* After a call ends without an answer, a second-by-second countdown timer runs.
* Pressing **STOP CALL LOOP** during countdown instantly cancels the timer and prevents subsequent calls.

---

## 10. How to Build the APK

### Debug APK:
In Android Studio:
1. Go to **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
2. Or run via command line (if Gradle is installed):
   ```bash
   ./gradlew assembleDebug
   ```

### Release APK:
1. Go to **Build > Generate Signed Bundle / APK...**.
2. Select **APK**, choose/create a signing key, select build variant **release**, and click **Finish**.
3. Or run via command line:
   ```bash
   ./gradlew assembleRelease
   ```

---

## 11. Location of Generated APKs

* **Debug APK**:
  `app/build/outputs/apk/debug/app-debug.apk`
* **Release APK**:
  `app/build/outputs/apk/release/app-release.apk`

---

## 12. Android Platform Telephony Limitations & Compliance

* **Default Dialer Restriction**: In Android 8.0+, detailed internal audio events (such as remote party ringing tones vs off-hook connected audio) are restricted to the OS Default Dialer app (`InCallService`).
* **Telephony State Monitoring**: Standard apps receive `CALL_STATE_OFFHOOK`, `CALL_STATE_RINGING`, and `CALL_STATE_IDLE`. Call Loop observes off-hook state transitions combined with `CallLog` duration queries to accurately detect answered calls while complying 100% with Android security and privacy guidelines.
* **No Background Dialing Bypass**: Outgoing calls trigger the native Android call interface cleanly and transparently.

---

## 13. Safety & Responsibility Note

> [!WARNING]
> **Use only for calls you are authorized to make.**
> Do not use this application for spamming, harassment, or unauthorized call flooding. Always adhere to local telecommunication laws and regulations.
