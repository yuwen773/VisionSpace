# Thumbimage Preview Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add performance-oriented thumbnail and preview image support by keeping upload fast, using `thumbnailUrl` in lists, and generating `previewUrl` asynchronously for detail pages.

**Architecture:** Reuse `x-file-storage` for original upload and thumbnail generation, persist a new `previewUrl` field, and add an async preview generation flow that downloads the uploaded original by storage key, encodes a WebP preview, uploads it back, and updates the picture record. Frontend consumes `thumbnailUrl || url` on list pages and `previewUrl || url` on the detail page.

**Tech Stack:** Spring Boot 2.7, MyBatis Plus, x-file-storage, Java ImageIO + WebP plugin, Vue 3, TypeScript

---

### Task 1: Add Data Model Support For Preview URLs

**Files:**
- Modify: `sql/create_table.sql`
- Modify: `src/main/java/com/yuwen/visionspace/model/entity/Picture.java`
- Modify: `src/main/java/com/yuwen/visionspace/model/vo/PictureVO.java`
- Modify: `src/main/resources/mapper/PictureMapper.xml`
- Modify: `frontend/src/api/typings.d.ts`

- [ ] **Step 1: Write the failing backend test**

Create a unit test that converts a `Picture` with `previewUrl` into `PictureVO` and asserts the field is preserved.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=PictureVOTest test`
Expected: FAIL because `previewUrl` does not exist on the entity / VO mapping yet.

- [ ] **Step 3: Write minimal implementation**

Add `previewUrl` to the entity, VO, mapper result map / column list, schema SQL, and frontend generated typings.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=PictureVOTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add sql/create_table.sql src/main/java/com/yuwen/visionspace/model/entity/Picture.java src/main/java/com/yuwen/visionspace/model/vo/PictureVO.java src/main/resources/mapper/PictureMapper.xml frontend/src/api/typings.d.ts src/test/java/com/yuwen/visionspace/model/vo/PictureVOTest.java
git commit -m "feat: add preview url model support"
```

### Task 2: Add Upload Result Storage Key And Preview Path Helpers

**Files:**
- Modify: `src/main/java/com/yuwen/visionspace/model/dto/file/UploadPictureResult.java`
- Modify: `src/main/java/com/yuwen/visionspace/manager/upload/PictureUploadTemplate.java`
- Create: `src/main/java/com/yuwen/visionspace/utils/PictureVariantPathUtils.java`
- Test: `src/test/java/com/yuwen/visionspace/utils/PictureVariantPathUtilsTest.java`

- [ ] **Step 1: Write the failing test**

Add tests for preview path generation from an original storage key, such as converting `/public/1/2026-03-27_xxx.jpg` into `/public/1/2026-03-27_xxx_preview.webp`.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=PictureVariantPathUtilsTest test`
Expected: FAIL because helper does not exist.

- [ ] **Step 3: Write minimal implementation**

Create a focused helper for deriving preview object keys and expose the original storage key from `UploadPictureResult`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=PictureVariantPathUtilsTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/yuwen/visionspace/model/dto/file/UploadPictureResult.java src/main/java/com/yuwen/visionspace/manager/upload/PictureUploadTemplate.java src/main/java/com/yuwen/visionspace/utils/PictureVariantPathUtils.java src/test/java/com/yuwen/visionspace/utils/PictureVariantPathUtilsTest.java
git commit -m "feat: add preview storage path helpers"
```

### Task 3: Add WebP Preview Generator And Storage Upload Flow

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/java/com/yuwen/visionspace/manager/storage/PictureStorageService.java`
- Create: `src/main/java/com/yuwen/visionspace/service/PicturePreviewService.java`
- Create: `src/main/java/com/yuwen/visionspace/service/impl/PicturePreviewServiceImpl.java`
- Create: `src/main/java/com/yuwen/visionspace/utils/PicturePreviewUtils.java`
- Test: `src/test/java/com/yuwen/visionspace/utils/PicturePreviewUtilsTest.java`

- [ ] **Step 1: Write the failing test**

Add a focused unit test for preview image sizing logic: given a large width/height, it should return bounded dimensions while preserving aspect ratio.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=PicturePreviewUtilsTest test`
Expected: FAIL because utility does not exist.

- [ ] **Step 3: Write minimal implementation**

