# VernAI: Offline-First Pan-Indian Voice Productivity Assistant

VernAI is an offline-first Android productivity assistant engineered for Snapdragon-powered devices (specifically optimized for iQOO Snapdragon 7+ and 8-series platforms). It runs localized ASR, LLM reasoning, OCR, and document synthesis 100% on-device with zero network requests after installation.

## Target Languages (Indic Multilingual)
- **Dravidian**: Telugu (`te`), Tamil (`ta`), Kannada (`kn`), Malayalam (`ml`)
- **Indo-Aryan**: Hindi (`hi`), Marathi (`mr`), Bengali (`bn`), Gujarati (`gu`)
- **Bridge**: Indian English (`en-IN`) / Code-mixed Vernacular (Hinglish, Tenglish, Tanglish)

## Core Capabilities
1. **Multilingual Voice Input & Live ASR**: Sub-300ms latency streaming transcription using Sherpa-ONNX unified IndicConformer.
2. **Local LLM Formal Complaint Generation**: Dual-language administrative complaint letters (Vernacular + English translation) using GGUF quantized models via `llama.cpp`.
3. **Document Simplification & OCR**: Local document parsing (digital PDF via PDFBox, scans via Tesseract 5) and regional language summarization.
4. **Structured Sales-Log Extraction**: Spoken daily transaction extraction into typed, validated JSON using GBNF grammars and local Room database persistence.
5. **Complex Text Export (PDF/DOCX)**: Native HarfBuzz-backed Brahmic conjunct glyph layout rendering for zero-corruption regional printouts.
6. **Zero Network Dependency**: Strict offline sandbox operation post model provisioning.

## Architecture
Modular Clean Architecture with Model-View-Intent (MVI) and unidirectional data flow in Kotlin and Jetpack Compose.
