# F-Droid

## Self-hosted repository

While official inclusion is pending, BBDNS is already available from a signed
F-Droid repository hosted on GitHub Pages:

- Repository URL: `https://buzzqw.github.io/BetterBlockerDNS/repo`
- Fingerprint: `13:64:2D:F1:5D:EC:D4:EE:11:9B:EE:11:C1:9C:61:5F:B1:B8:3C:CD:79:3C:52:75:F7:36:65:F8:40:A3:54:8D`

After `scripts/build_release.sh`, publish the new APK with:

```bash
FDROID_BIN=/path/to/fdroid scripts/publish_fdroid_repo.sh
```

The repository signing key lives in `~/.config/bbdns/fdroid-repo.env` and must
be backed up.

## Submission to the official F-Droid repository

`com.buzzqw.bbdns.yml` is a template for the
[fdroiddata](https://gitlab.com/fdroid/fdroiddata) repository. F-Droid builds
the app from source, signs it with the F-Droid key and publishes it from its
own repository.

## Before submitting

- The source repository must be public and up to date.
- `version.properties` must match `versionName`/`versionCode` above.
- Create and push the release tag, for example:

  ```bash
  ./gradlew bumpVersion
  # update CHANGELOG.md and metadata/en-US/changelogs/<versionCode>.txt
  git add version.properties CHANGELOG.md metadata/en-US/changelogs
  git commit -m "Release 1.0.25"
  git tag -a v1.0.25 -m "BBDNS 1.0.25"
  git push origin main v1.0.25
  ```

- Fill `<commit-hash>` with the full hash of the tagged commit:

  ```bash
  git rev-list -n 1 v1.0.25
  ```

## Submit

1. Fork `https://gitlab.com/fdroid/fdroiddata`.
2. Add the metadata file as `metadata/com.buzzqw.bbdns.yml`.
3. Install `fdroidserver` and validate the recipe:

   ```bash
   fdroid lint com.buzzqw.bbdns
   fdroid rewritemeta com.buzzqw.bbdns
   ```

4. Push the branch and open a merge request labelled **New App**. If you do not
   want to maintain the recipe, open a request instead:
   <https://gitlab.com/fdroid/rfp/-/issues>.

The CI on the merge request builds the app in the F-Droid environment. Once
the merge request is accepted, the first build usually appears in the main
repository within 24-48 hours.

## Build notes

- The app compiles its Go native library (`libnflog.so`) during the Gradle
  build, so the recipe installs Go and declares NDK `27.0.12077973`. That
  version must stay in sync with `app/src/main/go/build-android.sh`.
- The release Gradle hooks never send email: `emailReleaseBuild` only runs
  with `BBDNS_EMAIL_BUILD=1` (`scripts/build_release.sh`).
- `m3color` is resolved from JitPack. If the F-Droid build rejects that
  repository, replace it with an equivalent Maven Central artifact.
- F-Droid signs with its own key. Release builds use the project keystore when
  its environment variables are set; F-Droid does not have them, so it builds
  with the debug fallback and then re-signs the APK. Reproducible builds can be
  enabled later with `Binaries` and `AllowedAPKSigningKeys`.
