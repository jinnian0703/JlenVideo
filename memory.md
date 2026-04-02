# Memory

## Collaboration Rules

- For any new feature or small code/config change, automatically create a git commit and push it after the change is completed and verified when possible.
- Reply to the user in Chinese throughout the whole collaboration.
- After app code changes, provide the debug APK when applicable and include the pushed commit hash/message in the reply.

## Release Rules

- If the user says "发布某版本", treat it as a request to complete all of the following by default: update the version number, build the APK, commit and push the source code, create a GitHub Release, and upload the APK to the Release.
- Use the APK naming format `JlenVideo-版本号-debug.apk`.
- Publish GitHub Release notes with a UTF-8 file via `--notes-file`; do not inline Chinese release notes directly in a PowerShell command.
- After publishing, verify that the Chinese text on the GitHub Release displays correctly.
