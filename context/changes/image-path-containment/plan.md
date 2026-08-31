# Vehicle Image Write Path Implementation Plan

> Covers roadmap slices **S-02** (`image-write-ordering`, FR-004), **S-05**
> (`image-path-containment`, FR-008) and **S-04** (`image-format-allowlist`, FR-006), under the
> FR-007 guardrail. Scope was widened from S-05 alone by owner decision at research time; see
> `change.md` § "Research scope decision — 2026-08-31".

## Overview

Three defects sit on the vehicle image write path, all in code untouched since January 2019: a
replaced image file is deleted before the transaction commits (so a rollback destroys it), the
stored file's extension is chosen from a client-declared content type that is never validated, and
the path helper that reaches the data directory has no containment check and normalises in the
wrong order. This plan fixes all three behind a test harness the path currently lacks entirely,
without moving the client contract for reads.

## Current State Analysis

`ImageStorageServiceImpl` (72 lines) is the sole gateway to
`application.data-directory.location` in all of `src/main/java`. Three public methods and one
private path helper; the only other filesystem code in the tree (`config/WebConfigurer.java:72,90`)
resolves the static asset root and never touches the data directory.

**S-02 — the ordering defect is worse than "delete before commit".** In
`VehicleServiceImpl.editVehicle:56-61`, `vehicleMapper.vehicleDtoToVehicle(vehicleDto)` is
evaluated first, and that is what calls `save()` — so the **new** file lands on disk before
anything else. `updateVehicle:76` then deletes the **old** file synchronously, and only afterwards
does `vehicleRepository::save` run, with the flush and commit later still. A rollback anywhere
after that point leaves the old file destroyed *and* the new file orphaned, with the restored row
pointing at a filename that no longer exists. `FileUtils.deleteQuietly` swallows every failure.
`addVehicle` has no old file and `deleteVehicle:65-73` is a soft archive that never touches the
volume, so `editVehicle` is the entire scope.

**The fix pattern already exists in this repository, once.** `AdminVehicleServiceImpl:103-141`
(from the archived `archived-vehicle-purge` change) reads the filename before mutation, registers a
`TransactionSynchronization`, deletes only under `STATUS_COMMITTED`, checks `delete()`'s boolean
return, and logs rather than throws — with the reasoning written into the comment at `:122-125`.
`TransactionSynchronizationManager` appears nowhere else in `src/main/java`. That reasoning was
never propagated back to `VehicleServiceImpl`, which predates it by years.

**S-04 — the write path trusts an unvalidated client string.** `VehicleDetailsMapper:51-52` passes
`vehicleDetailsDto.getImageContentType()` straight into `save()`, which feeds it to
`MimeTypes.getDefaultMimeTypes().forName(...)` to pick the extension. That field carries no
validation annotation, and `VehicleDto.vehicleDetails` carries no `@Valid`, so the controller's
`@Valid @RequestBody VehicleDto` never cascades into it. The production consequence is measured:
four of the nine files on the volume are PNG bytes stored under a `.bin` name because a client
declared `application/octet-stream`, whose first glob in Tika's registry is `*.bin`.

A second, minor consequence is also measured: `MimeTypes.getDefaultMimeTypes()` returns a
JVM-lifetime static singleton, and `forName()` on a syntactically valid but unregistered media type
permanently adds an entry to it (1613 → 1618 over five probes). Authenticated-only and slow, but
unbounded and reachable from a request field. Ceasing to pass client input to `forName` closes it.

**S-05 — the path helper has two independent problems.** `prepareImagePath:68-71` is
`Paths.get(location).normalize().resolve(fileName).normalize().toAbsolutePath()`. There is no
containment check at all, and because `normalize()` runs *before* `toAbsolutePath()`, leading `..`
segments cannot be collapsed and survive into the returned path — measured: `../../../../etc/passwd`
comes back still containing `..`. An absolute `fileName` bypasses the base entirely, which a
`..`-only check would not catch.

**The FR-008 premise was verified and holds, with a qualification.** Client input cannot reach the
`name` parameter of `load`/`delete`: all three such call sites read
`getVehicleDetails().getImage()` off an entity fetched by numeric id, and that column
(`VehicleDetails:71-73`) is written in exactly one place, `VehicleDetailsMapper:51-53`, always from
`save()`'s server-generated `UUID + extension`. There is no MapStruct on this path — the three
vehicle "mappers" are hand-written `@Service` classes — so no generated code hides a second write.
But `imageContentType` *is* client-controlled and *does* select the extension, and containment
survives only because tika-core 2.7.0's registry rejects traversal-shaped media-type names and none
of its 1,320 globs contains `/` or `..`. That is a property of a dependency's data file, not an
invariant this application enforces.

