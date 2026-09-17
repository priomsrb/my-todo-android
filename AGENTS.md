# AGENTS.md — My Todo

A Google Keep-inspired Android TODO app whose data lives in plain markdown files.

Current state: **Phase 6 complete** — lists are real markdown files, one per list, in a folder the
user picks (app-private storage until they do); nested items expand and collapse, rows are lifted
by the handle and dragged to reorder and re-nest, and items are typed inline (Enter for the next
one, Tab to nest), deleted with an undo from the row's button (or by swiping, once that is switched
on in settings), coloured per list, searched across lists, and hidden once finished. A whole list can also be
pasted in at once from the list's menu, in whatever format it was copied from. Three
Glance home-screen widgets show a list and tick it off (with a mic and a + in the corner for
adding one by voice or by typing), list every list and open one, or take a spoken item straight
onto a list. See [TODO.md](TODO.md) for the roadmap.

## Product invariants

These hold for every phase. Do not design around them.

1. **Nesting is unbounded.** Never hardcode a maximum depth, and never flatten the tree into a
   fixed set of levels. Recursion or explicit stacks only.
2. **A move takes the subtree with it.** Dragging, indenting or outdenting an item moves all of its
   descendants along with it.
3. **Markdown is the source of truth.** The `.md` file is what the user owns; the app is a view over
   it. Anything the format cannot express (collapse state, per-list colour, "hide completed") is
   local UI state stored separately and never written into the file.
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
| Glance (widgets) | 1.2.0 |
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
./gradlew :app:testDebugUnitTest      # all tests: pure logic + Compose UI (Robolectric)
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

### Checking behaviour on a device

Compose UI tests cover gesture and navigation wiring, but not rendering or real touch timing. For
those, drive the emulator directly — no screenshots-by-eye needed:

```bash
adb shell uiautomator dump /sdcard/ui.xml    # then read bounds + content-desc from the XML
```

- **Find elements by their bounds**, not by guessing pixels: every row's drag handle carries
  `content-desc="Reorder"`, checkboxes appear as `android.widget.CheckBox`, item text as `TextView`.
- **Drags need `input motionevent`**, not `input swipe`: `DOWN`, several `MOVE`s, then `UP`. A
  reorder starts on the first movement past touch slop — no hold — but `input swipe` sends too few,
  too-coarse points to read as a drag, and it cannot hold the finger still between them.
- **Inspect a screen mid-transition** by dumping the UI *while a touch is still held*, between the
  `DOWN` and the `UP`.
- **Sample colours instead of eyeballing them**: `adb exec-out screencap -p > f.png` then
  `magick f.png -crop 1x1+540+1800 -format '%[pixel:p{0,0}]' info:`.
- **Slow animations down** to catch mid-transition frames:
  `adb shell settings put global animator_duration_scale 10` — Compose honours it. Put it back to
  `1` afterwards, along with anything else you changed (`cmd uimode night ...`).
- **Prove a fix by reproducing the bug first**: `git stash`, rebuild, reproduce, `git stash pop`,
  rebuild, confirm it is gone. Three bugs were "fixed" and re-reported before this became habit.
- The app may be pointed at a SAF folder rather than app-private storage — check
  `run-as dev.shafqat.mytodo cat files/datastore/settings.preferences_pb`. Write test lists into
  that folder with an obvious name and delete them afterwards; do not disturb real lists.

