# Meerkat Reader Technical Whitepaper

## 1. Executive Summary

Meerkat Reader has already evolved beyond a basic RSS client. The current codebase includes multi-provider feed sync, local feeds, full-content extraction, article image caching, backup and restore, configurable reader UI, home widgets, notifications, audio playback, rule-based article automation, and manual AI actions for translation, summaries, and key points.

The next product phase should focus on turning those individual capabilities into a coherent reading intelligence layer. The goal is not to add heavyweight infrastructure or broad dependencies. The goal is to make high-volume RSS reading easier by combining AI assistance, stronger filtering, advanced search, reliable offline reading, portable backups, selective external integrations, and improved reader ergonomics.

This document proposes the target behavior and technical design for seven feature areas:

1. AI reading workflows
2. Smart rules and filters
3. Advanced search
4. Complete offline reading
5. Enhanced backup and restore
6. External service integrations
7. Reader experience improvements

The recommended implementation model is incremental. Each feature should work with local accounts and remote accounts unless a backend limitation makes that impossible. Local data should remain the source of truth for user-facing UI state, while remote services should be treated as synchronization targets where possible.

## 2. Current Baseline

The current project already provides the following relevant foundations:

- Article model, feed model, account delegates, and SQLDelight persistence in the `capy` module.
- Android UI, settings, workers, widgets, AI, backup, and image cache implementation in the `app` module.
- Manual AI actions through `ArticleAiRepository`, using OpenAI-compatible `/chat/completions` requests.
- AI settings for provider, base URL, API key, model, language, translation display mode, and custom prompts.
- Article automation through `ArticleAutomation` and `ArticleAutomationRule`.
- Rule actions for mute, keep, mark read, star, categorize, and notify.
- Backup export and restore through `CapyBackupFile`.
- Article image cache and preloading workers.
- OPML import/export and starred bookmark export.

This baseline means the next phase should avoid rewrites. Most work can be delivered by extending the existing repositories, settings panels, SQLDelight schema, account delegates, and worker patterns.

## 3. Product Goals

### 3.1 Primary Goals

- Reduce the time required to process large unread queues.
- Make AI output useful inside the article list, article detail, and daily reading flow.
- Let users control noise through explainable rules and filters.
- Make offline reading reliable enough for travel and weak network conditions.
- Keep user data portable and recoverable.
- Preserve the app's lightweight Android-first architecture.

### 3.2 Non-Goals

- Build a hosted Meerkat cloud service in this phase.
- Force AI usage or automatically send article content to third-party providers by default.
- Replace remote account services such as Feedbin, FreshRSS, Miniflux, or Google Reader-compatible servers.
- Add large cross-platform frameworks or new storage engines.
- Build a full knowledge-management product inside the reader.

## 4. Design Principles

- Manual before automatic: expensive or privacy-sensitive actions should start as explicit user actions.
- Local-first UI state: cache AI results, rules, search metadata, and offline package state locally.
- Provider-neutral AI: keep OpenAI-compatible APIs as the default contract and isolate provider details.
- Explainability: any automatic action should have a visible reason and rule source.
- Account parity: features should work across local and remote accounts unless a remote API cannot support the operation.
- Incremental migration: use SQLDelight migrations for durable data and SharedPreferences only for simple settings.
- Failure visibility: background work should expose retryable failures without blocking normal reading.

## 5. Proposed Architecture

### 5.1 High-Level Components

```text
UI
  Article list
  Article detail
  Settings
  Rules editor
  Search screen

Application services
  ArticleAiRepository
  ArticleAutomation
  SearchRepository
  OfflinePackageRepository
  BackupRepository
  IntegrationRepository

Background work
  AI digest worker
  Offline download worker
  Cache cleanup worker
  Backup worker
  Integration sync worker

Persistence
  SQLDelight article data
  AI results
  rule match logs
  search index metadata
  offline package state
  integration export state

Account delegates
  Local
  Feedbin
  FreshRSS / Google Reader API
  Miniflux
```

### 5.2 Module Boundaries

- `capy`: durable domain model, persistence records, account delegates, automation evaluation, search query model, and account-agnostic business logic.
- `app`: Android UI, settings, workers, notification integration, network clients, AI provider calls, file export/import, and Android-specific cache paths.
- Client modules such as `feedbinclient`, `minifluxclient`, and `readerclient`: remote API adapters only.