**Test coverage is effectively zero.** No direct test of `ImageStorageServiceImpl`; `load()` is
referenced nowhere in `src/test`; no test creates or updates a vehicle *with* an image through
REST; nothing anywhere asserts filesystem state after a rollback. The one test that writes real
files, `AdminVehiclePurgeIT`, keeps a private duplicate of `prepareImagePath`'s logic at `:145-148`,
and `SessionFixtures.imageFor:159` saves `"fake-png-bytes".getBytes()` under a declared
`"image/png"` — bytes that are not a PNG, so the allowlist in Phase 4 will reject them.

`application-test.yml:76-78` sets `location: data`, a relative path resolving to the repo root's
gitignored `data/` directory, shared by the whole suite and with `dev`, with no isolation.

## Desired End State

- Replacing a vehicle image and then failing the transaction leaves the previously stored file on
  disk and loadable, and leaves no orphaned new file behind.
- Every path the storage service resolves lies under the configured data directory, or the
  operation is refused and logged; no caller's observable contract changes.
- Only byte-verified PNG and JPEG are written to the volume. A client that declares a *specific*
  image type contradicted by the bytes gets a 400; a client that declares nothing specific is
  accepted and the sniffed type decides the extension.
- All nine files already on the volume — including the four named `*.bin` — remain loadable and
  are reported to the client exactly as they are today.

Verified by: `./mvnw verify` green, with new tests that fail against the pre-change code in each
phase.

### Key Discoveries:

- The three slices land in **three disjoint methods across two files** —
  `VehicleServiceImpl.updateVehicle:75-83`, `ImageStorageServiceImpl.prepareImagePath:68-71`,
  `ImageStorageServiceImpl.save:28-45`. The roadmap's textual-conflict rationale for the
  S-02 → S-04 prerequisite does not survive contact with the code (research §10); the prerequisite
  is honoured here anyway by phase order.
- The post-commit delete pattern to copy is `AdminVehicleServiceImpl:122-141`. Note the research
  doc records that this callback ignores `delete()`'s boolean return (impl-review F5) — **it does
  not**; `:132-134` checks it and logs a warning. Copy it as written.
- `Tika#detect(byte[])` is present in tika-core 2.7.0 (`javap`-verified). No new dependency: the
  pinned `tika-core` ships its own magic-byte database and `DefaultDetector`.
- The error convention is a `RuntimeException` in `service/exception/` plus an `@ExceptionHandler`
  in `ExceptionTranslator` building a `ProblemDetail` and setting `title` from `getMessage()` —
  see `InvalidLookupTypeException` / `ExceptionTranslator:124-129`.
- `src/main/resources/default.png` is a real 300×300 PNG, usable as genuine image bytes in tests.
- `VehicleDetailsMapper:35` calls `tika.detect(filename)` on the **read** path — extension-based,
  and the reason the four `.bin` files report `application/octet-stream`. Untouched by this plan.

## What We're NOT Doing

- **Not touching the read path.** `VehicleDetailsMapper:34-35` keeps `load()` and
  `tika.detect(filename)` exactly as they are. Allowlist enforcement on read would make the four
  legacy `.bin` files unloadable and break FR-007; switching read-path *detection* to byte-sniffing
  would not break FR-007 but is a client-visible contract change against a frozen client 1.2.5,
  and is deliberately out of scope (research OQ4).
- **Not migrating or renaming the four legacy `*.bin` files.** FR-007 requires only that they stay
  loadable; with the read path unchanged, that is satisfied trivially.
- **Not cleaning up pre-existing orphaned files** on the volume. Orphans predate this change
  (`context/archive/2026-08-29-archived-vehicle-purge/research.md:188-199`) and no slice here
  claims to fix them.
- **Not adding `@Valid` to `VehicleDto.vehicleDetails`.** The missing cascade is a real latent
  issue but a separate one; adding it changes validation behaviour for eight unrelated fields.
- **Not resolving the empty-string-sentinel question** (research OQ1 / roadmap S-02 Unknown) —
  whether any `vehicle_details.image` row holds `""` needs a read of the production column, which
  no one has had access to. Phase 4 makes new occurrences impossible; existing ones, if any, keep
  serving `default.png` exactly as today.
- **Not cleaning up rollback orphans on the create path.** Phase 2's deferred deletion is
  registered in `updateVehicle`, which only `editVehicle` reaches. `addVehicle` writes its image
  file through the mapper and registers nothing, so a transaction that rolls back *after* a
  successful write — a constraint violation at flush, say — still leaves an orphan. FR-004 is
  about the *old* file surviving and is unaffected; the rollback cleanup added in Phase 2 is a
  deliberate extra, and extending it symmetrically to `addVehicle` would widen that phase a second
  time for a case with no measured frequency. Left as a known, stated gap, alongside the
  pre-existing orphans above.