**JDK note:** the machine default is JDK 25 while JDK 17 is also installed. Robolectric needs
several `--add-opens` / `--add-exports` flags to run on it at all; those are set on the `Test` tasks
in `app/build.gradle.kts` — removing them breaks every UI test with an `IllegalAccessException`.
If Gradle or AGP rejects the JVM it is running on, pin it in `gradle.properties`:

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
    ListPrefs.kt             per-list colour and view options — local, never in the file
    TreeMove.kt              flatten/rebuild, moveSubtree, insert/indent/outdent — the move maths
    Completed.kt             hiding finished items (a view) and sinking them (an edit)
    Search.kt                matching items across every list
    Dictation.kt             cutting a dictated sentence into the items it names
    TextImport.kt            reading pasted text — bullets, numbers, checkboxes — as items
  data/
    TodoRepository.kt        interface the UI talks to
    MarkdownTodoRepository.kt  keeps the tree and the files in step; no Android APIs
    StorageState.kt          Ready / PermissionLost / Error / Loading
    markdown/                MarkdownParser, MarkdownSerializer, MarkdownDocument
    collapse/                CollapseKeys (stable item identity) + CollapseStore
    prefs/                   ListPrefsStore: per-list colour and view options
    store/
      TodoFileStore.kt       the only seam that knows where files physically live
      LocalDirectoryStore.kt app-private default; also stands in for storage in tests
      SafDirectoryStore.kt   a folder the user picked, via a persisted tree URI
    settings/                DataStore: SettingsRepository (folder URI, swipe-to-delete),
                             DataStoreCollapseStore (collapse state),
                             DataStoreListPrefsStore (per-list colour, hide-completed)
  widget/
    ListWidget.kt            5a: one list, ticked off from the home screen; mic and + in the corner
    LauncherWidget.kt        5b: every list, tap to open one
    VoiceWidget.kt           5c: a 1x1 tile that is only a button — press, speak, it is on the list
    VoiceCaptureActivity.kt  the transparent overlay: dictation sheet, then a confirmation + undo
    VoiceCapture.kt          filing a dictated sentence onto a list, and taking it back off
    WidgetConfigActivity.kt  the shared "which list?" picker and its contract with the launcher
    ListWidgetConfigActivity.kt / VoiceWidgetConfigActivity.kt  its two subclasses
    ToggleItemAction.kt      a tick from a widget, resolved back to an item and written through
    WidgetRows.kt            the pure projection of a list into widget rows
    WidgetHost.kt            getting the repository, and the intents back into the app
    WidgetColors.kt          the app's palette as Glance colour providers, and the list-tint rule
  ui/
    navigation/MyTodoApp.kt  NavHost: lists → list/{listId} → search → settings
    lists/                   Keep-style grid of list cards
    todo/                    one list: flattened rows, checkboxes, inline editing, swipe-to-delete,
                             TodoDragState (drag, depth, auto-scroll)
    search/                  search across every list
    settings/                folder picker, storage state, swipe-to-delete toggle, about rows
    components/              shared composables (TextInputDialog, AddFromTextDialog,
                             ColorPickerDialog, EmptyState)
    theme/                   Keep-ish palette, typography, note tints
app/src/test/java/dev/shafqat/mytodo/
  TodoTreeTest.kt            tree helpers
  MarkdownTest.kt            parse/serialize round trips
  MarkdownTodoRepositoryTest.kt  storage behavior over a temp directory
  CollapseTest.kt            collapse keys and their persistence
  TreeMoveTest.kt            the move maths, incl. exhaustive invariants
  MoveItemTest.kt            moves reaching the file
  AutoScrollTest.kt          when a drag scrolls the list
  DragOverlayTest.kt         where the floating row is drawn relative to its slot
  OutlineEditTest.kt         indent/outdent and putting a deleted subtree back
  CompletedTest.kt           hiding finished items vs. sinking them
  SearchTest.kt              matching across lists
  ListEditingTest.kt         the Phase 4 edits as they reach the file, plus list prefs
  TodoItemListUiTest.kt      Compose: dragging rows (Robolectric)
  TodoItemEditingUiTest.kt   Compose: inline entry, Enter/Tab, swipe-to-delete
  WidgetRowsTest.kt          widget rows, key round-trips and deep-link intents
  DictationTest.kt           where a dictated sentence is and is not cut into several items
  TextImportTest.kt          the formats a paste may arrive in, and the nesting it must not invent
  VoiceCaptureTest.kt        a transcript reaching the file, and undo taking it back off
  NavigationTransitionUiTest.kt  Compose: taps during screen transitions
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
- **The dragged row is drawn away from its slot.** The preview gives it the slot it would land in;
  a `graphicsLayer` on that row then offsets it onto the finger, so it follows continuously while
  the slot underneath reads as the gap. `floatingOffset` is pure and tested: it centres the row on
  the finger and clamps it inside the viewport, so a finger that runs past the end of a list which
  cannot scroll any further does not drag the row off screen. The row is lifted over its neighbours
  with `zIndex`, and they use `animateItem` to slide into the places it vacates.