The AI transport may remain in `app` because it depends on Android settings and OkHttp wiring. Durable AI result models and cache metadata can be moved into `capy` if they need SQLDelight persistence.

## 6. Feature Area 1: AI Reading Workflows

### 6.1 Goals

- Provide article-level summaries, translations, and key points.
- Surface AI results in the article list when useful.
- Generate daily or weekly digests from unread articles.
- Support article Q&A.
- Keep AI use explicit, controllable, cached, and privacy-transparent.

### 6.2 User-Facing Behavior

- Article detail shows AI actions: Translate, Summarize, Key Points, Ask.
- Article list can optionally show a one-line AI summary preview.
- Users can regenerate AI output.
- Users can create a digest from the current filter, folder, feed, or unread queue.
- AI settings expose provider, base URL, API key, model, language, prompts, and output preferences.
- AI actions remain disabled until explicitly enabled.

### 6.3 Data Model

Add a durable AI result table instead of relying only on files:

```sql
CREATE TABLE article_ai_results (
  id TEXT NOT NULL PRIMARY KEY,
  article_id TEXT NOT NULL,
  action TEXT NOT NULL,
  provider TEXT NOT NULL,
  base_url TEXT NOT NULL,
  model TEXT NOT NULL,
  language TEXT NOT NULL,
  prompt_hash TEXT NOT NULL,
  content_hash TEXT NOT NULL,
  result_text TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE UNIQUE INDEX article_ai_results_unique_input
ON article_ai_results(
  article_id,
  action,
  provider,
  base_url,
  model,
  language,
  prompt_hash,
  content_hash
);
```

For digest results:

```sql
CREATE TABLE ai_digests (
  id TEXT NOT NULL PRIMARY KEY,
  filter_json TEXT NOT NULL,
  provider TEXT NOT NULL,
  model TEXT NOT NULL,
  language TEXT NOT NULL,
  article_ids_json TEXT NOT NULL,
  result_text TEXT NOT NULL,
  created_at INTEGER NOT NULL
);
```

### 6.4 Repository Design

Introduce a small set of domain types:

```kotlin
enum class ArticleAiAction {
    TRANSLATE,
    SUMMARIZE,
    KEY_POINTS,
    LIST_PREVIEW,
    DIGEST,
    QUESTION,
}

data class ArticleAiRequest(
    val action: ArticleAiAction,
    val article: Article,
    val question: String? = null,
    val forceRefresh: Boolean = false,
)
```

`ArticleAiRepository` should:

- Normalize article text through `ArticleAiContent`.
- Compute `contentHash` and `promptHash`.
- Check SQL cache before network calls.
- Fall back to file cache only during migration if needed.
- Call provider through a transport abstraction.
- Store successful results in SQLDelight.
- Return typed failures for disabled AI, missing API key, empty content, provider errors, rate limits, and timeout.

### 6.5 Provider Transport

Add a transport interface:

```kotlin
interface AiChatClient {
    suspend fun complete(request: AiChatRequest): Result<String>
}
```

Initial implementation:

- `OpenAiCompatibleChatClient`

Future implementation:

- `StreamingOpenAiCompatibleChatClient`
- provider-specific defaults for DeepSeek or other providers

Do not add provider SDK dependencies. The current OkHttp + kotlinx.serialization approach is sufficient and easier to audit.

### 6.6 Long Article Handling

Long content should be handled in phases:

1. Hard cap input to a configurable maximum for MVP.
2. Add chunking for summaries and digests.
3. Use map-reduce prompting:
   - summarize chunks
   - summarize the chunk summaries

Translations should avoid chunking initially unless paragraph boundaries can be preserved.

### 6.7 Background AI

AI background work should be opt-in and bounded:

- Only on Wi-Fi by default.
- Optional charging requirement.
- Daily article limit.
- Per-run token/character limit.
- Never run if API key is missing.
- Never run for feeds excluded from AI.

Workers:

- `ArticleAiPreviewWorker`: generates list previews for unread articles.
- `ArticleAiDigestWorker`: generates a digest for selected filters.

### 6.8 Privacy and Security

