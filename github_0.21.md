### Changes in this Release

**🎨 Visuals & UI**
*   **Splash Screen:** Restored PNG logo for better stability and scaling; fixed static colors.
*   **Themes:** Polished Doom, Cloudflare, Matrix, and Sapphire themes; added Android 13+ monochrome icon support.
*   **Reader:** Cleaned up the seekbar UI by removing page indicator dots.
*   **Navigation:** Updated tab names and fixed translations for "Navigation Style".

**🛠 Fixes**
*   **Covers:** Resolved flickering issues and optimized caching for cover images by using the `MangaCover` model and stable cache keys.
*   **Extensions:** Fixed empty language bugs in filters and lists; resolved initialization race conditions by reverting async init.
*   **Migration:** Simplified extension repo migration logic.

**✨ New Features**
*   **App Updates:** Added configurable check intervals for application updates in settings.
*   **Performance:** Performance improvements via updated baseline profiles for all architectures.