- **Letting go settles, it does not snap.** `settlingItemId` is deliberately separate from
  `draggedItemId`: the move is committed the instant the finger lifts, and the settle is only the
  drop animation sliding the leftover offset to zero. Feeding it back into the preview would move
  the item twice. Picking a row up again cancels any settle still running.
- **The dragged row's own slot wins the hit test.** Neighbours are mid-animation while it travels,
  and a plain hit test against a row still sliding can hand the target back to where it came from
  one frame after it left — the row then flickers between two slots. If the finger is still inside
  the dragged row's slot, the target does not move.
- **Off the ends means off the *rows*, not off the viewport.** The list has top and bottom content
  padding, which is inside the viewport but outside every row. Testing against `viewportStartOffset`
  left the top padding falling through to the "past the end" branch, so nudging the top row up by
  half a row sent it to the bottom of the list. The bounds to compare against are the first and last
  visible rows; a finger between two rows keeps whatever target it had.
- **The drag starts on movement, not on a hold.** The handle uses `detectDragGestures`, so a row
  moves from the first movement past touch slop; there is no long-press threshold to wait out. The
  handle consumes the gesture, which is what stops that same movement from scrolling the list or
  arming swipe-to-delete — and it is why the handle is a small, deliberate target rather than the
  whole row.
- **The drag gesture must not capture the row's index or depth.** `pointerInput` is keyed on the
  item id alone, deliberately, so reordering the list cannot cancel an in-flight drag — which also
  means its gesture block is never recreated when a row moves. Read index and depth through
  `rememberUpdatedState`; capturing them directly makes a row that has been moved fling back to
  wherever it sat when it was first composed, the moment it is picked up again.
- **Auto-scroll only engages once the finger has travelled.** Picking up a row that already sits
  inside the edge band used to scroll the list while the finger was still, and since each scroll
  re-reads the row under the finger, the item walked up the list on its own. `autoScrollSpeed` is
  pure and tested — keep the decision there rather than inline in the frame loop.

## How editing works

- **A row becomes its own text field.** Tapping an item's text swaps the `Text` for a
  `BasicTextField` in the same slot, so nothing moves as the edit starts. Which row is being edited
  is state of `TodoItemList`, not of the screen: it has to survive the row being re-nested, and a
  test can drive it without a ViewModel.
- **The editor lays out exactly like the text it replaces.** It wraps rather than being a
  single-line field, and it takes `typography.bodyLarge` directly instead of merging onto
  `LocalTextStyle`, which brought line metrics of its own. Both matter only for an item long enough
  to wrap: as a single line that item became one strip scrolling sideways, and the row — along with
  everything below it — jumped the moment it was tapped. Newlines are turned into spaces on the way
  in, since a wrapping field can be handed one by a paste or by an IME's return key, and an item is
  one line of markdown. Enter is still intercepted before the field sees it, so it splits the item
  rather than breaking the line.
- **The caret opens on the character that was tapped**, so a word in the middle of a long item can
  be fixed without walking back to it. `TodoRow` keeps the `TextLayoutResult` of the text it is
  showing and notes where the finger went down — on the *initial* pointer pass, consuming nothing,
  so the `clickable` still owns the tap along with its ripple and its accessibility click — then
  turns the two into an offset with `getOffsetForPosition`. That touch is in the coordinates of the
  whole text slot, which starts `TextVerticalPadding` above the text itself, so the padding is
  subtracted first; without that a tap on the second line of a wrapped item can read as the first.
  Edits that began with no tap behind them — a new item from Enter, a widget opening the app on one
  — pass a null offset and start at the end, as before. A blank row draws a placeholder longer than
  its own empty text, so the offset is clamped to the text it will actually sit in.