- Show clear privacy copy before enabling AI.
- Do not send article content unless the user triggers or enables the action.
- Store API keys in Android encrypted storage if available.
- Exclude API keys from regular backup by default.
- Provide a "clear AI cache" action.
- Allow feed-level AI exclusion.

## 7. Feature Area 2: Smart Rules and Filters

### 7.1 Goals

- Turn current rules into a reliable automation system.
- Let users understand why an article was muted, marked read, starred, categorized, or notified.
- Support richer matching while keeping common cases simple.
- Prepare for AI-assisted rules without making AI mandatory.

### 7.2 Current Rule Model

Current rules include:

- Fields: any, feed, author, title, content.
- Pattern matching with plain text or `/regex/`.
- Actions: mute, keep, mark read, star, categorize, notify.

### 7.3 Proposed Rule Model

Extend rules to support multiple conditions:

```kotlin
data class ArticleAutomationRule(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val matchMode: RuleMatchMode,
    val conditions: List<ArticleRuleCondition>,
    val actions: Set<ArticleRuleAction>,
    val categoryName: String,
    val priority: Int,
)

enum class RuleMatchMode {
    ALL,
    ANY,
}

data class ArticleRuleCondition(
    val field: ArticleRuleField,
    val operator: ArticleRuleOperator,
    val value: String,
)

enum class ArticleRuleOperator {
    CONTAINS,
    NOT_CONTAINS,
    REGEX,
    EQUALS,
    STARTS_WITH,
    ENDS_WITH,
}
```

Keep backward compatibility by migrating existing `pattern` rules into one condition.

### 7.4 Rule Match Log

Add a local log table:

```sql
CREATE TABLE article_rule_matches (
  id TEXT NOT NULL PRIMARY KEY,
  article_id TEXT NOT NULL,
  rule_id TEXT NOT NULL,
  rule_name TEXT NOT NULL,
  actions_json TEXT NOT NULL,
  matched_at INTEGER NOT NULL,
  explanation TEXT NOT NULL
);

CREATE INDEX article_rule_matches_article_id
ON article_rule_matches(article_id);
```

This enables:

- "Why was this article hidden?"
- Debugging user-created rules.
- Future UI for reviewing automation.

### 7.5 User Interface

Rules settings should support:

- Create rule.
- Edit rule.
- Enable/disable rule.
- Delete rule.
- Reorder rule priority.
- Test rule against sample text.
- Create rule from article.

Article detail and row menu should support:

- "Create rule from this feed"
- "Mute articles like this"
- "Notify for this author"
- "Show automation history"

### 7.6 Remote Account Behavior

Rules should evaluate locally after refresh for all account types.

Actions:

- Mute: remove locally and mark read remotely where possible.
- Mark read: apply local status and enqueue remote status sync.
- Star: apply local status and enqueue remote status sync.
- Categorize: use local saved search unless remote service supports labels safely.
- Notify: create local notification.

If a remote service cannot support a specific action, the local result should still apply and the UI should not claim remote sync.

### 7.7 AI-Assisted Rules

AI rules should be a second phase:

- User creates a natural-language criterion, such as "notify me about Android security updates".
- The app sends title, feed, author, and summary to AI.
- AI returns a strict JSON decision.
- The result is logged with explanation.

This should be opt-in per rule because it can increase cost and send article metadata to a provider.

## 8. Feature Area 3: Advanced Search

### 8.1 Goals

- Make search useful for large archives.
- Support precise queries without forcing a complex UI.
- Allow saved complex searches.
- Reuse existing saved search and article filter concepts where possible.

### 8.2 Query Syntax

Support simple text plus field qualifiers:

```text
android security is:unread feed:blog.google after:2026-01-01
```

Initial qualifiers:

- `is:unread`
- `is:read`
- `is:starred`
- `is:saved`
- `feed:<text>`
- `folder:<text>`
- `author:<text>`
- `title:<text>`
- `after:YYYY-MM-DD`
- `before:YYYY-MM-DD`
- `has:image`
- `has:audio`

### 8.3 Parser Design

Add a small parser in `capy`, not a heavy query language dependency:

```kotlin
data class ArticleSearchQuery(
    val text: String,
    val status: ArticleStatus?,
    val feed: String?,
    val folder: String?,
    val author: String?,
    val title: String?,
    val afterEpochSeconds: Long?,
    val beforeEpochSeconds: Long?,
    val hasImage: Boolean?,
    val hasAudio: Boolean?,
)
```