Add the WebP dependency, implement preview resize calculations, add stream upload support if needed, and create a preview service that can download the original, generate a temporary `.webp`, upload it, and return the preview URL.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=PicturePreviewUtilsTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add pom.xml src/main/java/com/yuwen/visionspace/manager/storage/PictureStorageService.java src/main/java/com/yuwen/visionspace/service/PicturePreviewService.java src/main/java/com/yuwen/visionspace/service/impl/PicturePreviewServiceImpl.java src/main/java/com/yuwen/visionspace/utils/PicturePreviewUtils.java src/test/java/com/yuwen/visionspace/utils/PicturePreviewUtilsTest.java
git commit -m "feat: add async preview generation service"
```

### Task 4: Trigger Async Preview Generation During Picture Upload

**Files:**
- Modify: `src/main/java/com/yuwen/visionspace/service/impl/PictureServiceImpl.java`
- Test: `src/test/java/com/yuwen/visionspace/service/impl/PictureServiceImplTest.java`

- [ ] **Step 1: Write the failing test**

Add a Mockito-based service test that verifies `uploadPicture(...)` saves a picture with `previewUrl = null` and schedules preview generation using the uploaded storage key.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=PictureServiceImplTest test`
Expected: FAIL because preview scheduling is not wired yet.

- [ ] **Step 3: Write minimal implementation**

Inject the preview service, set `previewUrl` from upload result (initially null), schedule async generation after successful save/update, and persist the returned preview URL when generation completes.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=PictureServiceImplTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/yuwen/visionspace/service/impl/PictureServiceImpl.java src/test/java/com/yuwen/visionspace/service/impl/PictureServiceImplTest.java
git commit -m "feat: schedule preview generation after picture upload"
```

### Task 5: Clean Up Preview Objects On Delete

**Files:**
- Modify: `src/main/java/com/yuwen/visionspace/service/impl/PictureServiceImpl.java`
- Test: `src/test/java/com/yuwen/visionspace/service/impl/PictureServiceImplTest.java`

- [ ] **Step 1: Write the failing test**

Extend delete-path tests to assert preview objects are deleted alongside original and thumbnail objects when the picture is uniquely referenced.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=PictureServiceImplTest test`
Expected: FAIL because preview cleanup is not implemented.

- [ ] **Step 3: Write minimal implementation**

Delete `previewUrl` when clearing picture files, without touching unrelated existing delete logic.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=PictureServiceImplTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/yuwen/visionspace/service/impl/PictureServiceImpl.java src/test/java/com/yuwen/visionspace/service/impl/PictureServiceImplTest.java
git commit -m "feat: clean up preview objects on picture delete"
```

### Task 6: Switch Frontend Consumers To Thumbnail And Preview Fallbacks

**Files:**
- Modify: `frontend/src/components/PictureList.vue`
- Modify: `frontend/src/pages/PictureDetailPage.vue`

- [ ] **Step 1: Write the failing test**

No frontend test harness is currently configured. Instead, document the intended display expressions before changing code:

```ts
const listImageUrl = picture.thumbnailUrl || picture.url
const detailImageUrl = picture.previewUrl || picture.url
```

- [ ] **Step 2: Verify current behavior is missing**

Inspect current code and confirm list/detail pages still bind directly to `picture.url`.

- [ ] **Step 3: Write minimal implementation**

Update the list page to use thumbnail fallback and the detail page to use preview fallback while keeping downloads pointed at the original.

- [ ] **Step 4: Run targeted verification**

Run: `npm run type-check`
Expected: PASS if dependencies are installed; otherwise record existing frontend environment blockers.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/PictureList.vue frontend/src/pages/PictureDetailPage.vue
git commit -m "feat: use thumbnail and preview fallbacks in frontend"
```

### Task 7: Final Verification And Documentation Sync

**Files:**
- Modify: `docs/feature/thumbimage.md` if implementation details diverged

- [ ] **Step 1: Run targeted backend tests**

Run: `mvn -Dtest=PictureVOTest,PictureVariantPathUtilsTest,PicturePreviewUtilsTest,PictureServiceImplTest test`
Expected: PASS

- [ ] **Step 2: Run compile verification**

Run: `mvn -DskipTests compile`
Expected: PASS

- [ ] **Step 3: Run frontend verification where possible**

Run: `npm run type-check`
Expected: PASS if dependency installation is fixed; otherwise document existing blocker.

- [ ] **Step 4: Sync docs**

Update the feature doc only if implementation details changed from the agreed design.

- [ ] **Step 5: Commit**

```bash
git add docs/feature/thumbimage.md
git commit -m "docs: sync thumbimage implementation notes"
```
