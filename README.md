# RenVault

An open-source password and passkey manager for Android, Windows, Linux and macOS.

RenVault works in one of two modes:

- **iCloud Keychain** – sign in with your Apple Account and RenVault reads and writes the same passwords and passkeys you use on iPhone, iPad and Mac.
- **Local vault** – no account and no server. The vault lives on your devices and syncs directly between the ones you pair with a QR code.

## Features

- Passwords, Wi-Fi networks and passkeys, with search, notes and recently deleted items
- Autofill on Android, including inline suggestions in the keyboard
- Passkeys:
  - Android 14+: create and use passkeys in Chrome and in apps
  - Use your phone as a passkey for any computer (QR code + Bluetooth)
  - Linux: a virtual security key that Chrome and Firefox see as a plugged-in USB key
  - Windows 11 24H2 and newer: RenVault as a passkey provider in Windows (experimental)
- Unlock with fingerprint, face, Windows Hello or the device PIN
- Encrypted `.renvault` backups and CSV import
- Checks whether a password appeared in a known data breach
- Material 3 Expressive design, light and dark theme

## Download

Builds are published on the [Releases](https://github.com/Laky-64/RenVault/releases) page:

| Platform | Package |
| --- | --- |
| Android 7.0+ | `.apk` |
| Windows | `.msi` |
| Linux | `.deb`, `.rpm`, `.tar.gz` |
| macOS (Apple Silicon) | `.dmg` |

RenVault is in beta. Keep a backup of your vault.

## Building

Requirements: JDK 25, Go (version in `core/go.mod`) and the Android SDK.

Set the Go installation in `local.properties`:

```
go.dir=/path/to/go
```

Then:

- Android: `./gradlew :androidApp:assembleDebug`
- Desktop: `./gradlew :desktopApp:run -Drenvault.main=io.lauren.renvault.VaultMainKt`
- Desktop packages: `./gradlew :desktopApp:packageDistributionForCurrentOS`

## Project layout

- `core/` – the Go core: vault, encryption, iCloud Keychain, sync, CTAP2/passkeys
- `shared/` – the Compose Multiplatform app shared by every platform
- `androidApp/` – Android entry point, autofill and Credential Manager services
- `desktopApp/` – desktop entry point and packaging

## Privacy

RenVault has no servers and collects no data. In iCloud mode it talks to Apple; signing in also uses an "anisette" server, which you can choose in the settings. In local mode nothing leaves your devices except the encrypted sync between them.

## License

RenVault is released under the [GNU General Public License v3.0](LICENSE).

RenVault is not affiliated with or endorsed by Apple. iCloud and Apple Account are trademarks of Apple Inc.
