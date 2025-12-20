# Maintenance Guide

This document outlines the procedures for maintaining the AIIMS Custom Fork of ODK Collect.

## Branch Structure
- **`master`**: Tracks `upstream/master` (Standard ODK Collect). SHOULD NOT contain custom code.
- **`vg-work`**: The active development branch containing AIIMS customizations on top of `master`.

## syncing with Upstream (Rebase Workflow)

To update the custom fork with the latest standard features/fixes:

1. **Update `master`**
   ```bash
   git checkout master
   git fetch upstream
   git merge upstream/master --ff-only
   git push origin master
   ```

2. **Rebase `vg-work`**
   ```bash
   git checkout vg-work
   git rebase master
   ```

3. **Resolve Conflicts**
   - If conflicts occur, resolve them in favor of keeping AIIMS functionality compliant with new upstream changes.
   - Use `git rebase --continue`.

4. **Verify**
   - Clean and Rebuild: `./gradlew clean assembleDebug`
   - Run Tests: `./gradlew testDebugUnitTest`

5. **Update Log**
   - Add an entry to `docs/SYNC_LOG.md` recording the sync.

6. **Push**
   ```bash
   git push -f origin vg-work
   ```
   *(Note: Force push is required after rebase)*
