# Specification: Implement Webview Paging Mode

## Overview
Introduce a new "Webview Paging" mode for novel reading in Tadami. This mode will leverage Webview pagination logic, similar to the `lnreader` app, to provide a structured and efficient reading experience for larger novel chapters.

## Functional Requirements
- **New Reader Mode:** Add a selectable "Webview Paging" mode in the reader settings.
- **Pagination Logic:** Implement logic to calculate and render chapter content into paginated views within a Webview.
- **Navigation Controls:** Support standard tap/swipe navigation for moving between pages.
- **Adaptation:** Adapt logic from `lnreader` to fit Tadami's multimodule DDD architecture and Compose-based UI.
- **Metadata Integration:** Ensure correct handling of novel metadata, illustrations, and styling within the paginated Webview.

## Acceptance Criteria
- Users can switch to "Webview Paging" mode in the reader settings.
- Chapter content is correctly paginated and rendered in the Webview.
- Navigation between pages is smooth and intuitive.
- Illustrations are correctly displayed within the paginated flow.
- The implementation adheres to Tadami's architecture and performance standards.