Parsing should be forgiving:

- Unknown qualifiers remain part of plain text.
- Quoted values are supported later.
- Invalid dates are ignored and surfaced as UI hints.

### 8.4 Persistence and Indexing

For MVP, SQL `LIKE` queries may be enough. For larger archives, use SQLite FTS:

```sql
CREATE VIRTUAL TABLE article_search_fts
USING fts5(
  article_id UNINDEXED,
  title,
  author,
  summary,
  content
);
```

FTS maintenance options:

- Update index during article insert/update.
- Rebuild index from settings if corrupted.

### 8.5 UI

- Keep the top-bar search for simple use.
- Add an advanced search screen only when syntax or filters are expanded.
- Show query chips for recognized qualifiers.
- Allow saving the current query as a saved search.
- Allow batch actions on results.

## 9. Feature Area 4: Complete Offline Reading

### 9.1 Goals

- Make selected content readable without network.
- Make cache state visible.
- Avoid unbounded storage growth.
- Reuse existing image cache and full-content cache work.

### 9.2 Offline Package Concept

An offline package is a local bundle for an article:

- Feed metadata
- Article metadata
- Original article HTML
- Extracted full content when available
- Rewritten local image paths
- Enclosures metadata
- Optional audio metadata

Add package state:

```sql
CREATE TABLE article_offline_packages (
  article_id TEXT NOT NULL PRIMARY KEY,
  state TEXT NOT NULL,
  include_full_content INTEGER NOT NULL,
  include_images INTEGER NOT NULL,
  include_audio INTEGER NOT NULL,
  bytes INTEGER NOT NULL DEFAULT 0,
  error_message TEXT,
  updated_at INTEGER NOT NULL
);
```

States:

- `NOT_DOWNLOADED`
- `QUEUED`
- `DOWNLOADING`
- `READY`
- `FAILED`
- `STALE`

### 9.3 Download Worker

Add `ArticleOfflinePackageWorker`:

- Processes queued articles.
- Downloads full content if requested.
- Reuses `ArticleImageDownloader`.
- Rewrites image URLs to local paths.
- Updates package state.
- Respects Wi-Fi and battery settings.

### 9.4 User Controls

Settings:

- Offline reading enabled.
- Download on Wi-Fi only.
- Include images.
- Include full content.
- Include audio.
- Storage limit.
- Cleanup policy.

Per feed:

- Always keep offline.
- Never cache offline.
- Use global setting.

Article UI:

- Offline status indicator.
- "Download for offline"
- "Remove offline copy"

### 9.5 Cleanup

Cleanup should preserve:

- Starred articles if configured.
- Saved-for-later articles.
- Recently opened articles.
- Feed-level offline articles.

Cleanup should remove:

- Old read unstarred packages.
- Failed partial packages.
- Files not referenced by package metadata.

## 10. Feature Area 5: Enhanced Backup and Restore

### 10.1 Goals

- Make user configuration portable.
- Prevent accidental credential leaks.
- Support predictable migration across app versions.
- Restore enough data to make a new install feel complete.

### 10.2 Current Backup

Current backup exports:

- Account source
- Account preferences
- App preferences
- Subscriptions OPML

### 10.3 Proposed Backup Format

Move to explicit sections:

```json
{
  "version": 2,
  "exportedAt": "...",
  "app": {
    "preferences": {}
  },
  "account": {
    "source": "LOCAL",
    "preferences": {},
    "subscriptionsOpml": ""
  },
  "rules": [],
  "savedSearches": [],
  "readLater": [],
  "starred": [],
  "ai": {
    "settings": {},
    "includeApiKey": false
  }
}
```

### 10.4 Credential Policy

Default:

- Do not export API keys.
- Do not export account passwords or tokens.

Optional:

- Allow encrypted credential export with a user-provided passphrase.
- Make this a separate explicit choice.

### 10.5 Restore Flow

Restore should:

- Validate version.
- Validate account type.
- Show a summary before applying.
- Preserve current account ID.
- Restore preferences.
- Import subscriptions.
- Restore rules and saved searches.
- Trigger refresh.

Potential modes:

- Merge into current account.
- Replace current configuration.

