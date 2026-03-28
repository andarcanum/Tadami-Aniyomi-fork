# Stack Boundaries

The project is intentionally mixed stack, but the boundaries should be obvious.

## Compose-First Surface

- `app`, `presentation-core`, and `presentation-widget` are the preferred UI surface.
- New screens should default to Compose, Voyager, and shared presentation components.
- Shared feature logic should live in `domain` or `data`, not in the screen layer.

## Legacy Bridge Surfaces

Some platform constraints still justify older APIs:

- `ReaderActivity` and `PlayerActivity` still host parts of the reader/player pipeline.
- `SettingsSecurityScreen` still crosses into `FragmentActivity` for authentication prompts.
- `MangaDownloadAdapter` still uses `FlexibleAdapter` for a legacy list implementation.
- Extension preference screens still bridge to AndroidX preference fragments.

These are compatibility layers, not the desired default for new code.

## Shared-Core Bridges

- `core/common` should stay focused on shared networking, preferences, and utility helpers.
- RxJava and OkHttp remain part of that bridge layer because the source APIs still depend on them.
- JS engine selection is intentionally hidden behind runtime facades so callers do not depend on engine-specific details.

## Cleanup Rule

When adding new behavior:

1. Prefer Compose and shared presentation code.
2. Keep legacy bridge code in one place.
3. Hide dependency-specific implementation details behind narrow facades.
4. Document any new bridge so it does not look accidental later.