- **Not replacing the storage backend.** The parked `minio-object-storage` change would delete
  this path logic entirely; that was known and accepted when FR-008 was kept.

## Implementation Approach

Build the missing test harness first, so every subsequent phase has a test that fails before the
change and passes after. Then take the three slices in dependency-safe order — S-02 (ordering),
S-05 (containment), S-04 (allowlist) — each self-contained in its own method, each independently
revertible. S-04 goes last because it is the only phase that changes an existing fixture and the
only one carrying client-contract risk.

## Critical Implementation Details

**Timing & lifecycle (Phase 2).** The new image file is written by the mapper *before*
`updateVehicle` runs, so by the time the deferred delete is registered there are two filenames in
play, not one: the old one to delete on commit, and the new one to delete on rollback. Both must be
captured as effectively-final locals before the entity mutation at `:81` overwrites
`vehicle.getVehicleDetails()`. A single `TransactionSynchronization` handles both branches off the
`status` argument; `afterCompletion` runs for rollback as well as commit, which is why it — not
`afterCommit` — is the right hook.

**State sequencing (Phase 3).** `.toAbsolutePath()` must precede `.normalize()`. The reverse order,
which is what the code does today, cannot collapse leading `..` segments and would leave a
containment check testing an unnormalised path.

## Phase 1: Test harness and baseline coverage

### Overview

Give this path the isolation and the tests it has never had, pinning current behaviour before
anything moves. No production code changes in this phase.

### Changes Required:

#### 1. Per-class temp data directory

**File**: `src/test/java/com/kasztelanic/carcare/web/rest/AbstractImageIT.java` (new)

**Intent**: A base class for the new filesystem-touching ITs that points
`application.data-directory.location` at a JUnit temp directory, so no test writes into the repo's
shared `data/` directory or the one a developer running `dev` is using. Satisfies the roadmap's
S-02 risk note ("scratch directory, never the production volume") by construction rather than
incidentally.

**Contract**: Extends `AbstractSessionIT`. A **single JVM-wide scratch root**, created once in a
`static` initializer via `Files.createTempDirectory(...)` and removed by a shutdown hook, plus a
`@DynamicPropertySource` method registering `application.data-directory.location` as that root's
absolute path. Exposes `protected Path imagePath(String fileName)` resolving against it, so tests
never duplicate the production helper's logic.

**Do not use a `static @TempDir` field here.** JUnit resolves `@TempDir` per *concrete* test class,
so every subclass would register a different property value — a different
`MergedContextConfiguration` cache key, and therefore a **separate Spring context per subclass**
(three of them by Phase 3). One shared root keeps the registered value identical across subclasses
so they share a single context. The cost is that JUnit no longer cleans up for us, hence the
shutdown hook; the tradeoff is deliberate. Per-class collision is avoided by resolving each class's
files under the shared root, not by varying the property.

#### 2. Real-image fixtures

**File**: `src/test/java/com/kasztelanic/carcare/fixtures/SessionFixtures.java`

**Intent**: Provide genuine PNG and JPEG bytes for tests, and a REST-level path to create or update
a vehicle carrying an image — neither exists today.

**Contract**: Two static helpers producing real bytes via `javax.imageio.ImageIO.write` on a small
`BufferedImage` (`"png"` and `"jpg"`), and a helper returning a `VehicleDto` with
`vehicleDetails.image` / `imageContentType` populated, for posting through `/api/vehicle`.
`imageFor(...)` keeps its current signature and behaviour in this phase; Phase 4 changes what it
passes.

#### 3. First direct unit test of the storage service

**File**: `src/test/java/com/kasztelanic/carcare/service/impl/ImageStorageServiceImplTest.java` (new)

**Intent**: Pin the storage service's current, unchanged behaviour so Phases 3 and 4 have a
baseline that visibly moves. Constructed directly with a stub `ApplicationProperties` pointing at a
`@TempDir` — no Spring context, matching `RequestBodyLimitFilterTest`'s raw-unit tier.

**Contract**: Covers save/load round-trip for PNG and JPEG; `save(null, ...)` → `""`;
`save(bytes, "not a mime type")` → `""` and nothing written; `load(null)` / `load("")` /
`load("missing.png")` → the `default.png` bytes; `delete(null)` / `delete("")` → `false`;
`delete` of an existing file → `true` and the file is gone.

#### 4. REST-level image coverage

**File**: `src/test/java/com/kasztelanic/carcare/web/rest/VehicleImageIT.java` (new)

