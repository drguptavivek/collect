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
    - `aiims_auth_module`: Custom authentication module for AIIMS integration

### Project Workflows

- **Building**: `./gradlew assembleDebug`
- **Testing**: `./gradlew test` (Unit tests), `./gradlew connectedCheck` (Instrumentation tests)

---
## Issue Tracking

This project uses **bd (beads)** for issue tracking.
Run `bd prime` for workflow context, or install hooks (`bd hooks install`) for auto-injection.

**Quick reference:**
- `bd ready` - Find unblocked work
- `bd create "Title" --type task --priority 2` - Create issue
- `bd close <id>` - Complete work
- `bd sync` - Sync with git (run at session end)

For full workflow details: `bd prime`

---
## Knowledge Base Integration

This project uses `agentic_kb` as a git submodule for reusable knowledge.


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

## Landing the Plane (Session Completion)

**When ending a work session**, you MUST complete ALL steps below. Work is NOT complete until `git push` succeeds.

**MANDATORY WORKFLOW:**

1. **File issues for remaining work** - Create issues for anything that needs follow-up
2. **Run quality gates** (if code changed) - Tests, linters, builds
3. **Update issue status** - Close finished work, update in-progress items
4. **PUSH TO REMOTE** - This is MANDATORY:
   ```bash
   git pull --rebase
   bd sync
   git push
   git status  # MUST show "up to date with origin"
   ```
5. **Clean up** - Clear stashes, prune remote branches
6. **Verify** - All changes committed AND pushed
7. **Hand off** - Provide context for next session

**CRITICAL RULES:**
- Work is NOT complete until `git push` succeeds
- NEVER stop before pushing - that leaves work stranded locally
- NEVER say "ready to push when you are" - YOU must push
- If push fails, resolve and retry until it succeeds

<!-- bv-agent-instructions-v1 -->

---

## Beads Workflow Integration

