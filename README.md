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
- Desktop: `./gradlew :desktopApp:run -Drenvault.main=VaultMainKt`
- Desktop packages: `./gradlew :desktopApp:packageDistributionForCurrentOS`

## Project layout

- `core/` – the Go core: vault, encryption, iCloud Keychain, sync, CTAP2/passkeys
- `shared/` – the Compose Multiplatform app shared by every platform
- `androidApp/` – Android entry point, autofill and Credential Manager services
- `desktopApp/` – desktop entry point and packaging

## Privacy and security

RenVault has no servers, no account of its own and no analytics. Your passwords never pass through anyone except Apple (in iCloud mode) and your own devices.

**How your data is protected**

- The vault on each device is encrypted with XChaCha20-Poly1305. The vault key is stored in the system's secure storage (Android Keystore, Windows DPAPI, the Secret Service keyring on Linux) or protected by your master password, derived with Argon2id.
- Paired devices sync directly over your local network. Pairing uses an X25519 key exchange through a QR code, and the traffic between devices is end-to-end encrypted.
- Encrypted `.renvault` backups are protected by a password you choose.

**What goes over the internet**

| Where | What is sent | When |
| --- | --- | --- |
| Apple | Your Apple Account sign-in and the iCloud Keychain data | iCloud mode only |
| Anisette server | Device data that Apple requires for sign-in, never your password | Signing in to iCloud; you can pick the server or use your own |
| Icon provider (DuckDuckGo, Google, Bitwarden or the site itself) | The website addresses of your items | Showing site icons; can be turned off in the settings |
| Google Play | The package names of linked Android apps | Showing app names and icons; off when site icons are off |
| Have I Been Pwned | The first 5 characters of the password's SHA-1 hash, never the password | Checking passwords against known breaches |
| GitHub | Nothing about you: it downloads Apple's public password rules | Checking that a password fits a website's rules |

In local mode nothing reaches Apple or the anisette server.

To report a security problem, see [SECURITY.md](SECURITY.md).

## License

RenVault is released under the [GNU General Public License v3.0](LICENSE).

RenVault is not affiliated with or endorsed by Apple. iCloud and Apple Account are trademarks of Apple Inc.
