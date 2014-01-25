Things to do when making a release:
-----------------------------------
1. Update the readme
    - "The current release is"
    - Link to apk
    - Update screenshots
2. Add a section to the changelog
3. Update the manifest
    - `versionCode` and `versionName`
4. Update the strings
    - `pref_about_summary`
5. git
    - `git add bin/HallMonitor.apk`
    - `git commit -m "releasing version $VERSION"`
    - `git tag $VERSION`
    - `git push`
    - `git push --tags`