Start with replace mode because it is simpler and less surprising.

### 10.6 Automatic Backup

Optional later phase:

- User selects a document tree.
- App writes periodic backup.
- Keep last N backups.
- Trigger after major preference or subscription changes.

## 11. Feature Area 6: External Service Integrations

### 11.1 Goals

- Extend Meerkat Reader into existing reading workflows.
- Avoid broad integration work that duplicates current sync providers.
- Prioritize services that complement RSS reading.

### 11.2 Integration Categories

Read-it-later and knowledge services:

- Wallabag
- Pocket
- Instapaper
- Readwise

RSS sync services:

- Inoreader
- NewsBlur
- Tiny Tiny RSS

Local or self-hosted sync:

- WebDAV
- Nextcloud

### 11.3 Integration Design

Add a small integration abstraction:

```kotlin
interface ArticleExportIntegration {
    val id: String
    val displayName: String
    suspend fun save(article: Article): Result<Unit>
}
```

Each integration should live behind:

- Settings panel for credentials.
- Repository for API calls.
- Worker for retries.
- Local export-state table.

```sql
CREATE TABLE article_integration_exports (
  id TEXT NOT NULL PRIMARY KEY,
  article_id TEXT NOT NULL,
  integration_id TEXT NOT NULL,
  state TEXT NOT NULL,
  remote_id TEXT,
  error_message TEXT,
  updated_at INTEGER NOT NULL
);
```

### 11.4 Priority Order

Recommended order:

1. Wallabag, because it is self-hostable and aligns with open-source RSS users.
2. Readwise export, because it complements AI summaries and highlights.
3. WebDAV backup sync, because it improves local-account portability.
4. Inoreader or NewsBlur only if user demand is clear.

### 11.5 Error Handling

- Failed exports remain queued.
- User can retry manually.
- Authentication failures surface in settings.
- Per-article export status is visible in the action sheet.

## 12. Feature Area 7: Reader Experience Improvements

### 12.1 Goals

- Improve long-form reading quality.
- Make AI translation and summaries feel native.
- Support accessibility and low-attention reading.
- Keep the article UI fast and stable.

### 12.2 Reading Progress

Add local reading progress:

```sql
CREATE TABLE article_reading_progress (
  article_id TEXT NOT NULL PRIMARY KEY,
  scroll_percent REAL NOT NULL,
  updated_at INTEGER NOT NULL
);
```

Behavior:

- Restore last position when reopening.
- Mark read only when configured threshold is crossed.
- Sync read status separately from progress.

### 12.3 Bilingual Translation View

Current translation mode supports replace or parallel display conceptually. Improve it with paragraph-level alignment:

- Split original and translated text into paragraphs.
- Render side-by-side or stacked paired paragraphs.
- Preserve original text toggles.
- Allow copy translated paragraph.

For MVP, alignment can be best-effort by paragraph order.

### 12.4 Article Structure

Add:

- Table of contents from headings.
- Jump between headings.
- Estimated reading time.
- Image gallery view.
- Better code block/table rendering where possible.

### 12.5 Text-to-Speech

TTS should be local-first:

- Use Android TTS engine.
- Support original article, AI summary, or translated article.
- Provide play/pause/skip.
- Save per-article TTS position.

This should reuse the current media/audio UI patterns where practical.

### 12.6 Accessibility

- Keep TalkBack improvements.
- Ensure AI result cards and offline status indicators are screen-reader friendly.
- Avoid icon-only controls without content descriptions.
- Make long AI output selectable and copyable.

## 13. Cross-Cutting Data and Privacy

### 13.1 Data Sent to AI Providers

Potentially sent:

- Article title
- Article URL
- Article text content
- User question
- Prompt text

Not sent by default:

- Account credentials
- Other feed subscriptions
- Reading history outside the selected request

### 13.2 Local Sensitive Data

Sensitive values:

- AI API keys
- Integration tokens
- Account credentials

Design:

- Store credentials in encrypted storage where available.
- Never include credentials in normal backups.
- Redact credentials in logs and crash exports.

### 13.3 Cache Controls

Settings should include:

- Clear AI cache.
- Clear offline packages.
- Clear image cache.
- Clear rule match history.

## 14. Background Work Strategy

Use WorkManager for all non-immediate jobs:

