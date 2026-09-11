# AGENTS.md — My Todo

A Google Keep-inspired Android TODO app whose data lives in plain markdown files.

Current state: **Phase 3 complete** — lists are real markdown files, one per list, in a folder the
user picks (app-private storage until they do); nested items expand and collapse, and rows can be
dragged to reorder and re-nest. No widget yet. See [TODO.md](TODO.md) for the roadmap.

## Product invariants

These hold for every phase. Do not design around them.

1. **Nesting is unbounded.** Never hardcode a maximum depth, and never flatten the tree into a
   fixed set of levels. Recursion or explicit stacks only.
2. **A move takes the subtree with it.** Dragging, indenting or outdenting an item moves all of its
   descendants along with it.
3. **Markdown is the source of truth.** The `.md` file is what the user owns; the app is a view over
   it. Anything the format cannot express (collapse state, per-list color) is local UI state stored
   separately and never written into the file.
4. **Item ids are stable** across reorder, re-nest and save/load, so Compose keys and drag state
   survive edits.
5. **Ticked items read as done** — grayed out and struck through, never hidden by default.

## File format

One list per markdown file, inside a single folder the user picks in settings.

```markdown
- [ ] Item 1
	- [ ] Sub item 1
	- [ ] Sub item 2
- [X] Checked off item
```

- Indentation is **one tab per nesting level**. When parsing, accept spaces too (4 spaces or 2
  spaces per level, detected per file); when writing, always emit tabs.
- Unchecked is `- [ ]`, checked is `- [X]` (uppercase on write; accept `x` on read).
- The list name comes from the filename alone (`groceries.md` → "Groceries"), so renaming a list
  renames its file. Never read a list's name from a `# Heading`, and never write one into the file.
- Completed items stay in the file as `- [X]`; nothing is archived out of it.
- Preserve lines the app does not understand rather than dropping them, so hand-edited files
  survive a round trip.

## Stack

| Piece | Version |
|---|---|
| Gradle | 9.5.1 (wrapper) |
| Android Gradle Plugin | 9.2.1 |
| Kotlin | 2.3.21 (AGP 9 built-in Kotlin — see below) |
| Compose BOM | 2026.09.00 (Material 3) |
| compileSdk / targetSdk | 37 (Android 17) |
| minSdk | 26 |
| JDK | builds on the machine default (JDK 25); see the JDK note below |

Every dependency goes through the version catalog at `gradle/libs.versions.toml` — no inline
coordinates in `app/build.gradle.kts`.

**AGP 9 applies Kotlin itself.** Do not add `org.jetbrains.kotlin.android` to the plugins block —
AGP 9 fails the build if you do. Only `com.android.application` and
`org.jetbrains.kotlin.plugin.compose` are applied. For the same reason there is no `kotlinOptions`
block; the Kotlin JVM target follows `compileOptions` (17).

**compileSdk must stay at 37.** androidx 2026.09-era artifacts (core-ktx 1.19.0, the Compose BOM)
refuse to compile against 36.

## Commands

```bash
./gradlew :app:assembleDebug          # build the debug APK
./gradlew :app:testDebugUnitTest      # JVM unit tests (tree + markdown logic)
./gradlew :app:installDebug           # install onto a running device/emulator
./gradlew :app:lintDebug              # Android lint
```

Run on the emulator:

```bash
~/Library/Android/sdk/emulator/emulator -avd Medium_Phone_API_36.1 &
adb wait-for-device
./gradlew :app:installDebug
adb shell am start -n dev.shafqat.mytodo/.MainActivity
```

Screenshot for visual checks: `adb exec-out screencap -p > /tmp/screen.png`.

**JDK note:** the machine default is JDK 25 while JDK 17 is also installed. If Gradle or AGP
rejects the JVM it is running on, pin it in `gradle.properties`:

```properties
org.gradle.java.home=/path/to/jdk-17
```

## Layout