- **Keys are handled on the preview pass.** Tab would otherwise move focus and Enter would insert a
  newline; `onPreviewKeyEvent` claims Tab, Shift-Tab and Enter before the field sees them. The soft
  keyboard's Next action is wired to the same place, since IMEs do not deliver Enter as a key event.
- **Enter is "split", and `addItemAfter` is one insert at the row below.** The new item goes at the
  edited row's index plus one, at its depth; when the item has children showing, that position *is*
  its first child, which is what an outliner does anyway. It falls out of the move coordinates
  rather than being a second code path.
- **Tab and Shift-Tab are moves, not a depth field.** `indentItem` re-runs `moveSubtree` at the same
  index one level deeper and lets `allowedDepthRange` refuse what is illegal; `outdentItem` first
  walks past the siblings that followed it, so they keep their parent instead of being adopted.
- **An item left empty is deleted when the edit ends.** Blank rows cannot be told apart on screen
  from rows the user meant to keep, and pressing Enter once too many is the usual way to get one.
  That is also what makes Enter-on-an-empty-item read as "I am done".
- **A new editor must not end itself.** A field reports "not focused" once before it is given focus;
  `ItemEditor` ignores that first report, or every freshly created item would be deleted the
  instant it appeared.
- **Leaving the app ends the edit too.** Nothing tells a text field it lost focus when the whole
  screen goes away, so without a lifecycle observer an item the user started typing and then
  switched away from is left behind as a blank row — and saved into their file as one. This was
  found in real use, not by the tests.
- **Back closes the editor** (`BackHandler` in `TodoItemList`) before it leaves the screen.
  Otherwise the only way out of an edit is to start another one — and an editing row is a row that
  cannot be swiped away, since swipe is deliberately off mid-edit.
- **Swipe-to-delete commits from `confirmValueChange`,** because the row is gone from the list as
  soon as it is deleted and there is no settled state left to observe. The box asks more than once
  on its way to dismissed, so the delete is guarded by a flag or a single swipe deletes twice.
- **Swipe-to-delete is off unless the user turns it on** in settings (`swipeToDeleteEnabled`,
  a boolean in the settings DataStore, default false). The rows are where a finger lands to scroll,
  so the gesture is easy to fire by accident, and every row already carries a delete button — so the
  default costs nothing. `TodoItemList`'s parameter defaults to `false` too, matching what ships;
  the swipe tests pass it explicitly.
- **Undo is `insertSubtree` at the recorded index and depth.** `TodoListViewModel` captures where an
  item sat *before* deleting it; anything less puts the item back at the end of the list, which is
  not undoing anything.

## Completed items, colour and view options

- **Hiding finished items is a view; moving them to the bottom is an edit.** `withoutCompleted()`
  filters what is rendered and never reaches the file. `completedLast()` rewrites the tree, and so
  the markdown, exactly as dragging each finished item down by hand would.
- **A done item with unfinished descendants is not hidden**, because its children would otherwise be
  orphaned or silently promoted a level.
- **Reordering is off while completed items are hidden.** A drag is a row index, and a filtered list
  does not have the same indices; rather than translate between two coordinate spaces, the drag
  handle is hidden. Everything else — editing, indent, delete, collapse — is keyed by item id and
  works unchanged.
- **Per-list colour and "hide completed" live in `ListPrefs`, keyed by file name**, in a third
  DataStore file (`list_prefs`). Same reasoning as collapse: a write re-emits the whole preferences
  object, so stores that are read for different reasons do not share a file. They follow a rename
  and are forgotten on delete, like collapse keys.
- **A list with no colour of its own takes the tint of its position in the grid**, so a new folder
  still looks like Keep's wall of coloured notes. "No colour" is stored as a null index, never as
  palette entry zero, so changing the default later reaches every list that never chose one.

## How adding from text works

