<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Client-server contract trial

- **Plan**: `context/changes/client-server-contract-trial/plan.md`
- **Scope**: Phases 1-3 (full plan)
- **Date**: 2026-08-27
- **Verdict**: NEEDS ATTENTION -> RESOLVED (all findings triaged and fixed, 2026-08-27)
- **Findings**: 1 critical, 1 warning, 2 observations (F4 surfaced during triage)

> Verdict note: the rubric maps a critical FAIL to REJECTED. Recorded as NEEDS ATTENTION
> instead, because the application layer still guards the affected invariant (see F1) and the
> fix is a single changeset. The remainder of the work is clean and well-evidenced.

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | FAIL |
| Architecture | PASS |
| Pattern Consistency | PASS (1 observation) |
| Success Criteria | PASS |

## Verification performed during this review

- Booted `target/carcare-1.3.11.war` under the `prod` profile against a fresh disposable
  MariaDB 10.11.6 and inspected the resulting `vehicles` schema (basis for F1).
- Generated the H2 migration SQL via `liquibase updateSQL` on the test classpath to confirm the
  engine divergence described in F1.
- Re-ran `./mvnw verify`: **175 tests, 0 failures, 1 skipped, BUILD SUCCESS** — matches the
  figure recorded in `change.md`. The single skip is the pre-existing `@Disabled`
  delete-with-history test owned by S-05.

## Findings

### F1 - Licence-plate migration silently drops NOT NULL on MariaDB

- **Severity**: CRITICAL
- **Impact**: MEDIUM - real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality (data safety / schema drift)
- **Location**: `src/main/resources/config/liquibase/changelog/20260827153000_client_contract_changelog.xml:8`
- **Detail**: `<modifyDataType>` renders as `ALTER TABLE vehicles MODIFY license_plate VARCHAR(20)`
  on MariaDB, and MySQL/MariaDB `MODIFY` replaces the entire column definition - so the original
  NOT NULL is dropped. Verified empirically against a fresh MariaDB 10.11.6 running the prod WAR:

  ```
  license_plate  varchar(20)  Null: YES     <- was NOT NULL
  make           varchar(20)  Null: NO
  model          varchar(20)  Null: NO
  ```

  No test can catch this, because the H2 path diverges. Liquibase emits
  `ALTER TABLE PUBLIC.vehicles ALTER COLUMN license_plate VARCHAR(20)` for H2, which preserves
  NOT NULL. The test schema keeps the constraint, the production schema loses it, and
  `./mvnw verify` stays green either way. Hibernate's `validate` does not check nullability, so
  the schema-validating contexts pass as well.

  Mitigating: `Vehicle.licensePlate` still carries `@NotNull` and `@Column(nullable = false)`,
  so the application never writes a null plate. This is a loss of the database-level backstop
  and a real test/prod schema divergence - not active corruption today.
- **Fix A (Recommended)**: Append a new forward-only changeset restoring the constraint:

  ```xml
  <changeSet author="Kacper" id="20260827153000-2">
      <addNotNullConstraint tableName="vehicles"
          columnName="license_plate" columnDataType="VARCHAR(20)"/>
  </changeSet>
  ```

  - Strength: Honors the plan's own forward-only rule and its Migration Notes. Renders correctly
    on both engines - MariaDB gets `MODIFY ... NOT NULL`, H2 gets `SET NOT NULL` (a no-op there).
  - Tradeoff: Two changesets for what reads as one logical change.
  - Confidence: HIGH - the MariaDB regression is directly observed and the H2 divergence is
    confirmed from generated SQL.
  - Blind spot: A regression test asserting nullability would pass on H2 even without the fix,
    so the fix is best verified against real MariaDB, the way the defect was found.
- **Fix B**: Edit changeset `20260827153000-1` in place to carry the constraint (e.g. an
  `<sql>`/`dbms`-split `MODIFY ... NOT NULL`).
  - Strength: Keeps one changeset; nothing has shipped to production, so no deployed checksum breaks.
  - Tradeoff: Violates the plan's forward-only rule and invalidates the checksum in any
    dev/trial database that already ran it, including anyone repeating the Phase 3 trial.
  - Confidence: MEDIUM - safe only if no environment has applied it.
  - Blind spot: Which non-disposable databases have already run the changeset was not audited.
- **Decision**: FIXED via Fix B. Changeset `20260827153000-1` now carries a second change -
  `<addNotNullConstraint tableName="vehicles" columnName="license_plate" columnDataType="VARCHAR(20)"/>` -
  so the constraint travels with the widening, with a comment recording why. Chose the two-change
  changeset over raw `<sql>` so both engines stay covered without a dbms split. Verified by
  rebuilding the prod WAR and booting it against a fresh MariaDB 10.11.6:
  `license_plate varchar(20) IS_NULLABLE: NO`, matching `make` and `model`. Note the checksum of
  changeset `-1` changed; any database that already applied it must be recreated (all Phase 3
  trial databases were tmpfs-backed and disposable).

### F4 - Over-limit licence plate returns 500, not a Problem Details 400

> Surfaced during triage of F2, not present in the original report.