```
app/src/main/java/dev/shafqat/mytodo/
  MainActivity.kt            edge-to-edge host, sets the Compose content
  MyTodoApplication.kt       manual DI: owns the single TodoRepository instance
  model/
    TodoItem.kt              tree node + flattenVisible/updateItem/addItem/removeItem helpers
    TodoList.kt              one named list, backed by one markdown file
    TreeMove.kt              flatten/rebuild and moveSubtree — all of the drag maths
  data/
    TodoRepository.kt        interface the UI talks to
    MarkdownTodoRepository.kt  keeps the tree and the files in step; no Android APIs
    StorageState.kt          Ready / PermissionLost / Error / Loading
    markdown/                MarkdownParser, MarkdownSerializer, MarkdownDocument
    collapse/                CollapseKeys (stable item identity) + CollapseStore
    store/
      TodoFileStore.kt       the only seam that knows where files physically live
      LocalDirectoryStore.kt app-private default; also stands in for storage in tests
      SafDirectoryStore.kt   a folder the user picked, via a persisted tree URI
    settings/                DataStore: SettingsRepository (folder URI),
                             DataStoreCollapseStore (collapse state)
  ui/
    navigation/MyTodoApp.kt  NavHost: lists → list/{listId} → settings
    lists/                   Keep-style grid of list cards
    todo/                    one list: flattened rows, checkboxes, add/delete,
                             TodoDragState (drag, depth, auto-scroll)
    settings/                placeholder rows until Phase 1
    components/              shared composables (TextInputDialog)
    theme/                   Keep-ish palette, typography, note tints
app/src/test/java/dev/shafqat/mytodo/
  TodoTreeTest.kt            tree helpers
  MarkdownTest.kt            parse/serialize round trips
  MarkdownTodoRepositoryTest.kt  storage behavior over a temp directory
  CollapseTest.kt            collapse keys and their persistence
  TreeMoveTest.kt            the move maths, incl. exhaustive invariants
  MoveItemTest.kt            moves reaching the file
```

## Conventions

- Kotlin official code style; 4-space indent; trailing commas.
- Compose only — no XML layouts, no view binding. XML is for manifest, strings, theme and icons.
- State flows one way: `TodoRepository` (`StateFlow`) → `ViewModel` → composable; events go back as
  lambdas. Composables never touch the repository directly.
- ViewModels are `AndroidViewModel`s reading the repository off `Application`; a ViewModel needing
  an argument (e.g. `listId`) gets a `viewModelFactory` next to its screen.
- Rendering flattens the tree through `flattenVisible()` so `LazyColumn` stays flat while the data
  keeps its nesting. Drag-and-drop and collapse build on that same helper — extend it rather than
  introducing a parallel flattening.
- Pure logic (tree ops, markdown parse/serialize) lives in `model/` or `data/` with no Android
  dependencies, so it is unit-testable on the JVM. Add tests there for every such change.
- **Update `TODO.md` when a task lands** — tick its box and note anything the next phase needs.

## How dragging works

- **A drag is two numbers**: the index the row should land at among the visible rows, and the depth
  it should nest at. `moveSubtree(itemId, targetIndex, targetDepth)` in `model/TreeMove.kt` consumes
  exactly those, and everything else — new parent, new siblings — follows. All of that logic is pure
  and exhaustively tested; keep it that way rather than moving tree maths into composables.
- **`targetIndex` is an index into the tree with the dragged subtree already removed.** That is the
  one coordinate convention to remember: it makes the dragged item's own starting index put it back
  where it was, and it is why the preview and the committed move agree.
- **`flattenForMove` strips an expanded item's children (they get their own rows) but keeps a
  collapsed item's children (they do not).** That invariant is what lets `rebuildTree` be lossless
  and a collapsed subtree be dragged as one unit.
- **`allowedDepthRange` is the guard rail**: at most one deeper than the row above, never shallower
  than the row below (which would re-parent it), never inside a collapsed parent (it would vanish).
  `moveSubtree` clamps, so callers may pass whatever the finger suggests.