- **Pasted text gets its own parser.** `itemsFromText` in `model/TextImport.kt` accepts `-`, `*`,
  `+` and `•` bullets, numbered lines, `- [ ]` / `- [x]` checkboxes and bare lines, mixed within
  one paste. It is deliberately not `MarkdownParser`: that one reads the app's own files, where an
  unrecognised line is someone's content and must survive untouched, while here every non-blank
  line is something the user meant to add and nothing may be dropped.
- **The shallowest line sets the baseline.** Text copied out of a code block or a quoted reply
  arrives indented as a whole; without subtracting that baseline every line after the first reads
  as a child of the one before it. The indent *unit* is then the smallest step actually present,
  and a line may still only ever be one level deeper than the line above it.
- **A bullet must be followed by whitespace**, so "e-mail Sam" and "3.5 kg of flour" keep their
  text. Lines that are only punctuation — a `---` rule, a stray bullet — name nothing and are
  dropped rather than added as items.
- **The dialog counts as it goes.** The field is parsed on every keystroke and the count under it
  says how many items a confirm would add, which is the only way to notice before the fact that a
  stray indent has nested half the paste under its first line.
- **Where it lands is chosen in the dialog and remembered** (`addFromTextAtTop`, in the settings
  DataStore, default "at the end"). The choice is held in the dialog rather than read back from the
  setting, so the toggle answers the finger instead of the next emission from storage — and it is
  persisted on confirm, since a cancelled paste should not move the next one.
- **Undo matches on position and text, never on ids.** `removeItems` drops the first (or last) N
  top-level rows *if* they still read as what was added. Ids are fresh UUIDs on every parse, any
  edit redraws the widgets a second later, and a widget redraw reloads the files — so an id-based
  undo silently did nothing from about a second after the snackbar appeared. The text check is also
  what makes an undo safe once the user has moved things: it becomes a no-op rather than taking the
  wrong rows.
- **A dialog holding a text field cannot be driven under Robolectric here.** `createComposeRule`
  renders a plain `AlertDialog` fine, but one containing an `OutlinedTextField` never goes idle and
  the test worker runs out of memory — with or without a focus request. The parser and the batch
  add/undo are unit tested; the dialog itself is checked on the emulator.

## How the widgets work

- **A widget shares the app's repository; it never opens the files itself.** Two readers of the same
  directory would drift apart, and ticking something off on the home screen has to be the same edit
  as ticking it off in the app. `widgetRepository()` waits for the first load, because a widget
  update is often what started the process.
- **A row carries a `CollapseKeys` key, not an item id.** Ids are fresh UUIDs on every parse, and a
  widget can sit on the home screen for hours across several reloads. The key is resolved back to an
  id when the tap arrives, so a tap either hits the item the user pointed at or does nothing.
- **Derive keys from the whole tree, never from a filtered one.** Keys number same-named siblings by
  position, so keys taken from a tree that has already had rows hidden name whichever item now sits
  in that position. `widgetRows()` builds the key map first and filters second.
- **A widget's write is flushed immediately** rather than left to the 500ms debounce: nothing keeps
  the process alive once the tap has been handled.
- **Each row's intent puts the list id in the data URI, not just an extra.** `Intent` equality
  ignores extras, so rows that differed only by extra would share one `PendingIntent` and every row
  in the launcher widget would open the same list.
- **Updates are pushed from `MyTodoApplication`**, which watches `repository.lists` and redraws both
  widgets on a 1s debounce — `lists` changes on every keystroke while an item is being typed.
  The widget also calls `refresh()` before drawing, which is what catches a file edited elsewhere.
- **`WidgetConfigActivity` sets `RESULT_CANCELED` first.** The launcher starts it before the
  widget exists and treats a cancelled result as "do not place it". Both per-list widgets share the
  base class rather than each restating that contract; a subclass supplies only the widget to
  redraw and the title to ask under.