This project uses [beads_viewer](https://github.com/Dicklesworthstone/beads_viewer) for issue tracking. Issues are stored in `.beads/` and tracked in git.

### Essential Commands

```bash
# View issues (launches TUI - avoid in automated sessions)
bv

# CLI commands for agents (use these instead)
bd ready              # Show issues ready to work (no blockers)
bd list --status=open # All open issues
bd show <id>          # Full issue details with dependencies
bd create --title="..." --type=task --priority=2
bd update <id> --status=in_progress
bd close <id> --reason="Completed"
bd close <id1> <id2>  # Close multiple issues at once
bd sync               # Commit and push changes
```

### Workflow Pattern

1. **Start**: Run `bd ready` to find actionable work
2. **Claim**: Use `bd update <id> --status=in_progress`
3. **Work**: Implement the task
4. **Complete**: Use `bd close <id>`
5. **Sync**: Always run `bd sync` at session end

### Key Concepts

- **Dependencies**: Issues can block other issues. `bd ready` shows only unblocked work.
- **Priority**: P0=critical, P1=high, P2=medium, P3=low, P4=backlog (use numbers, not words)
- **Types**: task, bug, feature, epic, question, docs
- **Blocking**: `bd dep add <issue> <depends-on>` to add dependencies

### Session Protocol

**Before ending any session, run this checklist:**

```bash
git status              # Check what changed
git add <files>         # Stage code changes
bd sync                 # Commit beads changes
git commit -m "..."     # Commit code
bd sync                 # Commit any new beads changes
git push                # Push to remote
```

### Best Practices

- Check `bd ready` at session start to find available work
- Update status as you work (in_progress → closed)
- Create new issues with `bd create` when you discover tasks
- Use descriptive titles and set appropriate priority/type
- Always `bd sync` before ending session

<!-- end-bv-agent-instructions -->

### Using bv as an AI sidecar

bv is a graph-aware triage engine for Beads projects (.beads/beads.jsonl). Instead of parsing JSONL or hallucinating graph traversal, use robot flags for deterministic, dependency-aware outputs with precomputed metrics (PageRank, betweenness, critical path, cycles, HITS, eigenvector, k-core).

**Scope boundary:** bv handles *what to work on* (triage, priority, planning). For agent-to-agent coordination (messaging, work claiming, file reservations), use [MCP Agent Mail](https://github.com/Dicklesworthstone/mcp_agent_mail).

**⚠️ CRITICAL: Use ONLY `--robot-*` flags. Bare `bv` launches an interactive TUI that blocks your session.**

#### The Workflow: Start With Triage

**`bv --robot-triage` is your single entry point.** It returns everything you need in one call:
- `quick_ref`: at-a-glance counts + top 3 picks
- `recommendations`: ranked actionable items with scores, reasons, unblock info
- `quick_wins`: low-effort high-impact items
- `blockers_to_clear`: items that unblock the most downstream work
- `project_health`: status/type/priority distributions, graph metrics
- `commands`: copy-paste shell commands for next steps

bv --robot-triage        # THE MEGA-COMMAND: start here
bv --robot-next          # Minimal: just the single top pick + claim command

#### Other Commands

**Planning:**
| Command | Returns |
|---------|---------|
| `--robot-plan` | Parallel execution tracks with `unblocks` lists |
| `--robot-priority` | Priority misalignment detection with confidence |

**Graph Analysis:**
| Command | Returns |
|---------|---------|
| `--robot-insights` | Full metrics: PageRank, betweenness, HITS (hubs/authorities), eigenvector, critical path, cycles, k-core, articulation points, slack |
| `--robot-label-health` | Per-label health: `health_level` (healthy\|warning\|critical), `velocity_score`, `staleness`, `blocked_count` |
| `--robot-label-flow` | Cross-label dependency: `flow_matrix`, `dependencies`, `bottleneck_labels` |
| `--robot-label-attention [--attention-limit=N]` | Attention-ranked labels by: (pagerank × staleness × block_impact) / velocity |

**History & Change Tracking:**
| Command | Returns |
|---------|---------|
| `--robot-history` | Bead-to-commit correlations: `stats`, `histories` (per-bead events/commits/milestones), `commit_index` |
| `--robot-diff --diff-since <ref>` | Changes since ref: new/closed/modified issues, cycles introduced/resolved |

**Other Commands:**
| Command | Returns |
|---------|---------|
| `--robot-burndown <sprint>` | Sprint burndown, scope changes, at-risk items |
| `--robot-forecast <id\|all>` | ETA predictions with dependency-aware scheduling |
| `--robot-alerts` | Stale issues, blocking cascades, priority mismatches |
| `--robot-suggest` | Hygiene: duplicates, missing deps, label suggestions, cycle breaks |
| `--robot-graph [--graph-format=json\|dot\|mermaid]` | Dependency graph export |
| `--export-graph <file.html>` | Self-contained interactive HTML visualization |

#### Scoping & Filtering

bv --robot-plan --label backend              # Scope to label's subgraph
bv --robot-insights --as-of HEAD~30          # Historical point-in-time
bv --recipe actionable --robot-plan          # Pre-filter: ready to work (no blockers)
bv --recipe high-impact --robot-triage       # Pre-filter: top PageRank scores
bv --robot-triage --robot-triage-by-track    # Group by parallel work streams
bv --robot-triage --robot-triage-by-label    # Group by domain

#### Understanding Robot Output

**All robot JSON includes:**
- `data_hash` — Fingerprint of source beads.jsonl (verify consistency across calls)
- `status` — Per-metric state: `computed|approx|timeout|skipped` + elapsed ms
- `as_of` / `as_of_commit` — Present when using `--as-of`; contains ref and resolved SHA

**Two-phase analysis:**
- **Phase 1 (instant):** degree, topo sort, density — always available immediately
- **Phase 2 (async, 500ms timeout):** PageRank, betweenness, HITS, eigenvector, cycles — check `status` flags

**For large graphs (>500 nodes):** Some metrics may be approximated or skipped. Always check `status`.

#### jq Quick Reference

bv --robot-triage | jq '.quick_ref'                        # At-a-glance summary
bv --robot-triage | jq '.recommendations[0]'               # Top recommendation
bv --robot-plan | jq '.plan.summary.highest_impact'        # Best unblock target
bv --robot-insights | jq '.status'                         # Check metric readiness
bv --robot-insights | jq '.Cycles'                         # Circular deps (must fix!)
bv --robot-label-health | jq '.results.labels[] | select(.health_level == "critical")'

**Performance:** Phase 1 instant, Phase 2 async (500ms timeout). Prefer `--robot-plan` over `--robot-insights` when speed matters. Results cached by data hash.

Use bv instead of parsing beads.jsonl—it computes PageRank, critical paths, cycles, and parallel tracks deterministically.

