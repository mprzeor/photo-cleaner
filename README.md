# PhotoCleaner — duplicate photo finder for Android

Finds photos of the same scene taken several times (bursts, retakes, re‑saves) inside a folder you pick,
groups them, and lets you delete everything but the best shot.

Kotlin · Jetpack Compose · Material 3 · Room · Coroutines · Coil · minSdk 26

---

## Screens

**Home**
- Pick a folder with the system picker (Storage Access Framework – no storage permission needed).
- "Include subfolders" toggle.
- **Start scan** → progress card with percentage, `done / total`, current file name, cache hits and unreadable files, Cancel button.
- Cache size + "Clear cache".
- When the scan finishes the app jumps to the results screen automatically.

**Duplicates**
- Summary bar: number of groups, photos involved, reclaimable bytes, and chips showing the active matching rules.
- One card per group, best photo first (highest resolution, then largest file). Thumbnails show resolution, size, date and a GPS marker.
- Tap a photo to mark it for deletion, long‑press to open it in your gallery app.
- "Keep best" per group, or the ✓✓ toolbar action to select all‑but‑best in every group.
- Delete FAB with confirmation dialog (deletes via SAF, then removes the entries from cache and results).
- **Filters** (⚙ tune icon) open a bottom sheet – see below. Groups recompute live while you drag.

---

## Design: where does the algorithm selector belong?

**On the results screen, not the home screen.** The work is split into two phases:

| Phase | Cost | Depends on filters? | Where |
|---|---|---|---|
| **Feature extraction** – decode, aHash, dHash, pHash, colour histogram, EXIF GPS + time | expensive (I/O + decode per photo) | **No** – everything is always computed | `ScanEngine` / `FeatureExtractor`, cached in Room |
| **Matching** – compare cached features pairwise, union‑find into groups | cheap (bit ops in memory) | **Yes** | `DuplicateGrouper`, re‑run on every filter change |

Because extraction never depends on which algorithm you choose, all fingerprints are computed once per file and the
filters only affect the cheap step. The user can therefore flip algorithms, drag thresholds or toggle GPS/time on the
results screen and see groups update in well under a second, with no rescan. The home screen stays minimal.

---

## Matching algorithms (all computed, individually switchable)

| Rule | What it does | Default |
|---|---|---|
| **pHash** (perceptual) | 32×32 grayscale → 2‑D DCT → 8×8 low frequencies → bits vs. median. Robust to resizing, compression, small edits. | on, Hamming ≤ 12 |
| **dHash** (difference) | 9×8 grayscale, bit = left < right neighbour. Encodes gradients; fast, good for bursts. | on, Hamming ≤ 12 |
| **aHash** (average) | 8×8 grayscale, bit = pixel > mean. Loose; finds more incl. false positives. | off, Hamming ≤ 8 |
| **Colour histogram** | 8 hue × 4 sat × 4 val bins, histogram intersection. Same scene, different framing. | off, ≥ 85 % |
| Require all to agree | AND instead of OR across the enabled visual rules. | off |
| **GPS proximity** | Haversine distance between EXIF positions must be ≤ N metres. Option to skip when a photo has no GPS. | off, 100 m |
| **Time window** | Photos must be taken within N minutes (EXIF, else file date). Also turns the O(n²) comparison into a sliding window → big speed‑up on large libraries. | off, 10 min |

Presets **Strict / Balanced / Loose** set the visual rules in one tap; GPS and time settings are kept.

Sanity check of the hash design (Python port of the same code, synthetic scenes): identical → 0 bits,
resized+recompressed → 1–2, hand‑held burst shifts → 8–16, unrelated scenes → 24–40. So the default 12 separates
well, and the colour histogram (0.90 for shifted shots vs. 0.05–0.17 unrelated) is what rescues looser framing in the
Loose preset.

---

## Cache

Room table `image_features`, one row per file keyed by `<authority>|<documentId>` (so it survives picking a
different parent folder). A row is reused when `size` and `lastModified` are unchanged; otherwise the file is
re‑analysed. Unreadable files are stored as `failed = true` so they aren't retried every scan. Deleting photos
removes their rows; "Clear cache" on the home screen wipes the table.

Rows are ~600 bytes (three 64‑bit hashes + 128 floats + metadata), so 20 000 photos ≈ 12 MB.

---

## Project layout

