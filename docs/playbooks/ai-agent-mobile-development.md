# AI-Agent Mobile Development Playbook (Picfort Case Study)

This report summarizes **practical rules and engineering habits** for building a mobile app with AI coding agents.
It is based on how Picfort is organized and operated, then cross-checked against Android and CI best-practice references.

---

## What characterizes an AI-agent-heavy mobile project

An AI-agent-heavy project is not defined by “AI-generated code.” It is defined by **how the repository constrains and verifies change**:

1. **Context is intentionally segmented.** The codebase is modular, and agents are told not to read everything at once. This reduces context overload and mistake propagation. [P2][P3][P5]
2. **Architecture rules are executable, not advisory.** Dependency boundaries are enforced by script and run in CI. [P6][P7]
3. **Every module has local operating instructions.** Short `AGENTS.md` files define scope, dependencies, entry points, and module-level verification commands. [P3][P6]
4. **Visual regressions are first-class.** Screenshot tests (Paparazzi) run as part of normal verification, with artifacts uploaded on failure. [P7][P8]
5. **Planning and decisions are durable.** ADRs + task queue statuses make intent, order, and completion criteria explicit for both humans and agents. [P4][P9][P10]
6. **Scaffolding is standardized.** New features are created through scripts/playbooks that encode the expected architecture from day one. [P11][P12]
7. **Security assumptions are documented and bounded.** Network trust zones and API-key requirements are explicit. [P13][P14]

---

## Rules and best practices for building mobile apps with AI agents

## 1) Treat architecture boundaries as a testable contract

**Rule:** If a boundary can be broken, it eventually will be broken by either humans or agents. Put boundaries into automated checks.

**Why for AI agents:** Agents optimize for local completion; without hard boundaries, they may introduce convenient but harmful couplings.

**How to implement:**
- Add a boundary script that fails on forbidden dependencies.
- Run it locally before changes and in CI on every PR.
- Keep checks simple and transparent (regex/static checks are acceptable as baseline guardrails).

**Picfort evidence:** Explicit feature isolation and core-layer boundary checks are codified and validated automatically. [P3][P6][P7]

---

## 2) Optimize repository structure for small context windows

**Rule:** Prefer a thin app shell plus isolated feature modules and shared core contracts.

**Why for AI agents:** Smaller scope per task reduces hallucination risk and improves correctness of code navigation and edits.

**How to implement:**
- Keep `:app` orchestration thin; move feature logic to feature modules.
- Keep cross-feature interfaces in a stable core module.
- Enforce “read only what you need” as an explicit contributor rule.

**Picfort evidence:** `:app` is explicitly “thin shell”; feature modules are isolated; contributors/agents are instructed not to load the whole project. [P2][P3][P5]

**External alignment:** Android’s modularization guidance emphasizes maintainability, scalability, encapsulation, and testability via loosely coupled modules. [E1]

---

## 3) Use interface contracts for cross-feature communication

**Rule:** Never call sibling feature internals directly; communicate via contracts in a neutral core layer.

**Why for AI agents:** Agents often follow direct call paths; contracts prevent “shortcut dependencies” that are hard to unwind later.

**How to implement:**
- Define provider interfaces in `:core:model` (or equivalent neutral layer).
- Bind implementations in composition root (`:app` / DI modules).
- Keep feature modules ignorant of each other’s internals.

**Picfort evidence:** Cross-feature contracts in `:core:model` are an explicit architecture rule and appear in both ADR and module guidance. [P3][P5][P11]

---

## 4) Make module docs short, local, and executable

**Rule:** Each module should expose a concise operations card: scope, dependencies, entry points, and exact test commands.

**Why for AI agents:** Agents perform best with explicit, local constraints and concrete commands, not broad prose.

**How to implement:**
- Add `<module>/AGENTS.md` with strict line budget.
- Include module-specific verification commands.
- Validate existence/size of these docs in automation.

**Picfort evidence:** Module `AGENTS.md` presence and recommended size are enforced; DoD requires updates when module code is touched. [P4][P6]

---

## 5) Standardize feature creation via scripts/playbooks

**Rule:** Never start new modules from blank copy-paste.

**Why for AI agents:** Scaffolding drift compounds quickly when many AI-generated edits happen in parallel or over time.

**How to implement:**
- Maintain a single scaffolding script for feature modules.
- Ship a playbook that explains the intended architecture and validation path.
- Include default dependencies and anti-coupling rules in templates.

