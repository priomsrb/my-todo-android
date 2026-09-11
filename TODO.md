# TODO — My Todo

Roadmap for a Google Keep-inspired Android TODO app backed by markdown files.
Conventions and invariants live in [AGENTS.md](AGENTS.md). Tick boxes as work lands.

## Phase 0 — Skeleton ✅

- [x] Gradle project (AGP 9.2.1 built-in Kotlin 2.3.21, Compose BOM, minSdk 26 / target 37)
- [x] Keep-inspired Material 3 theme (yellow accent, note tints, light + dark)
- [x] Tree model with unbounded nesting + `flattenVisible()` and tree-edit helpers
- [x] `TodoRepository` interface with an in-memory implementation and sample data
- [x] Home screen: Keep-style card grid of lists, with previews and done counts
- [x] List screen: recursively indented rows, checkbox toggling, delete, add item
- [x] Ticked items render grayed out and struck through
- [x] Settings screen placeholder + navigation
- [x] Unit tests for the tree helpers
- [x] `AGENTS.md` and `TODO.md`

## Phase 1 — Markdown files + storage location

The first phase that makes the app genuinely useful. Each list is one `.md` file in a folder the
user picks; the format is the one specified in AGENTS.md.

- [ ] `MarkdownParser`: tab-indented `- [ ]` / `- [X]` → `List<TodoItem>`; tolerate space indents,
      lowercase `x`, blank lines and stray text
- [ ] `MarkdownSerializer`: tree → markdown, always tabs, `- [X]` uppercase
- [ ] Round-trip unit tests, including a deeply nested file and a hand-messy file
- [ ] Settings: folder picker via `ACTION_OPEN_DOCUMENT_TREE` + `takePersistableUriPermission`
- [ ] Persist the folder URI in DataStore; show a friendly path in settings
- [ ] `MarkdownTodoRepository` implementing `TodoRepository` over SAF `DocumentFile`s
- [ ] One file per list; creating/renaming/deleting a list creates/renames/deletes its file
- [ ] Derive a list's id from its filename, so it is stable across restarts — the launcher widget's
      deep links (Phase 5b) and widget configs depend on that
- [ ] Debounced autosave after edits (~500ms) with save-on-pause
- [ ] Reload when a file changed underneath the app (on resume; `ContentObserver` if practical)
- [ ] Handle a lost/revoked folder permission gracefully
- [ ] Swap the app's repository from in-memory to markdown-backed

## Phase 2 — Expand / collapse

- [ ] Chevron on every item with children; tap toggles `collapsed`
- [ ] Show a descendant count on collapsed parents
- [ ] Persist collapse state locally, keyed by item id — never written into the `.md`
- [ ] Collapse-all / expand-all in the list overflow menu

## Phase 3 — Drag and drop

The headline interaction: reorder vertically *and* re-nest horizontally, always moving the subtree.

- [ ] Long-press drag in `LazyColumn`, using the existing drag handle
- [ ] Vertical reorder with live gap/placeholder feedback
- [ ] Horizontal drag to indent/outdent; target depth follows the drag's x offset
- [ ] Legal-depth rules: an item may nest at most one level deeper than the row above it
- [ ] The dragged item carries all of its descendants
- [ ] Auto-collapse the dragged subtree while dragging, restore on drop
- [ ] Auto-scroll when dragging near the top/bottom edges
- [ ] Haptics on pick-up and drop
- [ ] Unit tests for the move logic (source index + target depth → new tree)

## Phase 4 — Keep polish

- [ ] Fast inline entry: type, Enter starts the next item, Tab/Shift-Tab indents/outdents
- [ ] Tap an item's text to edit it in place
- [ ] Swipe to delete with an undo snackbar
- [ ] Per-list color (Keep's palette), stored locally
- [ ] "Hide completed" toggle, and move-completed-to-bottom
- [ ] Search across lists
- [ ] Rename a list (and its file) from the list screen
- [ ] Empty states and a proper app icon

## Phase 5 — Home-screen widgets

Two separate widgets, both Glance-based.

### 5a — List widget (view + edit one list)

- [ ] Glance `GlanceAppWidget` showing one chosen list
- [ ] Scrollable item list in the widget, indented to show nesting
- [ ] Tick items off directly from the widget, writing through to the file
- [ ] Configuration activity to pick which list the widget shows
- [ ] Refresh the widget when the underlying file changes, and vice versa
- [ ] Resizable, with a sensible minimum size
- [ ] Tapping the title opens that list in the app

### 5b — Launcher widget (pick a list to open)

- [ ] Glance widget listing every list, like the app's home grid in miniature
- [ ] Tapping a list opens it directly in the app (deep link into `list/{listId}`)
- [ ] Stays in sync as lists are created, renamed and deleted
- [ ] Scrollable when there are more lists than fit

## Phase 6 — Robustness and extras

- [ ] Conflict handling when a file changed on disk while edits were pending
- [ ] Undo/redo stack for structural edits
- [ ] Sort options (manual, alphabetical, completed last)
- [ ] Export/share a list as markdown
- [ ] Instrumented UI tests for the drag interaction
- [ ] Release build config + signing notes

## Decisions

- **A list's name comes from its filename alone.** No `# Heading` is read from or written to the
  file; renaming a list renames its file.
- **Completed items stay in the file as `- [X]`** indefinitely. Archiving them out is a possible
  future feature, not a current one.
- **Two widgets, not one** (see Phase 5): one edits a single list, one picks a list to open.