**Intent**: Prove, for the first time, that a vehicle can be created and updated *with* an image
through the real filter chain and that the bytes round-trip back to the client — the end-to-end
behaviour all three slices must preserve.

**Contract**: Extends `AbstractImageIT`. POST `/api/vehicle` with a real PNG → 200, a file exists
under the temp data directory, and GET returns the same bytes with a PNG `imageContentType`. PUT
replacing the image → the response carries the new bytes.

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `JAVA_HOME=~/.sdkman/candidates/java/17.0.20-tem ./mvnw test`
- Full suite passes: `JAVA_HOME=~/.sdkman/candidates/java/17.0.20-tem ./mvnw verify`
- The new tests are additive: unit count rises from 38 and integration count from 249, with no *integration* test skipped (`./mvnw test` keeps exactly one skip — the intentionally `@Disabled` case in `WebConfigurerTest`)
- No file appears in the repo's `data/` directory after a full run: `find data -type f | wc -l` returns `0` both before and after `./mvnw verify`. Note `data/` is gitignored (`.gitignore:5`, `/data/**`), so `git status` can never observe a stray file there — the check must read the filesystem directly

#### Manual Verification:

- The shared scratch root is removed after the run — no leftover directory under the JVM temp root
- All classes extending `AbstractImageIT` share one Spring context: the startup banner appears once for them, not once per class

**Implementation Note**: After completing this phase and all automated verification passes, pause
here for manual confirmation from the human that the manual testing was successful before
proceeding to the next phase.

---

## Phase 2: S-02 — delete a replaced image only after commit

### Overview

Move the file deletion in `editVehicle` to after a successful commit, and clean up the newly
written file if the transaction rolls back instead. Closes FR-004.

### Changes Required:

#### 1. Deferred deletion in the vehicle service

**File**: `src/main/java/com/kasztelanic/carcare/service/impl/VehicleServiceImpl.java`

**Intent**: Replace the synchronous `imageStorageService.delete(...)` at `:76` with a
transaction-scoped callback that deletes the old file only when the transaction commits, and
deletes the newly written file when it rolls back. Mirrors `AdminVehicleServiceImpl:122-141`
rather than inventing a variant — including its discipline of never throwing from the callback and
never silently swallowing a failed delete.

**Contract**: `updateVehicle` captures both filenames as effectively-final locals before mutating
the entity, and registers a single `TransactionSynchronization` whose `afterCompletion(int status)`
deletes the old filename under `STATUS_COMMITTED` and the new filename otherwise. Each branch
guards against null/empty, checks `delete()`'s boolean return, logs a warning naming the file when
it returns false, and catches `RuntimeException` so the callback cannot throw. The method keeps its
signature and still returns the mutated `vehicle`. `VehicleServiceImpl` gains `@Slf4j`. Registration
requires an active synchronization — guaranteed here by the enclosing `@Transactional` on
`editVehicle`, but guard with `TransactionSynchronizationManager.isSynchronizationActive()` so a
future non-transactional caller degrades to a no-op rather than throwing.

**Rollback-cleanup note**: FR-004 requires only that the old file survive. Deleting the orphaned
new file on rollback is a deliberate addition, made because the same callback is already being
registered and the alternative is knowingly creating orphans on every failed edit. It is
self-contained and can be dropped without affecting FR-004.

#### 2. Rollback proof

**File**: `src/test/java/com/kasztelanic/carcare/web/rest/VehicleImageRollbackIT.java` (new)

**Intent**: Prove the old file survives a failed update — the one thing no existing test in the
suite can observe, since a class-`@Transactional` test never reaches `STATUS_COMMITTED`.