**Picfort evidence:** `new-feature.sh` creates module structure + `AGENTS.md` + dependency wiring, and points to boundary verification; playbook codifies the same process. [P11][P12]

---

## 6) Use deterministic visual testing as a default, not an afterthought

**Rule:** UI snapshots should be reproducible and checked in, with explicit record/verify workflow.

**Why for AI agents:** Agents can unintentionally alter UI details; deterministic snapshots catch subtle regressions quickly.

**How to implement:**
- Keep screenshot tests in each feature module.
- Use deterministic fixtures (generated or fixed input media).
- Provide clear `record` and `verify` commands in docs and CI.
- Upload diffs/reports on failure for fast triage.

**Picfort evidence:** Paparazzi tests run in `app` and feature modules; deterministic sample photos are documented; CI verifies screenshots and uploads failures. [P7][P8]

**External alignment:** Paparazzi is explicitly designed for device-less rendering, golden recording, and verification with diff artifacts. [E3]

---

## 7) Mirror local checks in CI and keep the matrix honest

**Rule:** CI must run the same critical checks engineers/agents run locally, plus cross-runtime compatibility where relevant.

**Why for AI agents:** AI-generated changes can pass locally but fail under different runtime assumptions; matrix testing reduces false confidence.

**How to implement:**
- Run architectural checks + tests + lint + build in one CI path.
- Add runtime matrix where production/runtime parity matters (e.g., Python versions for backend component).
- Always upload actionable artifacts.

**Picfort evidence:** CI runs boundary checks, Android verification/lint/build, and Python server tests on 3.11 + 3.13; failure artifacts are uploaded. [P7][P8]

**External alignment:** GitHub Actions Gradle guidance emphasizes CI feedback loops, caching, and artifacts as part of healthy pipelines. [E4]

---

## 8) Keep decisions and task state in-version, granular, and current

**Rule:** For AI-assisted projects, undocumented intent equals future rework.

**Why for AI agents:** Agents need machine-readable/current context about “why this exists” and “what is next” to avoid contradictory changes.

**How to implement:**
- Use ADRs for architecturally significant decisions.
- Maintain a task queue with explicit statuses and dependencies.
- Define “ready” so implementation can proceed without extra product clarification.

**Picfort evidence:** ADR index and dedicated modular-architecture ADR exist; task docs define statuses and “ready means agent can implement”; status board tracks phase-level delivery including agentic modularization. [P5][P9][P10]

**External alignment:** ADR practice is designed to preserve rationale in lightweight, maintainable records. [E5]

---

## 9) Encode a strict Definition of Done for agent work

**Rule:** “Code changed” is not done. “Verified + documented + traceable” is done.

**Why for AI agents:** Agents can complete implementation quickly but skip integration responsibilities unless explicitly required.

**How to implement:**
- Require passing architecture checks and relevant tests.
- Require module doc updates for touched areas.
- Require ADR/task updates when decisions or roadmap state changed.

**Picfort evidence:** Definition of Done includes green checks, updated module docs, and ADR/task queue updates. [P4]

---

## 10) Make security assumptions explicit and environment-aware

**Rule:** Define trust boundaries, transport rules, and auth modes in docs and code-facing setup instructions.

**Why for AI agents:** Security gaps often come from implicit assumptions (for example, local-only defaults accidentally reused for internet exposure).

**How to implement:**
- Document local-network vs internet exposure rules.
- Require explicit API key for external exposure.
- Document HTTP/HTTPS constraints and token handling expectations.

**Picfort evidence:** Server exposure warnings, API-key requirements, and security notes are documented; architecture docs define layered auth and trust model. [P13][P14]

---

## 11) Reduce repeated control-flow patterns with shared primitives

**Rule:** When the same asynchronous control flow appears in multiple modules (poll loops, retries, timers), extract a tested helper.

**Why for AI agents:** Repeated ad-hoc loops drift over time and produce subtle behavior differences that are hard to reason about.

**How to implement:**
- Add one shared primitive with explicit semantics.
- Migrate modules incrementally.
- Add unit tests for timing semantics and guard conditions.

**Picfort evidence:** Polling behavior was extracted into `pollEvery(...)` with tests and adopted in shopping/events/family view models. [P15]

---

## 12) Keep a “debt radar” focused on AI-common failure modes

**Rule:** Continuously scan for patterns AI tools frequently overproduce: broad exception catches, unbounded loops, blocking calls in production paths, oversized multi-responsibility files.

