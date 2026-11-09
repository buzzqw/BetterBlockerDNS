# F-Droid

## Submission to the official F-Droid repository

**Status: closed on 8 November 2026.** The merge request
[!56352](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/56352) was closed
by the F-Droid maintainers because the template was not followed, the app had
not been maintained for more than three months, and it was not considered to
offer unique features. The recipe is kept up to date for a future submission.

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
  git commit -m "Release 1.0.29"
  git tag -a v1.0.29 -m "BBDNS 1.0.29"
  git push origin main v1.0.29
  ```

- Fill `<commit-hash>` with the full hash of the tagged commit:

  ```bash
  git rev-list -n 1 v1.0.29
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

- The recipe entry below builds version 1.0.29, whose Gradle build still
  compiles the Go helper (`libnflog.so`); that is why it installs Go. Current
  sources no longer ship that helper and only need NDK `27.0.12077973` for the
  C packet engine. When the recipe is updated to a newer version, the Go steps
  can be dropped.
- While the official Gradle CDN returns 504 errors, the recipe caches Gradle
  9.9.0 from a mirror in `prebuild`; `gradlew-fdroid` verifies the archive
  against the official Gradle transparency log before using it.
- The release Gradle hooks never send email: `emailReleaseBuild` only runs
  with `BBDNS_EMAIL_BUILD=1` (`scripts/build_release.sh`).
- `m3color` is resolved from JitPack. If the F-Droid build rejects that
  repository, replace it with an equivalent Maven Central artifact.
- F-Droid signs with its own key. Release builds use the project keystore when
  its environment variables are set; F-Droid does not have them, so it builds
  with the debug fallback and then re-signs the APK. Reproducible builds can be
  enabled later with `Binaries` and `AllowedAPKSigningKeys`.
