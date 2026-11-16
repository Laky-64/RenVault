# Security Policy

RenVault stores passwords, passkeys and verification codes, so security reports are taken seriously and handled before anything else.

## Supported versions

Only the latest release on the [Releases](https://github.com/Laky-64/RenVault/releases) page receives security fixes. RenVault is in beta, so please update before reporting.

## Reporting a vulnerability

Please do not open a public issue for security problems.

Report it privately through GitHub instead: open the **Security** tab of this repository and choose **Report a vulnerability**. Only the maintainer can see the report.

Include as much as you can:

- what an attacker can do and what they need (physical access, same network, a malicious app, a malicious website…)
- the platform and RenVault version
- steps to reproduce, or a proof of concept

You will get a reply as soon as possible. Once a fix is released, the report can be made public, with credit if you want it.

## In scope

- The vault and its encryption, backups and the local key storage on each platform
- iCloud Keychain sign-in and sync
- Sync between paired devices
- Autofill and passkeys (Android, the Linux virtual security key, the Windows passkey provider)

## Out of scope

- Vulnerabilities in Apple's services, in the operating system or in third-party anisette servers
- Attacks that need an already unlocked device and full control over it
- Reports from automated scanners without a working attack