- **The UI previews by applying the move.** `TodoListScreen` renders `moveSubtree(...)` of the
  pending drag rather than maintaining a separate gap/placeholder, so what is on screen is always
  exactly what a drop would produce.
- **Moving invalidates collapse keys**, since they are paths. `moveItem` re-derives and re-persists
  them for that file afterwards.
- **The drag gesture must not capture the row's index or depth.** `pointerInput` is keyed on the
  item id alone, deliberately, so reordering the list cannot cancel an in-flight drag — which also
  means its gesture block is never recreated when a row moves. Read index and depth through
  `rememberUpdatedState`; capturing them directly makes a row that has been moved fling back to
  wherever it sat when it was first composed, the moment it is picked up again.
- **Auto-scroll only engages once the finger has travelled.** Picking up a row that already sits
  inside the edge band used to scroll the list while the finger was still, and since each scroll
  re-reads the row under the finger, the item walked up the list on its own. `autoScrollSpeed` is
  pure and tested — keep the decision there rather than inline in the frame loop.

## Navigation

- **Every destination goes through `Screen()`**, which ignores touches until its `NavBackStackEntry`
  reaches `RESUMED`. While a screen fades in or out, both destinations are composed and the outgoing
  one still accepts touches, so a fast tap after backing out of a list used to land on the old
  screen — ticking whatever item sat under the finger and not opening the list that was tapped.
  Navigation resumes an entry only once its animation ends, which is why the lifecycle state is the
  signal. A tap during a transition is deliberately dropped rather than redirected.
- Screen transitions are short (180ms) so a screen settles quickly under an impatient finger; the
  navigation-compose default is several times longer.

## How storage works

- `MarkdownTodoRepository` owns the in-memory tree and writes it back through a `TodoFileStore`.
  It contains no Android APIs on purpose, so all of its behavior is covered by JVM tests against a
  `LocalDirectoryStore` pointed at a temp directory. Add tests there, not instrumented ones.
- **Edits save on a 500ms debounce**, so a burst of typing is one write; `flushPendingSaves()` runs
  from `MainActivity.onStop`. A rename or delete cancels that file's pending save first.
- **`refresh()` runs from `MainActivity.onStart`** to pick up outside edits, and deliberately skips
  any file with an unsaved edit so a reload cannot undo something just typed.
- **Unparseable lines are preserved.** `MarkdownDocument.extraLines` keys them by the id of the item
  they follow (`PREAMBLE` for lines before the first item), and the repository keeps that map per
  file so a rewrite does not eat hand-written content. Blank lines are normalized away.
- **Collapse state has its own DataStore file, and settings has another. Do not merge them.** A
  DataStore write re-emits the entire preferences object, so when they shared a file every collapse
  toggle looked like a folder change to `todoFolderUri` and sent the repository through a full
  reload — visible as the list screen flashing empty. `todoFolderUri` is also
  `distinctUntilChanged()`, and `attachStore` ignores an unchanged folder.
- **Collapse never writes to the file.** `setItemCollapsed` updates memory and the `CollapseStore`;
  the markdown format has no place for it. Collapse state is keyed by `CollapseKeys` — the file name
  plus the *text path* down to the item — because `TodoItem.id` is a fresh UUID on every parse and
  could never survive a reload. Editing an item's text therefore forgets its collapse, which is the
  intended trade-off: better to forget than to collapse the wrong item.
- **A failure becomes a `StorageState`, not a crash.** `SecurityException` means the folder
  permission is gone (`PermissionLost`, surfaced as a settings banner offering to re-pick); anything
  else becomes `Error`. The app never silently falls back to a different set of files.
- **Filenames are the identity.** `fileNameFor` slugifies a name, `displayNameFor` reverses it, and
  `uniqueFileName` de-duplicates. SAF providers may alter a name on create/rename, so both store
  methods return the name the file actually got and callers must use it.
