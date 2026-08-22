<p align="center">
  <img src="TMessagesProj/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="Zgram angel-wing icon" width="160">
</p>

<h1 align="center">Zgram for Android</h1>

<p align="center"><strong>Messaging, elevated.</strong></p>

<p align="center">
  <a href="https://t.me/zgram_io"><strong>Downloads and official updates</strong></a>
  ·
  <a href="https://github.com/re4/zgram-android">Source</a>
</p>

Zgram for Android is an independent community modification of Telegram for
Android. It keeps the Telegram protocol and mobile experience while bringing
the Zgram identity and celestial obsidian-and-gold design to Android.

## Android port

- Custom Zyzz angel-wing launcher, adaptive, monochrome, account, and notification icons
- ZGRAM wordmark in onboarding, the chat list, and stories
- Built-in Zgram dark theme with gold accents and the celestial wing wallpaper
- Zgram is the first-run theme while existing saved theme choices are preserved
- Official updates shortcut in Settings linking to [@zgram_io](https://t.me/zgram_io)
- Power User Center with live bookmark, archive, and encrypted-storage totals
- Searchable command palette for navigation, interface settings, local data, and current-chat actions
- Local Bookmarks that keep searchable message snapshots after edits or source deletion
- Encrypted Local Archive with per-chat opt-in retention, edit history, deletion markers, filters, and JSON/HTML export
- Expanded Zgram Chat Tools for chat appearance, mute controls, media filters, archive controls, bookmarks, and commands
- Automated GitHub Debug APK builds with downloadable artifacts
- All standard Telegram Android chat, group, channel, call, media, and privacy features

## Private local data

Local Bookmarks and the Encrypted Local Archive are Android-native Zgram
features. Their per-account data file is encrypted with an Android Keystore
key, stays on the device, and is never synced by Zgram. Archive capture is
opt-in per chat with 7-day, 30-day, 1-year, or forever retention.

Only eligible messages observed by this device while archiving is enabled are
captured. Secret chats, self-destructing and view-once media, protected or paid
content, and service messages are excluded. Exports are decrypted only when
you explicitly create and share a JSON or HTML file.

## Build a Debug APK

You need Android Studio 2025.1.4, Android SDK 35, Android NDK
27.2.12479018, and Java 17.

1. Clone with submodules:

   ```bash
   git clone --recursive https://github.com/re4/zgram-android.git
   cd zgram-android
   ```

2. Configure your Telegram API credentials in
   `TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java`.

3. Build the development APK:

   ```bash
   ./gradlew :TMessagesProj_App:assembleAfatDebug
   ```

The APK is generated under `TMessagesProj_App/build/outputs/apk/afat/debug/`.

Before publishing, replace the bundled sample signing and service configuration
with your own keystore, Firebase configuration, package identity, API ID, and
API hash.

## Automatic builds

Every source push and pull request runs the Android Debug workflow. You can
also start it manually from GitHub Actions. Successful runs upload a
`Zgram Android Debug` APK artifact.

## Telegram platform documentation

- [Telegram API](https://core.telegram.org/api)
- [MTProto](https://core.telegram.org/mtproto)
- [Security guidelines](https://core.telegram.org/mtproto/security_guidelines)

Zgram is unofficial and is not affiliated with Telegram Messenger Inc. The
source remains subject to the licenses included in this repository.
