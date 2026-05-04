# Agent Instructions for ODK Collect


### ⚠️ CRITICAL: Branch Configuration

**PREVENT CONFUSION**: This is a TWO-REPOSITORY setup with DIFFERENT BRANCHES:

| Repository | Branch | Purpose | Remote |
|-----------|--------|---------|--------|
| **collect** (parent) | `vg-work` | Main project development | `origin` → github.com/drguptavivek/collect.git |
| **agentic_kb** (submodule) | `main` | Knowledge documentation | `origin` → github.com/drguptavivek/agentic_kb.git |

**RULES TO FOLLOW**:
- ✅ **Always commit/push collect repo to `vg-work` branch**
- ✅ **Always commit/push agentic_kb to `main` branch**
- ❌ **NEVER push collect repo changes to agentic_kb repository**
- ❌ **NEVER push agentic_kb knowledge to collect repository**
- ⚠️ **Always verify remote URL before pushing**: Check `git remote -v` shows correct repository

**Verify Setup Before Working**:
```bash
# In collect repo (parent)
git remote -v  # Should show: origin → https://github.com/drguptavivek/collect.git
git branch -v  # Should show: * vg-work

# In agentic_kb (submodule)
cd agentic_kb
git remote -v  # Should show: origin → https://github.com/drguptavivek/agentic_kb.git
git branch -v  # Should show: * main
cd ..
```

## Project-Specific Instructions

### Project Context

- **Language**: Kotlin (primary), Java (legacy)
- **Framework**: Android SDK
- **Architecture**: MVVM, Clean Architecture principles
- **Key Modules**:
    - `collect_app`: Main application module
    - `medres_auth_module`: Custom authentication module for MEDRES integration

### Project Workflows

- **Building**: `./gradlew assembleDebug`
- **Testing**: `./gradlew test` (Unit tests), `./gradlew connectedCheck` (Instrumentation tests)

This project uses product flavors (odk and medres), 
DO NOt use installDebug - which can be ambiguous or lead to building unnecessary artifacts.
- To build and install the MEDRES debug version specifically, use:
   `./gradlew :collect_app:installMedresDebug`
  (Or simply ./gradlew installMedresDebug if running from the root directory).

- To build and install the standard ODK debug version, use:
   `./gradlew :collect_app:installOdkDebug`

---
## Issue Tracking

Add to Plans directory - 3CharCode-opn/closed/inprogress/blocked-yyyymmddhhmmss-3-4 word brief.md

### All issues need to be linked to Github issues as well
- use gh cli
- Issue format - [PRIORITY] Title (3CharCode)
- Then add a valid gh label to the gh issue 
   - bug: Indicates an unexpected problem or unintended behavior.
   - enhancement: Indicates new feature requests.
   - documentation: Indicates a need for improvements or additions to documentation.
   - wontfix: Indicates that work won't continue on the issue, pull request, or discussion.
   - duplicate: Indicates similar issues, pull requests, or discussions.
   - good first issue: Indicates an issue suitable for first-time contributors.
   - help wanted: Indicates that a maintainer wants help on the issue or pull request.
   - invalid: Indicates that an issue, pull request, or discussion is no longer relevant.
   - question: Indicates a need for more information or discussion.
- Include full details as per the bead and the proposed resolution in gh issue
 



## Agent Workflow

### ✅ Session Start Checklist

**BEFORE starting work**:
1. **Verify you're in the right repository**: `pwd` should show `/collect`
2. **Verify remote URLs**:
   ```bash
   git remote -v
   # Must show: origin → https://github.com/drguptavivek/collect.git (NOT agentic_kb!)
   ```
3. **Verify branch**: `git branch` should show `* vg-work`

### During Work
2. **Read full files**: Never rely on search snippets alone - always read complete files
3. **Follow project conventions**: Apply project-specific rules from sections above
4. **Document learnings**: Capture reusable knowledge in the KB (see agentic_kb/KNOWLEDGE_CONVENTIONS.md)

### When Adding Knowledge to KB

**CRITICAL**: Knowledge commits go ONLY to agentic_kb/main, NEVER to collect/vg-work

```bash
# 1. Make changes in agentic_kb/
cd agentic_kb
git checkout main
# ... create/edit knowledge files ...

# 2. Commit to agentic_kb/main
git add knowledge/...
git commit -m "Add: Knowledge about ..."
git push origin main  # Push to AGENTIC_KB, not collect!

# 3. Return to parent and update submodule reference
cd ..
git add agentic_kb
git commit -m "Update: agentic_kb submodule with new knowledge"
git push origin vg-work  # Push to COLLECT vg-work
```

### ⚠️ Common Mistakes to Avoid

❌ **WRONG**: `cd agentic_kb && git push origin vg-work`
   - This pushes to agentic_kb repository vg-work branch (mixing repos)

✅ **RIGHT**:
   - From agentic_kb: `git push origin main` (knowledge goes to agentic_kb/main)
   - From collect: `git push origin vg-work` (project code goes to collect/vg-work)

❌ **WRONG**: Having parent repo's origin point to agentic_kb
   - Check: `git remote -v` must show collect repository

✅ **RIGHT**: Parent repo origin always points to collect repository

---


## FIXING
- Fix issues one by one....top priority high yield issues first.
- Use TDD.
- After each fix, run tests and verify that they pass and app compiles. 
- Close issue in plan and close corresponding GH issue with full detaiuls of fix implemented .
- Do not prompt when closing issue or pushing to github or commiting to git.
- Update docs in @docs as app changes
