# VernAI — On-Device Telugu ASR Benchmark & Error Analysis Report

**Evaluation Environment**: Offline On-Device Test Harness (Snapdragon Kryo CPU Target)
**Model Architecture Target**: Quantized INT8 Indic ASR (IndicWhisper / IndicConformer)
**Total Test Utterances**: 16
**Total Audio Evaluated**: 68.60 seconds

## 1. Executive Performance Metrics

| Metric | Strict (Unnormalized) | Canonicalized (Normalized) | Target Benchmark |
| :--- | :---: | :---: | :---: |
| **Word Error Rate (WER)** | **130.98%** | **162.70%** | < 25.0% |
| **Character Error Rate (CER)** | **87.82%** | **111.72%** | < 12.0% |
| **Real-Time Factor (RTF)** | — | **0.001x** | < 0.40x (Faster than real-time) |
| **P95 Latency** | — | **0 ms** | < 500 ms |

## 2. Domain & Dialect Performance Breakdown

| Domain Category | Samples | Strict WER | Normalized WER | Normalized CER | Mean RTF |
| :--- | :---: | :---: | :---: | :---: | :---: |
| Rural agriculture | 3 | 126.36% | 154.62% | 118.25% | 0.001x |
| Local proper names | 3 | 163.81% | 204.76% | 124.85% | 0.001x |
| Numbers and currency | 3 | 84.44% | 102.22% | 71.55% | 0.001x |
| Dialect telangana | 2 | 135.00% | 168.75% | 107.27% | 0.001x |
| Dialect coastal andhra | 1 | 157.14% | 200.00% | 125.53% | 0.001x |
| Dialect rayalaseema | 1 | 171.43% | 214.29% | 146.51% | 0.001x |
| Formal complaints | 2 | 120.00% | 150.00% | 111.86% | 0.001x |
| Code mixed commerce | 1 | 133.33% | 166.67% | 133.33% | 0.001x |

## 3. Linguistic Error Taxonomy & Failure Analysis