- AI preview generation
- Digest generation
- Offline package download
- Cache cleanup
- Automatic backup
- Integration retries

Constraints:

- Wi-Fi only when configured.
- Charging only when configured.
- Backoff for provider errors.
- Bounded batch sizes.
- Cancellation support.

No background worker should make unbounded AI calls.

## 15. Testing Strategy

### 15.1 Unit Tests

- AI prompt rendering.
- AI cache key generation.
- AI error mapping.
- Rule condition evaluation.
- Rule migration from legacy pattern model.
- Search query parser.
- Offline package state transitions.
- Backup version parsing.

### 15.2 Integration Tests

- Local account refresh applies rules.
- Remote account refresh applies local rule actions and queues sync where needed.
- Backup export/restore preserves rules and settings.
- Offline package worker writes expected metadata.

### 15.3 UI Tests or Compose Previews

- AI settings.
- AI result cards.
- Rule editor.
- Search chips.
- Offline status indicators.
- Backup restore summary.

### 15.4 Manual QA

- Long article AI summary.
- Translation with non-English target language.
- Feed with many images offline.
- Remote account mark-read sync after rule match.
- Restore into same account type.
- Restore source mismatch handling.

## 16. Migration Plan

### Phase 1: Stabilize Current AI and Rules

- Move AI cache metadata to SQLDelight.
- Add AI cache clear action.
- Add rule edit and enable/disable.
- Add rule match log.
- Add "create rule from article".

### Phase 2: AI Reading Workflows

- Add list preview summaries.
- Add current-filter digest.
- Add article Q&A.
- Add long article chunking for summaries.
- Add bounded background generation.

### Phase 3: Search and Offline

- Add advanced search parser.
- Add saved advanced searches.
- Add offline package table and worker.
- Add per-feed offline settings.

### Phase 4: Backup and Integrations

- Add backup version 2.
- Add rules, saved searches, read-later, starred export sections.
- Add Wallabag or Readwise integration.
- Add optional automatic backup.

### Phase 5: Reader Experience

- Add reading progress.
- Improve bilingual translation view.
- Add TTS.
- Add table of contents and image gallery.

## 17. Risks and Mitigations

| Risk | Impact | Mitigation |
| --- | --- | --- |
| AI cost surprises | Users may incur unexpected provider charges | Manual defaults, batch limits, clear settings copy |
| Privacy concerns | Article content may be sent externally | Opt-in AI, feed exclusions, clear privacy notice |
| Rule mistakes hide articles | Users may miss important content | Keep action, rule logs, undo paths, test rule UI |
| Offline cache grows too large | Storage pressure | Quotas, cleanup policy, per-feed controls |
| Remote account inconsistency | Local actions may not sync cleanly | Sync queues, visible local-only actions, account-specific tests |
| Backup leaks secrets | Credential exposure | Exclude secrets by default, encrypted optional export only |
| UI complexity | Settings become hard to use | Progressive disclosure and sensible defaults |

## 18. Success Metrics

Product metrics:

- Fewer unread articles left after a reading session.
- Higher use of saved searches, rules, and AI summaries.
- Lower repeat manual actions for common filtering tasks.
- Successful offline reading without network.

Technical metrics:

- AI cache hit rate.
- AI request failure rate.
- Rule evaluation time per refresh.
- Offline package failure rate.
- Backup restore success rate.
- Crash-free sessions after feature rollout.

## 19. Recommended First Implementation Slice

The highest-impact, lowest-risk slice is:

1. Persist AI results in SQLDelight.
2. Add AI cache clearing.
3. Add list summary preview using cached summaries only at first.
4. Add rule edit and enable/disable.
5. Add rule match log.
6. Add "create rule from article".

This slice builds directly on current code, avoids new dependencies, does not require background AI generation, and makes both AI and automation more useful immediately.

## 20. Conclusion

Meerkat Reader already has the right foundations for a differentiated RSS reader. The next phase should connect those foundations into a practical reading intelligence system: AI for understanding, rules for automation, search for retrieval, offline packages for reliability, backups for portability, integrations for workflow continuity, and reader improvements for daily comfort.

The design should stay incremental and local-first. Each feature should deliver value on its own, while leaving room for deeper automation and AI-assisted workflows later.