- **The list widget's corner carries two buttons: a mic and a "+".** The mic is the voice tile's
  press, same intent and same activity. The "+" opens the list in the app with an empty item already
  waiting at the top — the other half of the same idea, for when dictation is the wrong tool. They
  are not in the header because the header is itself the tap target that opens the list, and buttons
  inside it would be targets inside a target. They float rather than taking a row of their own, and
  the `LazyColumn` ends with a spacer their height so the last item can still be scrolled clear.
- **The buttons are `primaryContainer`, not the list's tint.** `NoteColors[0]` is pure white, so a
  list with no colour of its own would have drawn invisible buttons on the widget's white
  background. The app's own "Add item" FAB uses the same pair, which is also why the corner reads
  the same in both places.
- **The "+" is a request the app carries out, not an edit the widget makes.** Typing needs a
  keyboard and the list to see it against, which is a screen, not an overlay — so the widget only
  asks. `MainActivity` turns the intent into an `OpenListRequest`, the NavHost navigates and
  remembers which list still owes a row, and `TodoListScreen` spends that request once.
- **That request is state, not part of the route.** Putting `newItem` in the route would make it
  part of the destination's identity, and navigating back to the list later — or restoring it after
  a rotation — would add a second empty row. It is an event, so it is held beside the NavHost and
  cleared the moment the screen acts on it.
- **It names the back stack *entry*, not the list.** Pressing "+" for a list the app is already
  showing pops that screen and pushes a new one, and for the length of the fade both are composed.
  A request addressed to "the screen showing this list" is therefore seen by two of them, and the
  one on its way out can get there first: it adds the row to its own dying ViewModel and takes the
  focus with it, so the surviving screen opens no editor, no keyboard appears, and — since nothing
  is being edited — nothing deletes the blank row either. One press, one stray row, no keyboard.
  `navController.currentBackStackEntry?.id`, read *after* navigating, leaves exactly one reader.
  This is why it was intermittent: with a slower tap the outgoing entry was already gone.
- **The editor waits for the window before asking for focus.** A widget tap opens the app and the
  editor in the same breath, and a focus request made while the window is still coming forward is
  half-honoured — Compose gives the field its caret, but the keyboard never comes, and nothing asks
  again once the window settles. `ItemEditor` waits on `LocalWindowInfo.isWindowFocused` first. It
  is a no-op for every edit begun inside the app, where the window is focused already.
- **`addItemAtTop` waits for storage before it writes.** A cold start from the "+" can reach the
  ViewModel before the folder has been read, and `mutate` drops an edit to a list it cannot find —
  silently, since there is nothing to find. It waits for the list to appear, with the same
  give-up timeout `widgetRepository()` uses.
- **The "+" adds at the top; the screen's own "Add item" still appends.** Same reasoning as a
  dictated item: something you reached for the widget to jot down has not been dealt with yet.
  Inside the app the list is in front of you and the end is where you are looking.

### The voice tile

- **It is a button, not a view.** `VoiceWidget` draws a mic on the list's tint and nothing else, so
  it is never out of date, and the press goes straight to `VoiceCaptureActivity` — no widget
  callback, no `RemoteViews` round trip between the finger and the microphone. The whole tile is the
  target: a widget cannot use long-press, which the launcher keeps for itself.
- **Dictation is the system's, via `ACTION_RECOGNIZE_SPEECH`.** That keeps `RECORD_AUDIO` out of the
  manifest entirely — the recogniser app holds the permission — and gives the user the sheet they
  already know. The cost is that a device with no app providing that *activity* has no dictation at
  all, which is why the launch is a `try`/`catch` on `ActivityNotFoundException` rather than a
  `resolveActivity` check: package visibility hides most recognisers from the check, so actually
  launching it is the only honest test. The `<queries>` entry is there for the same reason.
- **The capture activity is transparent, animation-free, and in its own task.** `taskAffinity=""`
  plus `excludeFromRecents` keeps it out of `MainActivity`'s task — otherwise pressing the tile
  would haul the whole app forward behind the dictation sheet — and the null window animation is
  what makes the sheet look like it opened straight off the home screen. It must **not** be
  `noHistory`: handing off to the recogniser backgrounds it, and `noHistory` would destroy it before
  the transcript came back.