**Contract**: Extends `AbstractImageIT`, method annotated
`@Transactional(propagation = Propagation.NOT_SUPPORTED)` with `try/finally` row cleanup via
`sessionFixtures.purgeRowsFor(...)`, following `AdminVehiclePurgeIT:66-68`. Drives an
`editVehicle` whose transaction is forced to roll back — inject the failure with a `@SpyBean` on
`VehicleRepository` stubbing `save` to throw. `SessionFixtures`' `vehicleFor` / `imageFor` /
`archive` all call that same `save`, so the `doThrow` stub must be installed **after** fixture setup
and `Mockito.reset(...)`-ed in the `finally` block **before** `purgeRowsFor` — otherwise the test
fails during setup or cleanup rather than at the point under test — then asserts the old file still exists and is
byte-identical, and that the new file (whose name is captured from the spy's argument) does not
exist. A companion test asserts the committed path still deletes the old file.

### Success Criteria:

#### Automated Verification:

- The rollback IT fails against the pre-change `VehicleServiceImpl` and passes after
- Full suite passes: `JAVA_HOME=~/.sdkman/candidates/java/17.0.20-tem ./mvnw verify`
- `ArchTest` still passes — no `web` dependency introduced into `service`

#### Manual Verification:

- A successful image replacement through the running `dev` app leaves exactly one file per vehicle in the data directory, with the old one gone

**Implementation Note**: After completing this phase and all automated verification passes, pause
here for manual confirmation from the human that the manual testing was successful before
proceeding to the next phase.

---

## Phase 3: S-05 — contain every image path under the data directory

### Overview

Fix the normalise/absolutise ordering and add the containment check that nothing currently
enforces, in the one helper that all three public methods route through. Closes FR-008.

### Changes Required:

#### 1. Containment in the path helper

**File**: `src/main/java/com/kasztelanic/carcare/service/impl/ImageStorageServiceImpl.java`

**Intent**: Make `prepareImagePath` refuse any name that resolves outside the configured data
directory, and fix the ordering defect that would otherwise leave the check inspecting an
unnormalised path.

**Contract**: The helper resolves the root as `Paths.get(location).toAbsolutePath().normalize()`
and the candidate as `root.resolve(fileName).toAbsolutePath().normalize()`, then throws an
unchecked exception unless `candidate.startsWith(root) && !candidate.equals(root)`. The
`!equals(root)` clause refuses a name that resolves to the directory itself — today only reachable
via `""`, which `load`/`delete` already short-circuit at `:50`/`:62`, so behaviour is unchanged;
making it explicit puts the rule in one place (research OQ3). Ordering is load-bearing:
`toAbsolutePath()` before `normalize()`.

The exception is a new unchecked type in `service/exception/` carrying the offending name — that
package, not `service/impl`, because all eight existing unchecked service exceptions live there. It
is **not** given an `ExceptionTranslator` handler, because no caller is allowed to let it escape —
see the next item.

#### 2. Callers absorb the refusal

**File**: `src/main/java/com/kasztelanic/carcare/service/impl/ImageStorageServiceImpl.java`

**Intent**: Keep every caller's observable contract identical. A containment refusal is logged and
then falls back to the sentinel that method already uses for failure, so no client-visible
behaviour moves and a refusal can never become a 500.

**Contract**: `save` returns `""` (joining the existing `MimeTypeException` / `IOException`
branches), `load` returns the `default.png` bytes, `delete` returns `false` — each logging at
`error` with the offending name. Note `load` calls `prepareImagePath` twice (`:50`, `:53`); collapse
to one call while adding the guard.

#### 3. Remove the duplicated helper in the purge IT

**File**: `src/test/java/com/kasztelanic/carcare/web/rest/AdminVehiclePurgeIT.java`

**Intent**: `:145-148` holds a private copy of the old `prepareImagePath` expression, which will
silently stop matching production the moment this phase lands.

**Contract**: Delete the private `imagePath(...)` method and make `AdminVehiclePurgeIT` **extend
`AbstractImageIT`**, inheriting the shared helper. Extending is the required form, not one of two
options: merely relocating the helper would leave this IT still writing into the repo's shared
`data/` directory, which is exactly what Phase 1's criterion 1.4 exists to rule out. The purge IT's
assertions and cleanup are otherwise unchanged.

#### 4. Containment cases

**File**: `src/test/java/com/kasztelanic/carcare/service/impl/ImageStorageServiceImplTest.java`

**Intent**: Pin the containment table directly, since none of these inputs is reachable through
REST — a REST-only test would leave FR-008 with no coverage at all.

**Contract**: A parameterised case per row of the measured table — `../../../../etc/passwd`,
`/etc/passwd` (absolute), `..`, and `sub/../ok.png` (contained, must still be accepted) — asserting
refusal or acceptance, and asserting that each public method returns its documented sentinel rather
than propagating. Plus a regression case that the returned path for a contained name is absolute
and normalised.

### Success Criteria:

#### Automated Verification:

- The containment cases fail against the pre-change helper and pass after
- Full suite passes: `JAVA_HOME=~/.sdkman/candidates/java/17.0.20-tem ./mvnw verify`
- No remaining copy of the path expression outside `ImageStorageServiceImpl`: `grep -rn "getDataDirectory().getLocation()" src/` returns **only** `ImageStorageServiceImpl` — `AbstractImageIT`'s helper resolves against its `@TempDir`, not against the property, so it must not appear either

#### Manual Verification:

- Loading and replacing an image through the running `dev` app behaves exactly as before — the guard is invisible in normal use

**Implementation Note**: After completing this phase and all automated verification passes, pause
here for manual confirmation from the human that the manual testing was successful before
proceeding to the next phase.

---

## Phase 4: S-04 — store only byte-verified PNG and JPEG

### Overview

Determine the stored file's type from its actual bytes, accept only PNG and JPEG, and reject a
client that declares a specific image type its bytes contradict. Write path only. Closes FR-006
while preserving FR-007.

### Changes Required:

#### 1. Byte-sniffing allowlist in `save`

**File**: `src/main/java/com/kasztelanic/carcare/service/impl/ImageStorageServiceImpl.java`

**Intent**: Stop deriving the extension from the client-declared content type. Sniff the bytes,
accept only the allowlist, and choose the extension from the sniffed type. This also ends the
unbounded growth of Tika's static MIME registry, since no client string reaches `forName` any more.

**Contract**: `save(byte[] image, String fileType)` keeps its signature (the interface and its one
caller are unchanged). It detects with a `Tika` instance field — `tika.detect(image)`, verified
present in tika-core 2.7.0 — and maps the result through a fixed allowlist of exactly
`image/png` → `.png` and `image/jpeg` → `.jpg`, chosen to match what the four legacy octet-stream
uploads and the five correctly-typed ones actually are. A detected type outside the allowlist
throws (see next item). `MimeTypes.getDefaultMimeTypes().forName(...)` and the `MimeTypeException`
catch are removed. The `IOException` catch and the `""` return stay for genuine write failures; the
`image == null` guard stays; and **the containment catch added in Phase 3 stays exactly as that
phase left it** — this phase changes only the type-detection branch.

#### 2. Mismatch rule for the declared content type

**File**: `src/main/java/com/kasztelanic/carcare/service/impl/ImageStorageServiceImpl.java`

**Intent**: The declared type no longer decides anything, but a client that makes a *specific*
image claim contradicted by the bytes is lying and should be told. A client that makes no specific
claim is accepted — this is the case that produced the four legacy `.bin` files, so it is the one
client behaviour on this path we have hard production evidence for, and breaking it would make
uploads vanish with no visible error (client 1.2.5 renders 4xx as a silent false success, measured
in S-03).

**Contract**: Treat `null`, blank, `application/octet-stream`, and any non-`image/*` value as "no
claim" — accept, and let the sniffed type decide. When `fileType` is an `image/*` type that differs
from the sniffed type, throw. Compare on the bare type/subtype, ignoring any `;charset=` parameter
and case.

#### 3. Rejection surfaces as a 400

**File**: `src/main/java/com/kasztelanic/carcare/service/exception/UnsupportedImageFormatException.java` (new)
and `src/main/java/com/kasztelanic/carcare/web/rest/errors/ExceptionTranslator.java`

**Intent**: Turn both rejection cases — bytes outside the allowlist, and a contradicted specific
claim — into a 400 that rolls the transaction back, rather than the silent `""` sentinel that makes
a failed image indistinguishable from no image.

**Contract**: An unchecked exception in `service/exception`, following `InvalidLookupTypeException`
exactly (a single constructor building the message). A handler in `ExceptionTranslator` alongside
`handleInvalidLookupTypeException` at `:124-129`: `ProblemDetail.forStatus(BAD_REQUEST)`, `title`
from `getMessage()`, returned through `handleExceptionInternal`, with a comment noting this is a
rejected client request rather than a fault so it gets no stack trace. Because `save` is called
from the mapper inside `@Transactional addVehicle`/`editVehicle`, the throw rolls the transaction
back before any row is written. **No file cleanup is involved**: every rejection here — the
allowlist miss and the contradicted claim alike — is decided from the detected type *before*
`writeByteArrayToFile` runs, so nothing was ever written. The Phase 2 callback plays no part on
this path and must not be relied on: in `editVehicle:58` the mapper (and therefore `save`) is
evaluated *before* `updateVehicle`, which is the only place that callback is registered, so a
Phase 4 rejection never reaches the registration at all.

#### 4. Update the fixture the allowlist breaks

**File**: `src/test/java/com/kasztelanic/carcare/fixtures/SessionFixtures.java`

**Intent**: `imageFor:159` saves `"fake-png-bytes".getBytes()` declared as `image/png`. Those bytes
are not a PNG, so this fixture starts throwing the moment the allowlist lands, breaking
`AdminVehiclePurgeIT`.

**Contract**: Switch it to the real PNG bytes helper added in Phase 1. Its signature and the purge
IT's assertions are unchanged.

#### 5. Allowlist cases

**Files**: `src/test/java/com/kasztelanic/carcare/service/impl/ImageStorageServiceImplTest.java`
and `src/test/java/com/kasztelanic/carcare/web/rest/VehicleImageIT.java`

**Intent**: Cover the accept, reject and no-claim branches at the unit tier, and the 400 contract
at the REST tier.

**Contract**: Unit — real PNG declared `image/png` → `.png`; real JPEG declared `image/jpeg` →
`.jpg`; real PNG declared `application/octet-stream` → accepted, `.png` (the legacy case, which
must keep working); real PNG declared `image/jpeg` → throws; a GIF or plain-text payload → throws
and writes nothing; sniffed extension wins regardless of the declared one. REST — POST with a
non-image body returns 400 with a `ProblemDetail`, no vehicle row is created, and the data
directory is empty afterwards.

### Success Criteria:

#### Automated Verification:

- The allowlist cases fail against the pre-change `save` and pass after
- Full suite passes: `JAVA_HOME=~/.sdkman/candidates/java/17.0.20-tem ./mvnw verify`
- No `MimeTypes` usage remains in `src/main`: `grep -rn "org.apache.tika.mime" src/main/java` returns nothing
- The read path is untouched: `git diff -- src/main/java/com/kasztelanic/carcare/service/mapper/VehicleDetailsMapper.java` shows no hunk touching the `load(...)` / `tika.detect(...)` lines (34-35). `--stat` is file-level and cannot express a line range, so read the diff itself

#### Manual Verification:

- Against a `dev` instance seeded with a copy of the production volume's nine files, every one — including the four `*.bin` — still loads in the client and reports the same `imageContentType` as before (FR-007)
- Uploading a real PNG through client 1.2.5 succeeds and the stored file is named `*.png`, not `*.bin`
- Uploading a non-image through the client is rejected (noting the client renders the 400 as a silent false success — expected, and an accepted limitation carried from S-03)

**Implementation Note**: After completing this phase and all automated verification passes, pause
here for manual confirmation from the human that the manual testing was successful before
proceeding to the next phase.

---

## Phase 5: Roadmap and requirement reconciliation

### Overview

Bring the foundation documents into line with what shipped and what the research established. No
code changes.

### Changes Required:

#### 1. Roadmap status and sequencing

**File**: `context/foundation/roadmap.md`

**Intent**: Record S-02, S-04 and S-05 as `done`, and correct the S-02 → S-04 prerequisite's stated
rationale, which the code does not support.

**Contract**: Update the three rows in "At a glance", the three `Status:` fields in "Streams", and
the "Backlog Handoff" table. Amend S-04's `Prerequisites` note to say the three slices touch
disjoint methods and that the prerequisite was honoured by phase order within one change rather
than by a textual conflict. Add a line to S-02's Unknown recording that the empty-string-sentinel
question remains unmeasured and why it is now harmless going forward.

#### 2. FR-008's verified premise

**File**: `context/foundation/prd.md`

**Intent**: The PRD records FR-008's premise — "filenames are server-generated UUIDs with no client
influence" — as accepted-as-true. The research verified it and found it narrower than stated.

**Contract**: Append a dated note under FR-008's Socratic record: the premise holds for
`load`/`delete`, but `imageContentType` is client-controlled and did select the stored extension,
and containment held only on a property of tika-core's data file until this change. FR-008 is
better characterised as guarding an invariant nothing else enforced than as guarding an unreachable
path. Do not rewrite the original record — append, as the other dated amendments in that file do.

#### 3. Close the change

**File**: `context/changes/image-path-containment/change.md`

**Contract**: `status: done`, `updated` to the completion date, and a short note recording that one
change closed three roadmap slices.

### Success Criteria:

#### Automated Verification:

- No roadmap slice still reads `Status: ready` or `proposed` for S-02, S-04 or S-05: `grep -n "Status:" context/foundation/roadmap.md`

#### Manual Verification:

- The roadmap's remaining open work is S-06 and S-07 only, and S-07's blocking branch/tag Unknown is still recorded

---

## Testing Strategy

### Unit Tests:

- `ImageStorageServiceImplTest` — the whole storage contract at the raw tier, with a `@TempDir`
  data directory and no Spring context: save/load/delete round-trip, `default.png` fallback, the
  `""` sentinel, the containment table (including the absolute-path and `..` escapes that are
  unreachable through REST), and the allowlist accept/reject/no-claim matrix.

### Integration Tests:

- `VehicleImageIT` — create and update a vehicle with a real image through the full filter chain;
  bytes round-trip; a non-image body is rejected with 400 and leaves no row and no file.
- `VehicleImageRollbackIT` — `NOT_SUPPORTED` propagation with an induced repository failure: the
  old file survives byte-identical, the new file is not orphaned, and the committed path still
  deletes the old file.
- `AdminVehiclePurgeIT` — unchanged assertions, but re-pointed at the shared path helper and the
  real-PNG fixture.

### Manual Testing Steps:

1. Seed a `dev` data directory with a copy of the production volume's nine files and confirm all
   nine still load in client 1.2.5, with unchanged `imageContentType` — including the four `*.bin`.
2. Upload a real PNG through the client; confirm the stored file is `*.png` and the old file is
   gone after the update commits.
3. Upload a non-image; confirm the server returns 400 and writes nothing, and note the client's
   silent-false-success rendering.
4. Confirm no file is left in the repo's `data/` directory after `./mvnw verify`.

## Performance Considerations

`Tika#detect(byte[])` reads only the leading magic bytes, so the added cost per upload is
negligible against the base64 decode already happening. The read path is untouched, so vehicle
listing — the hot path, which loads every vehicle's image bytes — is unaffected. The one real cost
is suite runtime. `AbstractImageIT`'s property override forces a Spring context distinct from the
default one, adding a context startup to `./mvnw verify` — **one**, not one per subclass, because
the shared scratch root registers an identical property value for every class that extends it (see
Phase 1 §1). `VehicleImageRollbackIT`'s `@SpyBean` forks the cache key once more regardless, so the
realistic ceiling is two additional contexts, not four.

## Migration Notes

No data migration. No Liquibase changelog. The nine files on the production volume are untouched,
and the four `*.bin` names stay as they are — only newly written files get a byte-derived
extension. Each phase is independently revertible; reverting Phase 4 alone restores the old
extension behaviour without affecting Phases 2 or 3.

## References

- Related research: `context/changes/image-path-containment/research.md`
- Scope decision: `context/changes/image-path-containment/change.md`
- Post-commit delete pattern to copy: `src/main/java/com/kasztelanic/carcare/service/impl/AdminVehicleServiceImpl.java:122-141`
- Error-shape convention: `src/main/java/com/kasztelanic/carcare/web/rest/errors/ExceptionTranslator.java:124-129`
- Non-transactional IT harness: `src/test/java/com/kasztelanic/carcare/web/rest/AdminVehiclePurgeIT.java:66-68`
- Production volume measurement: `context/changes/security-baseline/oq-resolution.md`
- Client 4xx rendering (accepted limitation): `context/archive/2026-08-30-request-body-limit/plan.md`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Test harness and baseline coverage

#### Automated

- [x] 1.1 Unit tests pass — 62ff5e9
- [x] 1.2 Full suite passes — 62ff5e9
- [x] 1.3 New tests are additive; counts rise from 38 / 249, no integration test skipped — 62ff5e9
- [x] 1.4 No file appears in the repo's `data/` directory after a full run (`find data -type f | wc -l` == 0 before and after) — 62ff5e9

#### Manual

- [x] 1.5 The shared scratch root is removed after the run (shutdown hook fired) — 62ff5e9
- [x] 1.6 All `AbstractImageIT` subclasses share one Spring context (startup banner appears once) — 62ff5e9

### Phase 2: S-02 — delete a replaced image only after commit

#### Automated

- [x] 2.1 Rollback IT fails before the change and passes after — 6785a38
- [x] 2.2 Full suite passes — 6785a38
- [x] 2.3 ArchTest still passes — 6785a38

#### Manual

- [x] 2.4 Successful replacement through `dev` leaves exactly one file, old one gone — 6785a38

### Phase 3: S-05 — contain every image path under the data directory

#### Automated

- [x] 3.1 Containment cases fail before the change and pass after — 2324cd8
- [x] 3.2 Full suite passes — 2324cd8
- [x] 3.3 No copy of the path expression outside `ImageStorageServiceImpl` (grep returns the service only) — 2324cd8

#### Manual

- [x] 3.4 Load and replace through `dev` behaves exactly as before — 2324cd8

### Phase 4: S-04 — store only byte-verified PNG and JPEG

#### Automated

- [x] 4.1 Allowlist cases fail before the change and pass after — 8ceb8e4
- [x] 4.2 Full suite passes — 8ceb8e4
- [x] 4.3 No `MimeTypes` usage remains in `src/main` — 8ceb8e4
- [x] 4.4 Read path untouched — `git diff` on `VehicleDetailsMapper` shows no hunk at lines 34-35 — 8ceb8e4

#### Manual

- [x] 4.5 All nine production files still load with unchanged `imageContentType` (FR-007) — 8ceb8e4
- [x] 4.6 Real PNG upload through client 1.2.5 stores a `*.png` file — 8ceb8e4
- [x] 4.7 Non-image upload is rejected server-side — 8ceb8e4

### Phase 5: Roadmap and requirement reconciliation

#### Automated

- [x] 5.1 No S-02 / S-04 / S-05 slice still reads `ready` or `proposed`

#### Manual

- [x] 5.2 Remaining open work is S-06 and S-07 only, with S-07's blocking Unknown intact