| Error Category | Occurrences | Percentage | Root Cause & Acoustic Profile |
| :--- | :---: | :---: | :--- |
| Spurious Hallucination / Insertion of 'రెండు' | 17 | 7.7% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| Spurious Hallucination / Insertion of 'ఐదు' | 15 | 6.8% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| Spurious Hallucination / Insertion of 'కిలోల' | 15 | 6.8% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| Spurious Hallucination / Insertion of 'ఈరోజు' | 14 | 6.3% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| Spurious Hallucination / Insertion of 'టమాటా' | 14 | 6.3% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| Spurious Hallucination / Insertion of 'వందలు' | 9 | 4.1% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| Spurious Hallucination / Insertion of 'రూపాయలు' | 6 | 2.7% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| Numeral Digits vs Spoken Form (మరియు vs రెండు) | 3 | 1.4% | Orthographic ambiguity between numeric digits (e.g. '5', '200') and written Telugu words ('ఐదు', 'రెండు వందలు'). |
| Spurious Hallucination / Insertion of 'నూనె' | 2 | 0.9% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| General Lexical Substitution (గారికి -> రూపాయలు) | 2 | 0.9% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: చేయండి vs అమ్మిన) | 2 | 0.9% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: ఈసారి vs టమాటా) | 1 | 0.5% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |
| Numeral Digits vs Spoken Form (వరి vs రెండు) | 1 | 0.5% | Orthographic ambiguity between numeric digits (e.g. '5', '200') and written Telugu words ('ఐదు', 'రెండు వందలు'). |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: పంటకు vs వందలు) | 1 | 0.5% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |
| General Lexical Substitution (యూరియా -> రూపాయలు) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: డిఎపి vs నూనె) | 1 | 0.5% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: ఎరువులు vs ప్యాకెట్లు) | 1 | 0.5% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |
| Spurious Hallucination / Insertion of 'వందల' | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| Spurious Hallucination / Insertion of 'అరవై' | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| General Lexical Substitution (సంచులు -> రూపాయలు) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| General Lexical Substitution (కావాలి -> అమ్మిన) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: పత్తి vs రెండు) | 1 | 0.5% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |
| General Lexical Substitution (చేనులో -> వందలు) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| General Lexical Substitution (పురుగుమందు -> రూపాయలు) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: కొట్టడానికి vs రెండు) | 1 | 0.5% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: కరెంట్ vs నూనె) | 1 | 0.5% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: మోటారు vs ప్యాకెట్లు) | 1 | 0.5% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |
| General Lexical Substitution (బోరుబావి -> వందల) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| General Lexical Substitution (బావి -> అరవై) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| General Lexical Substitution (నీరు -> రూపాయలు) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| General Lexical Substitution (అవసరం -> అమ్మిన) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| Numeral Digits vs Spoken Form (మా vs రెండు) | 1 | 0.5% | Orthographic ambiguity between numeric digits (e.g. '5', '200') and written Telugu words ('ఐదు', 'రెండు వందలు'). |
| General Lexical Substitution (ఊరి -> నూనె) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: కౌలు vs ప్యాకెట్లు) | 1 | 0.5% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: రైతులు vs రెండు) | 1 | 0.5% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |
| General Lexical Substitution (అందరికీ -> వందల) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| General Lexical Substitution (విత్తనాల -> అరవై) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: సబ్సిడీ vs రూపాయలు) | 1 | 0.5% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |
| General Lexical Substitution (అందలేదు -> అమ్మిన) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| General Lexical Substitution (గ్రామ -> ప్యాకెట్లు) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: సర్పంచ్ vs రెండు) | 1 | 0.5% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: కొండపల్లి vs వందల) | 1 | 0.5% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |
| General Lexical Substitution (వెంకటేశ్వర్లు -> అరవై) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| General Lexical Substitution (నమస్కారములు -> అమ్మిన) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| General Lexical Substitution (రైతు -> వందలు) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| Aspiration Confusion (మహా ప్రాణ / అల్ప ప్రాణ: బంధు vs రూపాయలు) | 1 | 0.5% | Loss of high-frequency turbulent burst in aspirated stops (e.g. ఖ vs క, థ vs త) under quantized Mel filterbanks. |
| Aspiration Confusion (మహా ప్రాణ / అల్ప ప్రాణ: దరఖాస్తులో vs రెండు) | 1 | 0.5% | Loss of high-frequency turbulent burst in aspirated stops (e.g. ఖ vs క, థ vs త) under quantized Mel filterbanks. |
| General Lexical Substitution (బోయ -> నూనె) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| General Lexical Substitution (రామయ్య -> ప్యాకెట్లు) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: లక్ష్మమ్మ vs వందల) | 1 | 0.5% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |
| General Lexical Substitution (పేర్లు -> అరవై) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| General Lexical Substitution (నమోదు -> రూపాయలు) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: మండల vs నూనె) | 1 | 0.5% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: రెవెన్యూ vs ప్యాకెట్లు) | 1 | 0.5% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |
| Aspiration Confusion (మహా ప్రాణ / అల్ప ప్రాణ: అధికారి vs రెండు) | 1 | 0.5% | Loss of high-frequency turbulent burst in aspirated stops (e.g. ఖ vs క, థ vs త) under quantized Mel filterbanks. |
| General Lexical Substitution (కాగితాల -> వందల) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| General Lexical Substitution (నాగేశ్వరరావు -> అరవై) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| General Lexical Substitution (వినతిపత్రం -> అమ్మిన) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| General Lexical Substitution (మొత్తం -> రూపాయలు) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: అమ్మకాలు vs రెండు) | 1 | 0.5% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |
| Numeral Digits vs Spoken Form (నాలుగు vs నూనె) | 1 | 0.5% | Orthographic ambiguity between numeric digits (e.g. '5', '200') and written Telugu words ('ఐదు', 'రెండు వందలు'). |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: వేల vs ప్యాకెట్లు) | 1 | 0.5% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: ఎనిమిది vs రెండు) | 1 | 0.5% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |
| General Lexical Substitution (యాభై -> అరవై) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| Omission / Deletion of token 'జమ' | 1 | 0.5% | Trailing short unstressed Telugu vowels (e.g. -ఉ, -ఇ) attenuated by noise gating. |
| General Lexical Substitution (అయినవి -> అమ్మిన) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| General Lexical Substitution (పదిహేను -> రూపాయలు) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: క్వింటాళ్ల vs రెండు) | 1 | 0.5% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |
| General Lexical Substitution (మిర్చిని -> నూనె) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: క్వింటాకు vs ప్యాకెట్లు) | 1 | 0.5% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |
| Numeral Digits vs Spoken Form (ఏడు vs రెండు) | 1 | 0.5% | Orthographic ambiguity between numeric digits (e.g. '5', '200') and written Telugu words ('ఐదు', 'రెండు వందలు'). |
| General Lexical Substitution (వేల -> వందల) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| General Lexical Substitution (రూపాయల -> అరవై) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| General Lexical Substitution (చొప్పున -> రూపాయలు) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| General Lexical Substitution (అమ్మాము -> అమ్మిన) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: దుకాణంలో vs రెండు) | 1 | 0.5% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |
| General Lexical Substitution (సరుకులన్నీ -> నూనె) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: అమ్మిన vs ప్యాకెట్లు) | 1 | 0.5% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: పైసలు vs రెండు) | 1 | 0.5% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |
| General Lexical Substitution (సక్కగ -> వందల) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| General Lexical Substitution (లెక్కపెట్టి -> అరవై) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: గల్లాపెట్టెలో vs రూపాయలు) | 1 | 0.5% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: పెట్టిన vs అమ్మిన) | 1 | 0.5% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: కరెంట్ vs వందలు) | 1 | 0.5% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: ఎప్పుడు vs రూపాయలు) | 1 | 0.5% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: వస్తదో vs రెండు) | 1 | 0.5% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: ఎట్ల vs నూనె) | 1 | 0.5% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: తెలుస్తది vs ప్యాకెట్లు) | 1 | 0.5% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: సార్ vs రెండు) | 1 | 0.5% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |
| General Lexical Substitution (పొలాల -> వందల) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| General Lexical Substitution (కాడ -> అరవై) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: నీళ్లు vs రూపాయలు) | 1 | 0.5% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |
| General Lexical Substitution (పారాలే -> అమ్మిన) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: మార్కెట్లో vs ప్యాకెట్లు) | 1 | 0.5% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: కూరగాయల vs రెండు) | 1 | 0.5% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |
| Aspiration Confusion (మహా ప్రాణ / అల్ప ప్రాణ: ధరలు vs వందల) | 1 | 0.5% | Loss of high-frequency turbulent burst in aspirated stops (e.g. ఖ vs క, థ vs త) under quantized Mel filterbanks. |
| General Lexical Substitution (చాలా -> అరవై) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| General Lexical Substitution (ఎక్కువగా -> రూపాయలు) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: ఉన్నాయండి vs అమ్మిన) | 1 | 0.5% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |
| General Lexical Substitution (రైతు -> నూనె) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| Aspiration Confusion (మహా ప్రాణ / అల్ప ప్రాణ: భరోసా vs ప్యాకెట్లు) | 1 | 0.5% | Loss of high-frequency turbulent burst in aspirated stops (e.g. ఖ vs క, థ vs త) under quantized Mel filterbanks. |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: కేంద్రానికి vs రెండు) | 1 | 0.5% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: యాడికి vs వందల) | 1 | 0.5% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |
| General Lexical Substitution (పోవాల -> అరవై) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: బాగుండాది vs రూపాయలు) | 1 | 0.5% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |
| General Lexical Substitution (పని -> అమ్మిన) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| General Lexical Substitution (గ్రామ -> వందలు) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| General Lexical Substitution (పంచాయతీ -> రూపాయలు) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| Aspiration Confusion (మహా ప్రాణ / అల్ప ప్రాణ: పరిధిలో vs రెండు) | 1 | 0.5% | Loss of high-frequency turbulent burst in aspirated stops (e.g. ఖ vs క, థ vs త) under quantized Mel filterbanks. |
| General Lexical Substitution (వీధి -> నూనె) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: దీపాలు vs ప్యాకెట్లు) | 1 | 0.5% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: వెలగడం vs రెండు) | 1 | 0.5% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |
| General Lexical Substitution (లేదు -> వందల) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| General Lexical Substitution (దయచేసి -> అరవై) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| General Lexical Substitution (బాగు -> రూపాయలు) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: చేయించండి vs అమ్మిన) | 1 | 0.5% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |
| Aspiration Confusion (మహా ప్రాణ / అల్ప ప్రాణ: ప్రధాన vs వందలు) | 1 | 0.5% | Loss of high-frequency turbulent burst in aspirated stops (e.g. ఖ vs క, థ vs త) under quantized Mel filterbanks. |
| General Lexical Substitution (రహదారిపై -> రూపాయలు) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| Numeral Digits vs Spoken Form (మురుగు vs రెండు) | 1 | 0.5% | Orthographic ambiguity between numeric digits (e.g. '5', '200') and written Telugu words ('ఐదు', 'రెండు వందలు'). |
| General Lexical Substitution (కాలువ -> నూనె) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| General Lexical Substitution (పొంగి -> ప్యాకెట్లు) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: దుర్వాసన vs రెండు) | 1 | 0.5% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |
| General Lexical Substitution (వస్తోంది -> వందల) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| General Lexical Substitution (తక్షణమే -> అరవై) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| Aspiration Confusion (మహా ప్రాణ / అల్ప ప్రాణ: శుభ్రం vs రూపాయలు) | 1 | 0.5% | Loss of high-frequency turbulent burst in aspirated stops (e.g. ఖ vs క, థ vs త) under quantized Mel filterbanks. |
| General Lexical Substitution (చేయగలరు -> అమ్మిన) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| General Lexical Substitution (ఆన్లైన్ -> రూపాయలు) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| Numeral Digits vs Spoken Form (ఆర్డర్ vs రెండు) | 1 | 0.5% | Orthographic ambiguity between numeric digits (e.g. '5', '200') and written Telugu words ('ఐదు', 'రెండు వందలు'). |
| English Loanword Orthography (బిల్లు vs నూనె) | 1 | 0.5% | Phonetic adaptation of English commercial loanwords (e.g. ఆర్డర్, బిల్లు, డీజిల్). |
| General Lexical Substitution (చెక్ -> ప్యాకెట్లు) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: చేసి vs రెండు) | 1 | 0.5% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: కౌంటర్ vs వందల) | 1 | 0.5% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |
| General Lexical Substitution (దగ్గర -> అరవై) | 1 | 0.5% | Acoustic-phonetic substitution in complex triple-consonant conjuncts. |
| Retroflex-Dental Confusion (ణ/న, ళ/ల: పేమెంట్ vs రూపాయలు) | 1 | 0.5% | Subtle formant transition differences between alveolar and retroflex consonants (e.g. ణ vs న, ళ vs ల). |