- **The list's name rides in the intent.** The prompt says "Add to Groceries" without anyone reading
  a file first; storage is only touched once there is a transcript to file. `savedInstanceState`
  guards the launch, or a rotation would stack a second sheet.
- **One sentence can name several items.** `splitDictation` cuts on "and then", "then", "next",
  "and next" and "after that" — never on a bare "and", which joins one item ("milk and bread") far
  more often than it starts another. A "next" followed by a time word is part of the item, not a
  break ("book the car in next week"). The rules err towards leaving a sentence alone: a split
  nobody asked for is more annoying than a missed one.
- **A dictated item goes on top, not on the end.** Something captured in passing is something you
  have not dealt with yet, and a list you speak at is a list that grows — appending would file every
  new item below everything already seen and settled. Within one sentence each item goes below the
  last, so "milk and then bread" reads top to bottom in the order it was spoken, and a later capture
  sits above an earlier one. `addItemAt` is the repository seam; it indexes into the whole tree, not
  the filtered view, so hiding finished items cannot move where a capture lands.
- **Inserting above existing rows re-keys collapse state.** Appending never had to, because it
  cannot change any existing item's path; inserting at the top renumbers same-named siblings, and
  collapse keys are built from exactly that numbering. `addItemAt` calls `rekeyCollapse` for the
  same reason `moveItem` does.
- **Undo deletes the ids the capture created**, not "the last N items", so anything added in between
  survives. The adds are flushed immediately, like any other widget write.
- Verified on the emulator as far as the microphone: the tile draws and tints correctly in both
  themes, the press opens the dictation sheet over the home screen in its own task. The leg past
  that needs real speech, so `VoiceCaptureTest` covers it against a temp directory instead.

### Coverage

- *How text lays out* has no automated coverage: Robolectric does not wrap text at all — a
  300-character item still measures as one line — so a test cannot tell a wrapping editor from a
  single-line one. Row heights are compared on the emulator instead, from `uiautomator dump`
  bounds, the same split the widgets use.
- Widget *rendering* has no automated coverage. The row projection, the key round-trip and the
  intents are unit tested; the drawing is checked on the emulator, the same split the app itself
  uses (see "Checking behaviour on a device").

## Navigation

- **Every destination goes through `Screen()`**, which ignores touches until its `NavBackStackEntry`
  reaches `RESUMED`. While a screen fades in or out, both destinations are composed and the outgoing
  one still accepts touches, so a fast tap after backing out of a list used to land on the old
  screen — ticking whatever item sat under the finger and not opening the list that was tapped.
  Navigation resumes an entry only once its animation ends, which is why the lifecycle state is the
  signal. A tap during a transition is deliberately dropped rather than redirected.
- Screen transitions are short (180ms) so a screen settles quickly under an impatient finger; the
  navigation-compose default is several times longer.
- **The NavHost sits on a `Surface` in the theme background colour, and the window background is
  `@color/window_background` with a `values-night` variant.** Cross-fading screens are both briefly
  translucent, so whatever is behind them shows through: with the old hardcoded white window
  background that was a bright flash on every navigation in dark mode. Keep those colours matching
  `KeepBackground` / `KeepBackgroundDark` in `ui/theme/Color.kt`.

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
- **A rename returns the id the list actually got.** The file name is the id, so a rename changes
  it: `renameList` hands back the new one and the list screen re-opens on the new route. A screen
  left on the old id would show nothing.
- **A failure becomes a `StorageState`, not a crash.** `SecurityException` means the folder
  permission is gone (`PermissionLost`, surfaced as a settings banner offering to re-pick); anything
  else becomes `Error`. The app never silently falls back to a different set of files.
- **Filenames are the identity.** `fileNameFor` slugifies a name, `displayNameFor` reverses it, and
  `uniqueFileName` de-duplicates. SAF providers may alter a name on create/rename, so both store
  methods return the name the file actually got and callers must use it.