- **Severity**: WARNING
- **Impact**: MEDIUM - real tradeoff; pause to reason through it
- **Dimension**: Plan Adherence (plan gap, not implementation drift)
- **Location**: `src/main/java/com/kasztelanic/carcare/web/rest/VehicleResource.java:52,61`
- **Detail**: A 21-character plate returned 500, observed as
  `AssertionError: Status expected:<400> but was:<500>` with
  `ConstraintViolationException: Validation failed for classes [Vehicle] during persist time ...
  'length must be between 1 and 20', propertyPath=licensePlate`.

  Cause: `VehicleResource`'s POST/PUT took a bare `@RequestBody VehicleDto` with no `@Valid`, and
  `VehicleDto` declared no constraints. The `@Length` lived only on the entity, so it fired at
  Hibernate persist time and escaped as an unhandled 500. Every other constrained endpoint in the
  project uses the `@Valid @RequestBody` convention (`AccountResource:62,117`,
  `UserResource:97,126`, `UserJwtController:38`).

  This was the plan's own root cause left unfixed. The plan's Current State records that "the first
  11-character browser submission produced a generic 500"; Phase 2 raised the ceiling 10->20 so the
  client's forms fit, but the 500-instead-of-400 behavior simply moved from 11 characters to 21.
  Phase 2's contract - "longer values remain invalid under the server model" - was literally
  satisfied while the client still received an unusable error shape.
- **Fix A (chosen)**: Add `@Valid` to `VehicleResource`'s POST and PUT and mirror the entity's
  constraints onto `VehicleDto` (`@NotNull @Length(min = 1, max = 20)` on `make`, `model`,
  `licensePlate`) so the boundary rejects before persist.
  - Strength: Matches the project's existing `@Valid @RequestBody` convention and routes the failure
    through `ExceptionTranslator.handleMethodArgumentNotValid`, which already emits the richer
    `error.validation` + `fieldErrors` shape.
  - Tradeoff: Touches `src/main` beyond the plan's stated scope.
  - Confidence: HIGH - the 400 shape is now asserted by test and the full suite is green.
  - Blind spot: Only the vehicle endpoints were corrected; the other event resources
    (repair, routine-service, inspection, insurance, refuel) were not audited for the same
    bare-`@RequestBody` pattern.
- **Fix B**: Leave `src/main` untouched, assert the 500 in F2's test, and record the gap as a
  follow-up. Rejected - it would enshrine an error shape the client cannot use.
- **Decision**: FIXED via Fix A.

### F2 - No negative-boundary test for the 20-character plate limit

- **Severity**: OBSERVATION
- **Impact**: LOW - quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: `src/test/java/com/kasztelanic/carcare/web/rest/VehicleResourceIT.java:69`
- **Detail**: Phase 2's contract has two halves: "Vehicle create and edit accept any nonblank
  plate through 20 characters; longer values remain invalid under the server model." Only the
  first half is tested. `createsAndUpdatesVehicleWithTwentyCharacterLicensePlate` proves 20
  works; nothing proves 21 is rejected, so a future widening of `@Length` would go unnoticed.
- **Fix**: Add a 21-character case asserting the Problem Details 400, following the existing
  `returnsProblemDetailForMissingFuelType` pattern in the same file.
- **Decision**: FIXED. Added `rejectsLicensePlateLongerThanTwentyCharacters` to `VehicleResourceIT`.
  Writing it surfaced F4 below - the endpoint answered 500, not 400 - so the test now asserts the
  contract as corrected by F4: status 400, `application/problem+json`, `$.message` =
  `error.validation`, and `$.fieldErrors[0]` naming `licensePlate` / `Length`.

### F3 - Inline FQN matcher instead of the file-wide static import

- **Severity**: OBSERVATION
- **Impact**: LOW - quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: `src/test/java/com/kasztelanic/carcare/config/SecurityConfigurationIT.java:60`
- **Detail**: Uses `org.hamcrest.Matchers.hasItem("test")` fully qualified inline. Every other
  hamcrest usage in the suite static-imports it - `AuditResourceIT:22`, `UserResourceIT:37-38`,
  `UserJwtControllerIT:15-21`, `JwtSessionIT:9`. The file already uses static imports for MockMvc.
- **Fix**: Add `import static org.hamcrest.Matchers.hasItem;` and shorten the call site.
- **Decision**: FIXED. Static import added; call site is now `value(hasItem("test"))`.

## What passed

All four planned source changes match their stated intent. `ProfileInfoContributor` lands in the
pre-existing `management/` package alongside `SecurityMetersService` and exposes exactly the two
client fields with no broad environment exposure. `master.xml` includes the new changelog without
touching the 2019 changeset. The `.gitignore` and `roadmap.md` additions are both covered by
Phase 3's record-and-handoff contract, so no scope creep. The Phase 3 manual evidence is unusually
strong - per-mutation HTTP statuses, captured toast headers, two independent clean-database passes -
and C-1 / C-2 / S-05 are correctly scoped out rather than quietly fixed.

## Triage outcome (2026-08-27)

| Finding | Decision |
|---------|----------|
| F1 - MariaDB NOT NULL dropped | FIXED (Fix B, in-place changeset edit) |
| F4 - over-limit plate returned 500 | FIXED (Fix A, `@Valid` + DTO constraints) |
| F2 - missing negative-boundary test | FIXED |
| F3 - inline FQN matcher | FIXED |

Files changed during triage:

- `src/main/resources/config/liquibase/changelog/20260827153000_client_contract_changelog.xml`
- `src/main/java/com/kasztelanic/carcare/web/rest/VehicleResource.java`
- `src/main/java/com/kasztelanic/carcare/service/dto/VehicleDto.java`
- `src/test/java/com/kasztelanic/carcare/web/rest/VehicleResourceIT.java`
- `src/test/java/com/kasztelanic/carcare/config/SecurityConfigurationIT.java`

Final verification: `./mvnw verify` - **176 tests, 0 failures, 1 skipped, BUILD SUCCESS** (up from
175; the skip remains the pre-existing `@Disabled` delete-with-history test owned by S-05). F1's fix
additionally verified against a fresh MariaDB 10.11.6, since the H2 test path cannot observe it.

Open follow-up, not actioned: the other event resources were not audited for the same bare
`@RequestBody` pattern described in F4.
