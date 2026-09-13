# VernAI Testing Strategy & Quality Assurance Specification

**Target:** VernAI (Offline-First Multimodal Telugu Civic & Enterprise Assistant)  
**Platform:** Android API 26+ (targetSdk 36), On-Device Indic AI Stack  
**Quality Philosophy:** Deterministic, Network-Isolated, Air-Gapped Verification

---

## 1. Overview & Test Architecture

VernAI operates in a completely air-gapped, zero-cloud environment without `android.permission.INTERNET`. Its quality assurance framework must guarantee that all core capabilities—speech recognition, language model inference, deterministic number parsing, civic letter generation, document parsing, Room persistence, and file export—operate reliably on resource-constrained Android devices without cloud dependencies.

```
                   ▲
                  / \
                 /   \     Android Instrumented Tests
                /     \    - GrantPermissionRule (Microphone)
               / E2E   \   - FileProvider & ContentResolver
              / Device  \  - Target Device Storage & Sandbox
             /-----------\
            /             \   Integration & Component Tests
           /  Integration  \  - ASR Audio Frames & Spectrograms
          /                 \ - Room In-Memory DAOs & Flow
         /-------------------\- Local Document Exporters (PDF, DOCX, XLSX, CSV)
        /                     \
       /      Unit Tests       \  Unit & State Tests
      /                         \ - Prompts, Schemas & JSON Parsers
     /       (Fast, Pure)        \- Deterministic Telugu Arithmetic & Currency
    /                             \- MVI ViewModels & UI States
   /-------------------------------\- Offline Security & SHA-256 Verifiers
```

---

## 2. Test Suites & Taxonomy

### 2.1 Prompts, Schemas, Parsing & Arithmetic
- **Target Components:** `TeluguLetterPromptTemplate`, `IndicPromptTemplate`, `TeluguLetterParser`, `SalesLogParser`, `TeluguSalesArithmetic`.
- **Validation Goals:**
  - Strict JSON schema adherence without Markdown fence leaks.
  - Rejection of malformed JSON with descriptive domain exceptions (`LetterParsingException.MalformedJson`, `LetterParsingException.SchemaValidationFailed`).
  - Strict fact preservation checking (`FactPreservationViolation`).
  - Deterministic arithmetic calculation ($Quantity \times UnitPrice = Total$) with tolerance for floating-point rounding.
  - Recognition of Telugu number words (`ఒకటి`, `రెండు`, `ఐదు`, `పది`, `వంద`, `వెయ్యి`, `అర`, `పావు`, `ముప్పావు`).
  - Ambiguity detection and flagging (`SalesValidationStatus.AMBIGUOUS_QUANTITY`, `AMBIGUOUS_PRICE`).

### 2.2 ViewModel UI State Tests
- **Target Components:** `LetterEditorViewModel`, `SalesViewModel`, `DocReaderViewModel`.
- **Validation Goals:**
  - Clean MVI State transitions upon intent dispatches.
  - State immutability and bidirectional binding.
  - Anti-hallucination workflow: initial draft state (`isDraft = true`, `isUserReviewed = false`), export interruption via review modal, review confirmation, state reset on re-generation.
  - Sales clarification flow: identifying ambiguous rows, user resolution, grand total recalculation.
  - Side effect emissions: toast messages, SAF share intent triggers.

### 2.3 ASR Integration Tests (Local Fixtures)
- **Target Components:** `OnDeviceAsrEngine`, `AudioRecordManager`, `MelSpectrogramExtractor`, `TeluguTextNormalizer`, `TeluguAsrEvaluationPipeline`.
- **Validation Goals:**
  - Spectrogram feature extraction on 16kHz synthetic and realistic speech PCM arrays.
  - Audio chunking, RMS computation, decibel calculation, and voice activity detection (VAD).
  - Graceful handling of silence, extreme decibel spikes, and empty buffers without native crashes.
  - Telugu Unicode normalization (removing extraneous whitespace, standardizing halant/virama).

### 2.4 Deterministic LLM Inference Tests
- **Target Components:** `LlmInferenceEngine`, `MockLlmInferenceEngine`, `LlamaCppInferenceEngine`, `InferenceLock`.
- **Validation Goals:**
  - Deterministic generation for civic complaints, land inquiries, and sales ledger inputs.
  - Streaming generation via `Flow<String>` without token dropping.
  - Mutual exclusion enforcement: sequential execution under `InferenceLock` to prevent dual-model memory crashes.
  - Coroutine cancellation responsiveness: immediate teardown without native thread leaks.

### 2.5 Document Extraction & Export Tests
- **Target Components:** `PdfDocumentExporter`, `DocxDocumentExporter`, `XlsxDocumentExporter`, `SalesLedgerExporter`, `DocumentProcessor`.
- **Validation Goals:**
  - Telugu Unicode glyph rendering (vowel modifiers, conjuncts like `క్ష`, `జ్ఞ`, `శ్రీ`).
  - Empty data handling (empty item lists, null metadata).
  - Large document stress tests (>100 items, multi-page letters).
  - Special characters and XML escaping (`&`, `<`, `>`, `"`, `\n`).
  - Spreadsheet XML formula validation (`SUM(...)`).

### 2.6 Room Database Tests
- **Target Components:** `SalesLogDao`, `ComplaintDao`, `ExplanationDao`, `VernAiDatabase`, `VernAiTypeConverters`.
- **Validation Goals:**
  - CRUD operations on `sales_logs`, `complaints`, and `document_explanations`.
  - Type converter serialization/deserialization for complex nested JSON entities.
  - Flow emission upon database updates.
  - OnConflictStrategy replace behavior and cascading deletes.

### 2.7 Offline Operation & Air-Gap Tests
- **Target Components:** `AndroidManifest.xml`, `ModelIntegrityVerifier`, `ModelLifecycleManager`, `DataPrivacyManager`.
- **Validation Goals:**
  - Complete absence of `android.permission.INTERNET`.
  - SHA-256 checksum verification of model weights.
  - Rejection of tampered, incomplete, or corrupted model binaries.
  - Nuclear data purge verifying zero-trace secure shredding.

### 2.8 Android Instrumented Tests (On-Device)
- **Target Components:** `AudioRecordManager`, `FileProvider`, `AudioPermissionHandler`.
- **Execution Target:** Connected Android Device / Emulator (`emulator-5554`).
- **Validation Goals:**
  - `GrantPermissionRule` granting `RECORD_AUDIO` on live Android runtime.
  - Hardware `AudioRecord.getMinBufferSize` verification without `SecurityException`.
  - `FileProvider.getUriForFile` validation for app-private cache exports.
  - Verification that file read/write permissions are restricted to the application UID.

---

## 3. Execution Commands

### Run All Unit & Integration Tests (Local JVM)
```powershell
.\gradlew.bat testDebugUnitTest
```

### Run Instrumented Tests (Connected Device / Emulator)
```powershell
.\gradlew.bat connectedDebugAndroidTest
```