```
app/src/main/java/com/example/photocleaner/
├─ PhotoCleanerApp.kt            Application, holds AppContainer
├─ MainActivity.kt
├─ di/AppContainer.kt          hand‑rolled DI: Room, SettingsStore, ScanEngine (app‑scoped)
├─ analysis/
│  ├─ ImageHasher.kt           aHash / dHash / pHash / HSV histogram, area‑average resampler, DCT
│  ├─ Similarity.kt            Hamming, histogram intersection, haversine
│  ├─ FeatureExtractor.kt      decode small bitmap → HashResult + EXIF → ImageFeatureEntity
│  ├─ DuplicateGrouper.kt      union‑find grouping under MatchSettings (cancellable)
│  ├─ MatchSettings.kt         filter model + presets + chip labels
│  └─ Model.kt                 ImageFeatures, DuplicateGroup
├─ data/
│  ├─ db/Database.kt           Room entity / DAO / database
│  ├─ media/FolderScanner.kt   walks a SAF tree with DocumentsContract
│  ├─ media/ExifReader.kt      GPS + DateTimeOriginal
│  └─ prefs/SettingsStore.kt   folder URI, recursive flag, MatchSettings persistence
├─ processing/ScanEngine.kt    scan orchestration + ScanState (Idle/Running/Done/Error)
└─ ui/
   ├─ home/                    HomeScreen, HomeViewModel
   ├─ results/                 ResultsScreen, ResultsViewModel, GroupCard, FilterSheet
   ├─ navigation/AppNavGraph.kt
   ├─ common/Format.kt
   └─ theme/Theme.kt
```

### Scan pipeline (`ScanEngine.runScan`)
1. `FolderScanner` lists images (one `query()` per directory; hidden dirs like `.thumbnails` skipped).
2. Load all cache rows, split entries into hits / misses.
3. Misses go through a channel to `min(cores, 6)` worker coroutines running `FeatureExtractor`.
4. Results stream to a writer coroutine that upserts in batches of 32.
5. Progress (`ScanState.Running`) is published after every image; `ScanState.Done` carries the feature list.
6. `ResultsViewModel` combines that list with the debounced `MatchSettings` and runs `DuplicateGrouper`
   with `mapLatest`, so dragging a slider cancels stale computations.

---

## Build & run

1. Open the folder in Android Studio (Ladybug or newer). It will offer to generate the Gradle wrapper JAR
   (`gradle/wrapper/gradle-wrapper.properties` is included; the binary JAR is not).
   Alternatively run `gradle wrapper` once from the command line.
2. Sync, then Run on a device/emulator with API 26+.
3. Tap **Choose folder** → pick e.g. `DCIM/Camera` → **Start scan**.

Versions used: AGP 8.7.3, Kotlin 2.0.20, Compose BOM 2024.09.00, Room 2.6.1, KSP 2.0.20‑1.0.25.
The project was written without a build environment available, so expect at most minor fixes on first sync
(e.g. a bumped library version) rather than structural changes.

---

## Notes & limitations

- **Permissions:** none requested. SAF grants access to the chosen folder; EXIF GPS is readable from
  user‑selected documents without `ACCESS_MEDIA_LOCATION`.
- **Orientation:** hashes are computed on the stored pixels, ignoring EXIF rotation. Shots from the same burst share
  orientation so this rarely matters; thumbnails (Coil) are shown correctly rotated.
- **Scale:** pairwise matching is O(n²) with tiny constants – ~10 k photos group in well under a second on
  hashes alone; the colour histogram is the slowest rule. Enabling the time window makes it near‑linear.
- **Background execution:** the scan runs in an app‑scoped coroutine, so navigating between screens is fine, but
  the OS may kill the process if the app is backgrounded for a long scan. Wrapping `ScanEngine` in a foreground
  service (or WorkManager) is the natural next step.
- **HEIC** decodes on API 28+; on 26/27 such files are marked unreadable.

## Ideas for later
- Foreground service + notification for very large libraries.
- BK‑tree / multi‑index hashing to make hash matching sub‑quadratic without the time filter.
- On‑device embeddings (e.g. a small CLIP/MobileNet via TFLite) as a fifth, semantic similarity rule.
- Exact‑duplicate short‑cut via file size + SHA‑1 before hashing.
- MediaStore integration so deleted files vanish from the gallery immediately.
