# Vehicle Image Write Path — Plan Brief

> Full plan: `context/changes/image-path-containment/plan.md`
> Research: `context/changes/image-path-containment/research.md`

## What & Why

Three defects sit on the vehicle image write path, all in code untouched since January 2019. A
replaced image file is deleted before the transaction commits, so a rollback destroys it
irrecoverably (FR-004). The stored file's extension is chosen from a client-declared content type
that is never validated, which is why four files on the production volume are PNG bytes named
`*.bin` (FR-006). And the path helper that reaches the data directory has no containment check
and normalises in the wrong order (FR-008). One change closes all three, under the FR-007
guardrail that every file already on the volume stays loadable.

## Starting Point

`ImageStorageServiceImpl` is the sole gateway to the data directory in all of `src/main/java` —
72 lines, three public methods, one private path helper. Its containment behaviour and its trust
in the client content type are as originally written; the only changes since have been Lombok
reformatting. Test coverage is effectively zero: no direct test of the class, no test that creates
or updates a vehicle *with* an image through REST, and nothing anywhere asserting filesystem state
after a rollback. The fix pattern for the ordering defect already exists in this repository —
`AdminVehicleServiceImpl:122-141`, from the archived purge change — but was never propagated back.

## Desired End State

Replacing an image and then failing the transaction leaves the previous file intact and loadable,
with no orphaned new file. Only byte-verified PNG and JPEG reach the volume, and a client that
declares a specific image type its bytes contradict gets a 400 instead of a silently mistyped file.
Every path the storage service resolves lies under the configured data directory or the operation
is refused and logged. All nine files already stored — including the four `*.bin` — load exactly as
they do today.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| FR-008 premise | Verified, and narrower than recorded | Client input cannot reach `load`/`delete`, but `imageContentType` does select the extension, so containment held only on a tika-core data-file property. | Research |
| Slice packaging | One plan, three phases + harness | The dominant cost is the missing test scaffolding, and building it once serves all three slices. | Plan |
| S-02 → S-04 prerequisite | Honoured by phase order, rationale corrected | The three slices touch disjoint methods across two files, so the roadmap's textual-conflict argument does not hold. | Research |
| Allowlist rejection | 400 `ProblemDetail`, whole request rejected | Every failure on this path is silent today; a rejected upload should not be indistinguishable from no upload. | Plan |
| Declared content type | Ignored for storage; contradicted *specific* claim rejected | Generic/absent declarations must keep working — that is the behaviour that produced the four legacy `.bin` files, the only client behaviour here with production evidence. | Plan |
| Containment refusal | Helper throws; callers fall back to existing sentinels | Puts the invariant in the one gateway without moving any caller's observable contract or risking a 500. | Plan |
| Read path | Untouched | Byte-sniffing on read would be a client-visible contract change against a frozen client 1.2.5; allowlisting on read would break FR-007. | Plan |
| Test isolation | Per-class temp dir via `@DynamicPropertySource` | Satisfies the roadmap's "scratch directory, never the production volume" risk note by construction. | Plan |
| Test coverage | Unit table + rollback IT + REST image IT | The containment cases are unreachable through REST, and the rollback is unobservable in a `@Transactional` test — each tier covers what the others cannot. | Plan |

## Scope

**In scope:** post-commit deletion of a replaced image and rollback cleanup of the new one; path
containment across read, write and delete; byte-verified PNG/JPEG allowlist on the write path; a
400 rejection path with its exception and translator handler; the missing test harness; roadmap and
PRD reconciliation.

**Out of scope:** the read path (`VehicleDetailsMapper:34-35`); migrating or renaming the four
legacy `*.bin` files; cleaning up pre-existing orphaned files; adding the missing `@Valid` cascade
to `VehicleDto.vehicleDetails`; measuring whether any `vehicle_details.image` row holds the
empty-string sentinel; replacing the storage backend.

## Architecture / Approach

All three fixes land inside the service layer, in three disjoint methods across two files:
`VehicleServiceImpl.updateVehicle` gains a `TransactionSynchronization` that defers file deletion
past commit; `ImageStorageServiceImpl.prepareImagePath` gains a `startsWith(root)` check with the
normalise/absolutise order corrected; `ImageStorageServiceImpl.save` swaps
`MimeTypes.forName(clientString)` for `Tika#detect(bytes)` against a two-entry allowlist. Rejection
flows through the existing `service/exception` + `ExceptionTranslator` convention. No new
dependency — the pinned `tika-core` 2.7.0 already ships the magic-byte database. No schema change,
no Liquibase changelog, no client-contract movement for reads.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Test harness | Temp-dir isolation, real-image fixtures, first direct storage-service test, first REST image IT | Property override forces a distinct Spring context, adding startup time to the suite |
| 2. S-02 ordering | Old file survives a rolled-back edit; new file not orphaned | The rollback IT needs `NOT_SUPPORTED` propagation and an induced failure — the fiddliest test here |
| 3. S-05 containment | Every resolved path under the data directory, or refused | A too-strict rule could refuse a legitimate name; mitigated by the `sub/../ok.png` acceptance case |
| 4. S-04 allowlist | Only byte-verified PNG/JPEG stored; 400 on a contradicted claim | Breaks `SessionFixtures.imageFor`'s fake bytes; client-1.2.5 upload behaviour beyond the octet-stream case is inferred, not measured |
| 5. Reconciliation | Roadmap statuses, corrected prerequisite rationale, FR-008 premise note | None — documentation only |

**Prerequisites:** Java 17 exactly (`export JAVA_HOME=~/.sdkman/candidates/java/17.0.20-tem`).
For Phase 4's manual verification, a `dev` instance seeded with a copy of the production volume's
nine files. No database access is required — the one question that needs it is explicitly out of
scope.

**Estimated effort:** ~2-3 sessions across five phases; roughly 120 lines of production change and
substantially more test code.

## Open Risks & Assumptions

- **Client 1.2.5's upload content type is only partly known.** The four `.bin` files prove it
  sends `application/octet-stream` at least sometimes; the mismatch rule is built to keep that case
  working, but no direct measurement of the client's upload payload exists.
- **A 400 renders as a silent false success in client 1.2.5** (measured for the 413 in S-03). The
  rejection is correct server-side but the owner will not see an error message. Accepted, carried
  from S-03; the client is frozen.
- **Rollback cleanup of the newly written file is a deliberate addition beyond FR-004**, made
  because the callback is being registered anyway. Self-contained and droppable.
- **The empty-string sentinel remains unmeasured.** Phase 4 makes new occurrences impossible; any
  existing rows keep serving `default.png` exactly as today.
- **`AbstractImageIT` adds a Spring context** to a suite currently at ~45 seconds.

## Success Criteria (Summary)

- An owner whose vehicle update fails still has their image — the file is on disk and loads.
- An image uploaded through the client is stored under a name matching what it actually is, and a
  file that is not a PNG or JPEG never reaches the volume.
- Every image already stored, including the four `*.bin`, loads exactly as before.
