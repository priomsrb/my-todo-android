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

## Phase 1 — Markdown files + storage location ✅

Each list is one `.md` file in a folder the user picks; the format is the one specified in AGENTS.md.

- [x] `MarkdownParser`: tab-indented `- [ ]` / `- [X]` → `List<TodoItem>`; tolerates space indents
      (any width), `*`/`+` bullets, lowercase `x`, blank lines and stray text
- [x] `MarkdownSerializer`: tree → markdown, always tabs, `- [X]` uppercase
- [x] Round-trip unit tests, including a 50-level file and a hand-messy file
- [x] Settings: folder picker via `ACTION_OPEN_DOCUMENT_TREE` + `takePersistableUriPermission`
- [x] Persist the folder URI in DataStore; show a friendly path in settings
- [x] `MarkdownTodoRepository` over a `TodoFileStore` seam — `SafDirectoryStore` for a picked
      folder, `LocalDirectoryStore` for the app-private default and for tests
- [x] One file per list; creating/renaming/deleting a list creates/renames/deletes its file,
      wired to a long-press menu on the home grid
- [x] List ids are filenames, so they survive restarts (Phase 5b deep links depend on it)
- [x] Debounced autosave (500ms) with a flush on `onStop`
- [x] Reload on `onStart`, skipping files with edits not yet written
- [x] Lost folder permission surfaces as a warning in settings instead of silently switching files
- [x] Swap the app's repository from in-memory to markdown-backed

## Phase 2 — Expand / collapse ✅

- [x] Chevron on every item with children; tap toggles `collapsed`
- [x] Collapsed parents show how many descendants are hidden
- [x] Persist collapse state locally — **keyed by the item's text path, not its id**: ids are
      UUIDs regenerated on every parse, so they cannot survive a reload. See `CollapseKeys`
- [x] Collapse-all / expand-all in the list overflow menu
- [x] Collapse follows a list through a rename and is forgotten when a list is deleted

## Phase 3 — Drag and drop ✅

Reorder vertically *and* re-nest horizontally, always moving the subtree.

- [x] Drag the handle to start a reorder — no long press: the drag begins as soon as the finger
      moves on the handle (it waited out a long press until Phase 6)
- [x] Live feedback: the screen previews the pending move by applying `moveSubtree` to the tree, so
      the row is shown in the slot it would land in (Phase 6 keeps that preview and draws the row
      itself offset onto the finger, leaving the slot as the gap)
- [x] Horizontal drag indents/outdents; target depth follows the drag's x offset
- [x] Legal-depth rules in `allowedDepthRange`: at most one level deeper than the row above, never
      shallower than the row below, never inside a collapsed parent
- [x] The dragged item carries all of its descendants
- [x] The dragged subtree auto-collapses while it travels and restores on drop
- [x] Auto-scroll when dragging near the top/bottom edges, accelerating toward the edge
- [x] Haptics on pick-up and drop
- [x] Unit tests for the move logic, including exhaustive checks that no move ever loses,
      duplicates or mis-nests an item

## Testing infrastructure ✅

- [x] Compose UI tests on the JVM via Robolectric, in the ordinary `testDebugUnitTest` task
- [x] Regression tests for both drag bugs and the navigation-transition bug, each verified to fail
      against the code that had the bug
- [x] `TodoItemList` split out of `TodoListScreen` so the drag wiring can be driven without a
      ViewModel

## Phase 4 — Keep polish ✅

- [x] Fast inline entry: type, Enter starts the next item, Tab/Shift-Tab indents/outdents.
      Enter on a still-empty item closes the editor instead of adding another blank row, and back
      dismisses it — otherwise the only way out of an edit is to start another one
- [x] Tap an item's text to edit it in place; an item left empty is dropped when the edit ends
- [x] Swipe to delete with an undo snackbar. Undo re-inserts the subtree at the index and depth it
      was taken from, through the same `insertSubtree` a drag lands with
