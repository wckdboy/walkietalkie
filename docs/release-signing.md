# Stable release signing

Debug and instrumentation builds use the local demo keystore. They must never
be uploaded as the installable GitHub release asset. `assembleRelease` produces
an unsigned release APK, without embedding any signing key or password.

The guarded release job runs only after build/unit/lint/emulator checks succeed.
It requires these **repository Actions secrets** in `wckdboy/walkietalkie`:

- `ANDROID_SIGNING_KEYSTORE_BASE64`: base64 of a stable PKCS12 keystore.
- `ANDROID_SIGNING_PASSWORD`: password for that keystore and private key.

The expected alias is `walkietalkie`. Generate a new key only during an explicitly
authorized initial setup. Retain a secure backup outside GitHub; replacing the
key changes the app's signing identity. Do not put secrets in source, artifacts,
workflow outputs, logs, or release notes.

`scripts/sign-release.py` fails if either secret is missing, decodes the key into
a private temporary directory with file mode 0600, passes the password via an
environment variable to `apksigner`, verifies the signed APK, then removes the
temporary keystore. It does not generate keys. Verification prints the public
certificate fingerprint, not the private key or password. The release includes
`SIGNING_CERTIFICATE.txt` so users can compare the public signer fingerprint
across releases. The helper also checks against the approved public fingerprint
in `release-signing-certificate.sha256` and rejects an unexpected signing key.

A new stable key cannot match earlier randomly generated CI demo keys. Preserve
that original key if it exists and update the alias configuration accordingly,
or expect a one-time installation/signature transition. Users must export their
recordings before uninstalling an incompatible older APK. Once established,
keep the same stable key for future releases.

The `release/0.2.3-safe-speech` branch may publish this compatibility prerelease
without modifying `main`. This branch-specific path is intentionally narrow.