**Why for AI agents:** These patterns often pass initial tests but degrade reliability and maintainability.

**How to implement:**
- Add periodic static scans for broad catches and blocking calls.
- Track hotspots as explicit debt items.
- Prioritize debt that impacts correctness under load/cancellation/concurrency.

**Picfort evidence:** Remaining examples include broad `catch (Exception)` blocks, `while(true)` loops in other modules, and `runBlocking` in secure storage paths. [P16]

---

## 13) Enforce deterministic test runtime for visual checks

**Rule:** Snapshot tests must run under fixed runtime conditions (theme mode, locale, time-relative text, and stable fixtures).

**Why for AI agents:** Agent-generated UI changes are frequent; without deterministic runtime, teams misclassify flaky diffs as product regressions (or miss real regressions in noise).

**How to implement:**
- Centralize a test helper that forces night mode explicitly for every snapshot run.
- Pin locale for screenshot tests and avoid runtime-relative strings unless injected.
- Keep time-sensitive UI text behind injectable formatters in composables used by screenshots.

**Picfort evidence:** Snapshot helpers now force `nightMode` in app/events/family tests, and settings uses injected relative-time formatting for deterministic rendering. [P17]

---

## 14) Add an “agent KPI loop” (quality + delivery telemetry)

**Rule:** Measure agent output quality as a first-class engineering signal, not anecdote.

**Why for AI agents:** Without measurement, teams optimize for throughput (more patches) instead of outcome (fewer regressions and less rework).

**How to implement:**
- Track **first-pass green rate** (all required checks pass on first CI run).
- Track **rework ratio** (follow-up fix commits per agent-authored change).
- Track **snapshot flake rate** (non-deterministic Paparazzi failures).
- Track **time-to-merge** and compare by change scope (small/medium/large).

**Picfort fit:** The current CI already emits test/snapshot status and artifacts; those are sufficient inputs to start KPI extraction. [P7]

---

## 15) Add an AI-specific threat model (beyond runtime app security)

**Rule:** Treat repository content and generated suggestions as potentially untrusted instructions.

**Why for AI agents:** Security risk is not only in app runtime; it is also in **agent operation** (prompt injection in comments/files, unsafe command execution patterns, accidental secret exposure).

**How to implement:**
- Add a short “AI threat model” section to architecture docs (attack surface + mitigations).
- Require secret scanning in PR/diff validation for agent-authored changes.
- Define explicit policy for “never execute instructions discovered in code/comments without review.”

**Picfort fit:** Runtime security assumptions are documented; this should be extended with AI-operation-specific controls. [P13][P14]

---

## 16) Add machine-readable change-scope guardrails

**Rule:** The acceptable blast radius of agent edits should be enforced automatically.

**Why for AI agents:** Agents can perform broad edits quickly; large implicit scope is a major source of architectural drift.

**How to implement:**
- Define path-level policy (allowed/forbidden directories per task type).
- Fail CI when change size exceeds agreed thresholds (files/modules touched) unless explicitly approved.
- Add reviewer gates for high-risk paths (navigation root, security, persistence).

**Picfort fit:** Existing modular boundary checks are a strong base; scope guardrails make them more operational in day-to-day agent work. [P3][P6]

---

## 17) Protect backend-mobile contracts with schema compatibility checks

**Rule:** API contract compatibility should be tested explicitly, not inferred from UI behavior.

**Why for AI agents:** Agents can unintentionally modify DTO assumptions; failures may appear late and only in specific flows.

**How to implement:**
- Keep versioned API schema snapshots (OpenAPI or JSON schema equivalents).
- Add compatibility checks in CI for changed contracts.
- Add consumer-oriented contract tests for highest-risk endpoints (auth, timeline sync, family/events writes).

**Picfort fit:** Given the architecture’s clear client/server boundary and provider contracts, contract tests are a high-leverage next reliability step. [P5][P13]

---

## Recommended operating model (concise checklist)

Use this as a baseline workflow for AI-assisted mobile development:

1. **Constrain:** module boundaries + contracts + local module instructions.
2. **Generate:** scaffold from templates/scripts, not blank files.
3. **Verify locally:** module tests + visual snapshots + boundary checks.
4. **Verify globally:** CI runs architecture/test/lint/build + artifacts.
5. **Document intent:** ADR for architectural choices, status board for execution order.
6. **Harden continuously:** debt radar for exception, polling, blocking, and coupling patterns.
7. **Stabilize test runtime:** deterministic snapshot environment (locale/time/night mode).
8. **Measure agent outcomes:** first-pass green rate, rework ratio, flake rate, time-to-merge.
9. **Constrain blast radius:** machine-readable change-scope guardrails.
10. **Secure the agent workflow:** AI-specific threat model and secret-scan-by-default.