- [x] Per-list color (Keep's palette), stored locally in `list_prefs` — its own DataStore file, not
      merged with settings or collapse. A list with no colour of its own keeps the tint of its
      position in the grid; the chosen colour also tints the list screen
- [x] "Hide completed" toggle (a per-list view, never written to the file) and
      move-completed-to-bottom (an edit, which rewrites the file)
- [x] Search across lists, including inside collapsed subtrees; a result opens its list
- [x] Rename a list (and its file) from the list screen — the screen re-opens under the new id,
      since the file name *is* the id
- [x] Empty states (no lists, empty list, everything done, no search results) and an app icon that
      reads as a checklist and works as a monochrome themed icon

Two decisions worth knowing about before Phase 5:

- **Reordering is off while completed items are hidden.** Drag coordinates are row indices, and a
  filtered list does not have the same ones. The drag handle is hidden rather than left inert.
- **Editing an item still forgets that it was collapsed**, now on every keystroke rather than once
  per edit, because collapse keys are text paths. Still a Phase 6 item.

## Phase 5 — Home-screen widgets ✅

Two separate widgets, both Glance-based (Glance 1.2.0).

### 5a — List widget (view + edit one list) ✅

- [x] Glance `GlanceAppWidget` showing one chosen list
- [x] Scrollable item list in the widget, indented to show nesting; collapsed subtrees stay folded
      and hidden completed items stay hidden, so the widget never disagrees with the app
- [x] Tick items off directly from the widget, writing through to the file. The tap carries a
      `CollapseKeys` path key, not an item id — ids are fresh UUIDs on every parse, and a widget
      outlives the process that drew it
- [x] Configuration activity to pick which list the widget shows, stored in that widget's own
      Glance state so several can sit side by side
- [x] Refresh the widget when the underlying file changes, and vice versa: the widget reads from
      disk before drawing, and the app pushes an update (debounced) whenever the lists change
- [x] Resizable, with a sensible minimum size
- [x] Tapping the title opens that list in the app

### 5b — Launcher widget (pick a list to open) ✅

- [x] Glance widget listing every list, like the app's home grid in miniature — colour dot, name
      and done count
- [x] Tapping a list opens it directly in the app. Each row's intent carries its list id in the
      *data URI* as well as an extra: `Intent` equality ignores extras, so otherwise every row
      would share one `PendingIntent` and open whichever list was tapped first
- [x] Stays in sync as lists are created, renamed and deleted
- [x] Scrollable when there are more lists than fit

Worth knowing before Phase 6:

- **A tap on a stale row does nothing rather than the wrong thing.** A key that no longer resolves
  — because the item was edited or removed since the widget was drawn — is dropped.
- **The widget flushes its write immediately** instead of using the autosave debounce: nothing keeps
  the process alive once the tap is handled.

## Phase 5c — The voice tile  ✅

- [x] A 1x1 widget that is only a button: press it, speak, and what you said is on a list. The list
      is chosen when the tile is placed, so capture itself costs one press and no decisions
- [x] One sentence can name several items — split on "and then" / "then" / "next" / "after that",
      never on a bare "and"
- [x] Added straight away with an undo on the confirmation, rather than a confirm step every time
- [x] Captures land on top of the list, newest batch first, each batch in the order it was spoken
- [X] Not verified end to end: the emulator has no microphone, so the transcript → confirmation →
      undo leg has only unit coverage. Check it on a real phone
- [X] Consider a fallback for devices with no `ACTION_RECOGNIZE_SPEECH` activity — `SpeechRecognizer`
      against the on-device `RecognitionService` works there, but needs `RECORD_AUDIO` and an
      overlay of our own. Only worth it if it actually bites
- [x] The same button in the list widget's bottom-right corner, so a list you are already looking at
      can be spoken to without a second tile. It floats over the items — the header is already the
      tap target that opens the list — and the list ends with a spacer its height so the last item
      still scrolls clear of it. Checked on the emulator: the press opens the dictation sheet
      prompting "Add to <list>", in both themes, and the picker's static preview matches
- [x] A "+" beside it for when dictation is the wrong tool — a noisy room, or something quicker
      typed. It opens the list in the app with an empty item already waiting at the top and the
      keyboard up. The widget only asks; the app carries it out, because typing needs a screen.
      Checked on the emulator from a cold start and with the app already open, and that backing out
      without typing leaves no blank row behind (the usual "an empty item is deleted when the edit
      ends" rule does the work — verified via home, the back key and the top-bar arrow)
- [x] Fixed straight after, reported from real use: pressing "+" a second time often opened the list
      with no keyboard and left a blank row behind. Opening a list the app is already showing leaves
      the outgoing screen composed beside the incoming one, and the request was addressed to "the
      screen showing this list" — so the dying one could take it, adding the row to its own
      ViewModel and the focus along with it. The request now names the back stack entry. Reproduced
      first (8/8 failures under a tight tap loop, one stray row each), then 22/22 clean after
- [x] Found while fixing that: a focus request made while the window is still coming forward gets
      the caret but not the keyboard. `ItemEditor` now waits for `isWindowFocused`

## Phase 6 - Polish ✅

- [x] Reordering starts immediately: the drag handle no longer waits out a long press, so a row
      follows the finger from the first movement
- [x] Floating drag overlay: the dragged row is lifted out of the list and drawn offset onto the
      finger, so it travels with it pixel by pixel instead of hopping a whole slot at a time. The
      slot it holds underneath is the gap it would drop into — still the same `moveSubtree` preview,
      now with the row drawn away from it
- [x] The rows it passes slide into their new places (`animateItem`) rather than teleporting, and a
      dropped row slides the last few pixels home instead of snapping
- [x] Fixed while wiring that up: a finger in the list's *top padding* — above the first row but
      inside the viewport — hit no row and fell through to "past the end of the list", so nudging
      the top row up by about half a row flung it to the bottom
- [x] Swipe-to-delete is now a setting, off by default. The rows are where a finger lands to
      scroll, so the gesture fired by accident; the row's delete button is unaffected, and the
      undo snackbar still backs both

## Phase 7 — Robustness and extras

- [ ] Verify on a real SAF provider that creating a list keeps the requested filename — providers
      may append or change an extension. `SafDirectoryStore.create`/`rename` already read the real
      name back off the result, but that path has only been exercised against a local directory
- [ ] Verify the revoked-permission banner end to end: revoke access to the chosen folder (clear the
      provider's permission or remove the volume) and confirm settings offers to re-pick it
- [ ] Editing an item's text forgets that it was collapsed (its `CollapseKeys` key changes). Inline
      editing made this more visible, since every keystroke now changes the key. Re-key collapse
      state on edit if it proves annoying in practice
- [ ] Conflict handling when a file changed on disk while edits were pending
- [ ] Undo/redo stack for structural edits
- [ ] Instrumented UI tests for the drag interaction
- [ ] Widget rendering has no automated coverage — the row projection and key round-trip are unit
      tested, but the drawing itself has only been checked by hand on the emulator
- [ ] Release build config + signing notes

## Decisions

- **A list's name comes from its filename alone.** No `# Heading` is read from or written to the
  file; renaming a list renames its file.
- **Completed items stay in the file as `- [X]`** indefinitely. Archiving them out is a possible
  future feature, not a current one.
- **Three widgets, not one** (see Phase 5): one edits a single list, one picks a list to open, one
  takes a spoken item onto a list.
- **The voice tile hands dictation to the system recogniser** rather than running `SpeechRecognizer`
  itself. That keeps `RECORD_AUDIO` out of the app and gives the user the sheet they already know;
  the trade is that a device without an app providing that activity gets a clear message instead.
- **A dictated item is added immediately, with an undo**, rather than shown in a confirm step. The
  whole point of the tile is that capture costs one press; a confirmation every time would spend
  what it saves.
- **Voice captures go to the top of the list**, unlike every other add, which appends. What you say
  in passing is the thing you have not dealt with yet; burying it under everything already settled
  is how it gets missed.
- **Widgets share the app's one repository** rather than reading the files themselves. Two readers
  of the same files would eventually disagree, and ticking something off in the widget has to be
  the same edit as ticking it off in the app.
- **Hiding completed items is a view; moving them to the bottom is an edit.** The first never
  touches the file and is remembered per list; the second reorders the markdown exactly as dragging
  each finished item down by hand would.
- **A list's colour and view options live in `list_prefs`, keyed by file name**, and follow a
  rename and vanish with a delete, the same way collapse state does.
- **Swipe-to-delete is opt-in**, a boolean in the settings DataStore that ships off. It is a
  destructive gesture on the same surface a finger uses to scroll, and deleting is already a
  one-tap button on every row — so the default is the safe one, not the convenient one.
- **Tapping an item's text puts the caret where the finger landed**, not at the end. The end is
  right for a row that opened without a tap — a new item from Enter, or one a widget opened — and
  those still get it; a tap has a position and it is what the user meant. Tapping the empty space
  beside a short item still lands at the end of the line, so starting an edit to carry on typing
  costs nothing.
