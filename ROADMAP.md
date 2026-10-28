# CodePad prioritized delivery roadmap (2026-10-29)

## P0: correctness, data safety and language support
- Syntax highlighting: initial live integration (PHP, Java, Kotlin, JS/TS, Python, C/C++, JSON, SQL, HTML/CSS, etc.); language-specific lexing and performance tests still needed.
- Encoding: detect BOM (UTF-8, UTF-16LE/BE), configurable decoder and save encoding; never silently replace invalid bytes or modify BOM / CRLF. NOT IMPLEMENTED YET.
- Execution/compilation: Android sandboxes and platform-specific runtimes required. Do not run arbitrary downloaded code inside app process; no PHP or Java runtime is bundled. NOT IMPLEMENTED.
- Autosave recovery: verify crash recovery, atomic writes, title/token collisions, and edits during save. NOT VERIFIED.

## P1: editor productivity
- Language-aware autocomplete, folding, indentation; regex search/replace correctness; high-performance editor for big files.
- Line endings, multiple encodings, go to line, word wrap, selections, keyboard shortcuts.
- File tree, recent folders, document providers and safe save as.

## P2: visual experience
- App icon adaptive Android and platform-specific variants.
- Light, dark and system theme with persistent setting, minimalist toolbar and document tabs.
- Accessibility, RTL text, tablet layouts.

## P3: verified platform delivery
- Android debug and signed release with on-device tests and instrumented tests.
- Desktop Windows/macOS packaging, native file operations, smoke tests.
- iOS Xcode project, Files and iCloud integration, tests.
- Release checklist and manual QA.

A green Android build only confirms compile/package, not runtime quality.