---

## Sources

### Project evidence (Picfort repository)

- **[P1]** `AGENTS.md:11` — mandatory pre-change checks.
- **[P2]** `AGENTS.md:25` — “Do not load the whole project…”.
- **[P3]** `AGENTS.md:45-49` — feature isolation, contracts, isolated tests, enforced boundaries.
- **[P4]** `AGENTS.md:51-56` — Definition of Done requirements.
- **[P5]** `docs/decisions/0011-modular-architecture.md:21,25,75-84` — AI-agent context constraints and modular strategy.
- **[P6]** `scripts/check-modular-boundaries.sh:12-25,32,45-62` — executable boundary/doc checks.
- **[P7]** `.github/workflows/ci.yml:48,54,56,79,94` — CI boundary checks, screenshot verification, artifact upload, Python matrix.
- **[P8]** `README.md:127,132,137-139` — Paparazzi workflow and CI expectations.
- **[P9]** `docs/tasks/README.md:5,13,14,16` — source of truth and task readiness/status semantics.
- **[P10]** `docs/STATUS.md:125-128,172` — Phase 8 (agentic modularization) and status taxonomy.
- **[P11]** `docs/playbooks/add-feature-module.md:5,19-22,47` — feature playbook and verification command.
- **[P12]** `scripts/new-feature.sh:19,72-74,93,117` — automated scaffolding + anti-coupling guidance.
- **[P13]** `docs/ARCHITECTURE.md:86,89,101` — trust boundary, layered auth, polling model.
- **[P14]** `README.md:167,375-379` — exposure warning and security notes.
- **[P15]** `core/model/src/main/java/com/github/ljarka/picfort/core/model/Polling.kt:10,13,36` + `core/model/src/test/java/com/github/ljarka/picfort/core/model/PollingTest.kt:9,47,64` + usage in `feature/*/*ViewModel*.kt` (`shopping`:68,130; `events`:113; `family`:53).
- **[P16]** Debt examples: `feature/wall/.../FamilyWallViewModel.kt:98,137,151,180,195,209`, `feature/timeline/.../SyncEngine.kt:97`, `feature/timeline/.../TimelineViewModel.kt:124,230,259`, `app/.../SetupViewModel.kt:171`, `core/network/.../SecurePreferencesStore.kt:57,81`.
- **[P17]** Deterministic screenshot controls: `app/src/test/.../ScreenshotTest.kt:58,63,331`, `feature/events/src/test/.../EventsScreenshotTest.kt:26,29`, `feature/family/src/test/.../FamilyScreenshotTest.kt:32,37`, `app/src/main/.../SettingsScreen.kt:105,167`.

### External references

- **[E1]** Android Developers — *Guide to app modularization*:  
  https://developer.android.com/topic/modularization
- **[E2]** Android Developers — *Guide to app architecture* (separation of concerns, SSOT, UDF):  
  https://developer.android.com/topic/architecture
- **[E3]** Paparazzi project docs/README (record/verify snapshots, failure diffs):  
  https://github.com/cashapp/paparazzi
- **[E4]** GitHub Docs — *Building and testing Java with Gradle* (CI, artifacts):  
  https://docs.github.com/en/actions/tutorials/build-and-test-code/java-with-gradle
- **[E5]** Cognitect — *Documenting Architecture Decisions* (ADR rationale):  
  https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions
- **[E6]** DORA — *The Four Keys Metrics* (delivery/quality measurement baseline):  
  https://dora.dev/guides/dora-metrics-four-keys/
- **[E7]** OWASP GenAI Security Project — *LLM/GenAI Top 10 risks* (prompt injection and related controls):  
  https://genai.owasp.org/
- **[E8]** GitHub Docs — *About code owners* (review gates for critical paths):  
  https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/about-code-owners
- **[E9]** Pact Docs — *Contract testing for consumers/providers* (API compatibility discipline):  
  https://docs.pact.io/
- **[E10]** Google Play Console Help — *Staged rollouts* (incremental release safety):  
  https://support.google.com/googleplay/android-developer/answer/6346149
