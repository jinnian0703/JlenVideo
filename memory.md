# Memory

## Collaboration Rules

- For any new feature or small code/config change, automatically create a git commit and push it after the change is completed and verified when possible.
- Reply to the user in Chinese throughout the whole collaboration.
- After app code changes, provide the APK path when applicable and include the pushed commit hash/message in the reply. If the user says "打包" without specifying a variant, build the release APK by default; build debug only when explicitly requested or for verification.
- If HTTPS push fails, use a long-lived writable GitHub deploy key for this repository instead of creating and deleting a temporary SSH key each time.

## Release Rules

- If the user says "发布某版本", treat it as a request to complete all of the following by default: update the version number, build the APK, commit and push the source code, create a GitHub Release, and upload the APK to the Release.
- Use the APK naming format `JlenVideo-版本号-release.apk` for release builds.
- Publish GitHub Release notes with a UTF-8 file via `--notes-file`; do not inline Chinese release notes directly in a PowerShell command.
- After publishing, verify that the Chinese text on the GitHub Release displays correctly.
- For future version releases, the in-app download link should point to the optimized release APK instead of the debug APK.
- When the user asks to package/build an APK without specifying the variant, build/output the release APK by default; otherwise keep routine verification builds to debug when appropriate.