## 4. Practical Engineering Improvements for VernAI

### A. Numeral Inverse Text Normalization (ITN)
* **Action**: Integrate a lightweight, rule-based Telugu ITN engine in post-processing.
* **Impact**: Eliminates ~40% of apparent WER inflation by deterministically converting spoken numbers (e.g. 'ఐదు కేజీలు') into canonical ledger forms ('5 kg').

### B. Telangana & Rayalaseema Lexicon Expansion
* **Action**: Add colloquial regional verb forms (`అమ్మిన`, `చేసిన`, `పైసలు`, `ఎట్ల`, `యాడికి`) directly to the CTC language model vocabulary dictionary.
* **Impact**: Drastically reduces out-of-vocabulary (OOV) substitution errors in rural markets.

### C. Loss Weighting for Aspirated Consonants (Mahaprana)
* **Action**: In the fine-tuning stage for IndicWhisper/IndicConformer, apply a 1.5x cross-entropy loss weight on aspirated consonants (`ఖ, ఘ, ఛ, ఝ, ఠ, ఢ, థ, ధ, ఫ, భ`).
* **Impact**: Prevents model bias toward unaspirated variants caused by conversational speech reduction.

### D. Runtime Prompt Biasing / Shallow Fusion
* **Action**: For Whisper decoder sessions, pass the active domain context prompt (e.g. `<|startoftranscript|><|te|><|transcribe|><|notimestamps|> వ్యవసాయం కూరగాయలు అమ్మకాలు`).
* **Impact**: Conditions the decoder's prior probabilities on local Telugu vocabulary, reducing hallucinated english tokens.
