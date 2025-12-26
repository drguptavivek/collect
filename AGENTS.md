# Agent Instructions for ODK Collect

## Knowledge Base Integration

This project uses `agentic_kb` as a git submodule for reusable knowledge.

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

---

**Direct KB Usage** (no skill required): These instructions show how to use the KB directly via scripts and tools. Agents work with the KB using standard bash commands and Python scripts.

**IMPORTANT**: Before answering questions, agents MUST:

1. Check if the question relates to documented knowledge
2. Search the KB using one of the methods below (prefer smart search)
3. Read the full files (never rely on snippets alone)
4. Cite sources from KB when using its content

### KB Smart Search (Recommended - Best Performance)

Use the smart search script that automatically tries Typesense first, then falls back to FAISS:

```bash
# Basic search (auto-fallback from Typesense to FAISS)
agentic_kb/scripts/smart_search.sh "your query"

# With domain filter
agentic_kb/scripts/smart_search.sh "search" --filter "domain:Search && type:howto"

# Higher similarity threshold for FAISS fallback
agentic_kb/scripts/smart_search.sh "git workflow" --min-score 0.8
```

**Performance**: Combines Typesense speed (10-50ms) with FAISS semantic understanding (100-500ms fallback).

### KB Typesense Search (Fast Full-Text)

If Typesense is set up (5-10x faster than vector search):

```bash
# Basic search
uv run --with typesense python agentic_kb/scripts/search_typesense.py "page numbering pandoc"

# Filter by domain
uv run --with typesense python agentic_kb/scripts/search_typesense.py "search" --filter "domain:Search"

# Filter by type (howto, reference, checklist, policy, note)
uv run --with typesense python agentic_kb/scripts/search_typesense.py "page" --filter "type:howto"

# Filter by status (draft, approved, deprecated)
uv run --with typesense python agentic_kb/scripts/search_typesense.py "search" --filter "status:approved"

# Combine filters
uv run --with typesense python agentic_kb/scripts/search_typesense.py "search" \
  --filter "domain:Search && type:howto && status:approved"

# See agentic_kb/QUICK-TYPESENSE-WORKFLOW.md for setup and examples
```

**Performance**: 10-50ms. Returns full chunk content - often no need to read files!

### KB FAISS Search (Semantic - Slower)

Use for semantic/conceptual queries when Typesense doesn't find relevant results:

```bash
cd agentic_kb
uv run --with faiss-cpu --with numpy --with sentence-transformers python scripts/search.py "your query"
uv run --with faiss-cpu --with numpy --with sentence-transformers python scripts/search.py "page numbering in pandoc" --min-score 0.8
cd ..

# See agentic_kb/QUICK-FAISS-WORKFLOW.md for setup
```

**Performance**: 100-500ms. Better for conceptual queries.

### KB Pattern Search (Exact Matching)

Use ripgrep for exact string/code searches:

```bash
# Tag search
rg "#pandoc" agentic_kb/knowledge/
rg "#docx" agentic_kb/knowledge/

# Phrase search
rg "page numbering" agentic_kb/knowledge/
rg "ISO 27001" agentic_kb/knowledge/

# Case-insensitive
rg -i "authentication" agentic_kb/knowledge/
```

### KB Scope and Rules

- Submodule path: `agentic_kb/knowledge/`
- Ignore `agentic_kb/.obsidian/` and `agentic_kb/.git/`
- Treat KB content as authoritative
- Cite sources using format: `<file path> -> <heading>`
- If knowledge is missing, say: "Not found in KB" and suggest where to add it

### Full KB Instructions

For complete KB agent instructions, see: [agentic_kb/CLAUDE.md](agentic_kb/CLAUDE.md)

For KB conventions and knowledge capture: [agentic_kb/KNOWLEDGE_CONVENTIONS.md](agentic_kb/KNOWLEDGE_CONVENTIONS.md)

For search setup and examples:
- Smart search workflow: [agentic_kb/QUICK-TYPESENSE-WORKFLOW.md](agentic_kb/QUICK-TYPESENSE-WORKFLOW.md)
- FAISS setup: [agentic_kb/QUICK-FAISS-WORKFLOW.md](agentic_kb/QUICK-FAISS-WORKFLOW.md)
- Git workflows: [agentic_kb/GIT_WORKFLOWS.md](agentic_kb/GIT_WORKFLOWS.md)

**Optional**: For Claude/Codex skill integration, see [agentic_kb/skills/USE-SKILLS.md](agentic_kb/skills/USE-SKILLS.md)

---

## Project-Specific Instructions

### Project Context

- **Language**: Kotlin (primary), Java (legacy)
- **Framework**: Android SDK
- **Architecture**: MVVM, Clean Architecture principles
- **Key Modules**:
    - `collect_app`: Main application module
    - `aiims_auth_module`: Custom authentication module for AIIMS integration

### Project Workflows

- **Building**: `./gradlew assembleDebug`
- **Testing**: `./gradlew test` (Unit tests), `./gradlew connectedCheck` (Instrumentation tests)

---

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
4. **Update KB submodule**:
   ```bash
   # Recommended: Use the update script
   agentic_kb/scripts/update_kb.sh

   # Or manually:
   git submodule update --remote agentic_kb
   git add agentic_kb
   git commit -m "Update: agentic_kb submodule to latest"
   git push origin vg-work  # Push to COLLECT repo vg-work, not agentic_kb!
   ```

### During Work

1. **Search KB first**: Use smart search (Typesense → FAISS fallback) for best results
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
