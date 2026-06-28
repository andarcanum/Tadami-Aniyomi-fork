# Original User Request

## Initial Request — 2026-06-28T19:20:13+05:00

Redesign and implement a robust, test-backed multi-language dictionary lookup engine for the novel reader in Tadami. The engine must support looking up terms in any source language (e.g., Japanese, Chinese, Russian, English) and returning definitions in the user's chosen target language (e.g., Russian, English).

Working directory: d:/lnreader/Tadami/ranobe-aniyomi/.worktrees/ranobe-novel
Integrity mode: development

## Requirements

### R1. Multi-Language Engine Routing
The engine must support looking up terms in various source languages and returning definitions in the user's chosen target language. It must handle cases where the source and target languages differ, as well as when they are the same.

### R2. Stable Dictionary Engine Selection
Select and integrate one or more highly stable and well-documented dictionary sources (e.g., Wiktionary MediaWiki Action API, free dictionary APIs, or a hybrid approach). The engine must be resilient against rate limiting, 403 Forbidden, and 501 Not Implemented errors.

### R3. Clean Parsing & Formatting
All definitions returned by the engine must be cleanly parsed, stripped of any HTML, Wiki syntax, JSON markup, or metadata, and formatted as a clean list of bullet points. The text must be optimized for text-to-speech (TTS) engines to read without pronouncing code snippets or formatting tags.

### R4. Graceful Fallback Logic
If a definition is unavailable in the requested target language, the engine must fall back gracefully to a sensible default (e.g., English) or return a clean, user-friendly "not found" state rather than failing or crashing.

### R5. Comprehensive Unit Testing
Implement unit tests in the codebase that verify the dictionary lookup and parsing logic for multiple language pairs, including:
- English word to Russian definitions
- Russian word to English definitions
- Japanese word to Russian/English definitions
- Edge cases (empty query, very long query, special characters)

## Acceptance Criteria

### Compilation & Tests
- [ ] The project compiles successfully using `./gradlew compileDebugKotlin --no-daemon`.
- [ ] All newly added unit tests for the dictionary engine pass successfully.

### Functional Behavior
- [ ] A lookup for an English word (e.g., "great") with target language "ru" returns clean Russian definitions.
- [ ] A lookup for a Russian word (e.g., "замечательный") with target language "en" returns clean English definitions.
- [ ] A lookup for a Japanese word (e.g., "走る") with target language "ru" returns definitions in Russian, or falls back cleanly to English.
- [ ] The output does not contain any raw HTML tags, Wiki syntax (like `[[link]]` or `{{template}}`), or JSON characters.
- [ ] The UI displays the attribution (e.g., "Source: Wiktionary") correctly.
