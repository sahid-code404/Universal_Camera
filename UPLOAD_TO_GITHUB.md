# Upload this repository to GitHub

Your empty repository is:

`git@github.com:sahid-code404/Universal_Camera.git`

## Recommended: command line

Unzip the package, then:

```bash
cd Universal_Camera
git init
git add .
git commit -m "chore: initialize Camera architecture, native UI and OTA"
git branch -M main
git remote add origin git@github.com:sahid-code404/Universal_Camera.git
git push -u origin main
```

If `origin` already exists:

```bash
git remote set-url origin git@github.com:sahid-code404/Universal_Camera.git
git push -u origin main
```

## After push

1. Open **Actions** and confirm `Android CI` starts.
2. If CI reports a Kotlin/AGP dependency issue, fix the build before starting Phase 1 camera probing.
3. Configure release signing secrets described in `docs/OTA_UPDATES.md` before creating a release tag.
4. Do not publish production APKs signed with a temporary/debug key if you expect seamless OTA updates later.
