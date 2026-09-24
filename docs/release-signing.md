# Release signing

Release APKs must use one private signing key for the lifetime of the app.
The key must never be committed to this repository.

## Create a key

Run this outside the repository. Choose and keep the password in a password
manager:

```bash
keytool -genkeypair \
  -keystore monomemo-release.jks \
  -storetype PKCS12 \
  -alias monomemo-release \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -storepass 'CHOOSE_A_PASSWORD' \
  -keypass 'CHOOSE_A_PASSWORD' \
  -dname 'CN=MonoMemo, O=kusune'
```

Keep `monomemo-release.jks` and its password private. The certificate
fingerprint for Android Developer Console registration can be displayed with:

```bash
keytool -list -v -keystore monomemo-release.jks \
  -storepass 'CHOOSE_A_PASSWORD'
```

## GitHub Actions secrets

Add these repository Actions secrets at **Settings → Secrets and variables →
Actions**:

- `MONOMEMO_KEYSTORE_BASE64`: output of `base64 -w 0 monomemo-release.jks`
- `MONOMEMO_KEYSTORE_PASSWORD`: the keystore password

The release workflow uses the fixed alias `monomemo-release` and signs every
tagged release with this same key. If the key is lost, existing installations
cannot be updated, so keep a secure backup.
