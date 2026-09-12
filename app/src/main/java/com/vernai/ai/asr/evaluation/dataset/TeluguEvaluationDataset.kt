package com.vernai.ai.asr.evaluation.dataset

/**
 * Domain category for evaluation benchmark samples.
 */
enum class EvaluationDomain {
    RURAL_AGRICULTURE,
    LOCAL_PROPER_NAMES,
    NUMBERS_AND_CURRENCY,
    DIALECT_TELANGANA,
    DIALECT_COASTAL_ANDHRA,
    DIALECT_RAYALASEEMA,
    FORMAL_COMPLAINTS,
    CODE_MIXED_COMMERCE
}

/**
 * Single evaluation utterance metadata and reference text.
 */
data class EvaluationUtterance(
    val id: String,
    val domain: EvaluationDomain,
    val referenceText: String,
    val durationSec: Double,
    val primaryChallenge: String,
    val keyPhonemesOrKeywords: List<String>
)

/**
 * Curated, legally open, representative Telugu speech evaluation test set.
 * Contains 16 carefully engineered representative test utterances covering
 * farming terminology, regional names, numerals, dialect idioms, and formal complaints.
 */
object TeluguEvaluationDataset {

    val utterances = listOf(
        // Category 1: Rural Agricultural & Farming Vocabulary
        EvaluationUtterance(
            id = "TEL_RUR_01",
            domain = EvaluationDomain.RURAL_AGRICULTURE,
            referenceText = "ఈసారి వరి పంటకు యూరియా మరియు డిఎపి ఎరువులు రెండు సంచులు కావాలి",
            durationSec = 4.2,
            primaryChallenge = "Agricultural input names (యూరియా, డిఎపి) and agricultural packaging (సంచులు)",
            keyPhonemesOrKeywords = listOf("వరి", "యూరియా", "డిఎపి", "ఎరువులు", "సంచులు")
        ),
        EvaluationUtterance(
            id = "TEL_RUR_02",
            domain = EvaluationDomain.RURAL_AGRICULTURE,
            referenceText = "పత్తి చేనులో పురుగుమందు కొట్టడానికి కరెంట్ మోటారు మరియు బోరు బావి నీరు అవసరం",
            durationSec = 4.8,
            primaryChallenge = "Aspirated conjuncts (త్థి) and compound rural terms (పురుగుమందు, బోరు బావి)",
            keyPhonemesOrKeywords = listOf("పత్తి", "పురుగుమందు", "మోటారు", "బోరు బావి")
        ),
        EvaluationUtterance(
            id = "TEL_RUR_03",
            domain = EvaluationDomain.RURAL_AGRICULTURE,
            referenceText = "మా ఊరి కౌలు రైతులు అందరికీ విత్తనాల సబ్సిడీ అందలేదు",
            durationSec = 3.6,
            primaryChallenge = "Agrarian tenure terms (కౌలు రైతులు) and subsidy administrative terminology",
            keyPhonemesOrKeywords = listOf("కౌలు", "రైతులు", "విత్తనాల", "సబ్సిడీ")
        ),

        // Category 2: Local Telugu Proper Names with Complex Graphemes
        EvaluationUtterance(
            id = "TEL_NAM_01",
            domain = EvaluationDomain.LOCAL_PROPER_NAMES,
            referenceText = "గ్రామ సర్పంచ్ కొండపల్లి వెంకటేశ్వర్లు గారికి నమస్కారములు",
            durationSec = 4.0,
            primaryChallenge = "Triple consonant conjuncts (ర్ప్, ల్లి, శ్వ, ర్లు) in Andhra/Telangana names",
            keyPhonemesOrKeywords = listOf("సర్పంచ్", "కొండపల్లి", "వెంకటేశ్వర్లు")
        ),
        EvaluationUtterance(
            id = "TEL_NAM_02",
            domain = EvaluationDomain.LOCAL_PROPER_NAMES,
            referenceText = "రైతు బంధు దరఖాస్తులో బోయ రామయ్య మరియు లక్ష్మమ్మ పేర్లు నమోదు చేయండి",
            durationSec = 4.5,
            primaryChallenge = "Conjuncts (క్ష్మ, మ్మ) and scheme title (రైతు బంధు)",
            keyPhonemesOrKeywords = listOf("రైతు బంధు", "రామయ్య", "లక్ష్మమ్మ")
        ),
        EvaluationUtterance(
            id = "TEL_NAM_03",
            domain = EvaluationDomain.LOCAL_PROPER_NAMES,
            referenceText = "మండల రెవెన్యూ అధికారి కాగితాల నాగేశ్వరరావు గారికి వినతిపత్రం",
            durationSec = 4.1,
            primaryChallenge = "Honorific suffix (రావు గారికి) and administrative designation (రెవెన్యూ అధికారి)",
            keyPhonemesOrKeywords = listOf("మండల", "రెవెన్యూ", "నాగేశ్వరరావు", "వినతిపత్రం")
        ),

        // Category 3: Numbers, Currencies & Quantities
        EvaluationUtterance(
            id = "TEL_NUM_01",
            domain = EvaluationDomain.NUMBERS_AND_CURRENCY,
            referenceText = "ఈరోజు 5 కేజీల టమాటా 200 రూపాయలు, 2 నూనె ప్యాకెట్లు 260 రూపాయలు అమ్మిన",
            durationSec = 4.9,
            primaryChallenge = "Rapid spoken numerals with metric units (5 కేజీల, 200 రూపాయలు, 260 రూపాయలు)",
            keyPhonemesOrKeywords = listOf("5 కేజీల", "టమాటా", "200 రూపాయలు", "260 రూపాయలు", "అమ్మిన")
        ),
        EvaluationUtterance(
            id = "TEL_NUM_02",
            domain = EvaluationDomain.NUMBERS_AND_CURRENCY,
            referenceText = "మొత్తం అమ్మకాలు నాలుగు వేల ఎనిమిది వందల యాభై రూపాయలు జమ అయినవి",
            durationSec = 4.4,
            primaryChallenge = "Compound thousand-hundred numeral articulation (నాలుగు వేల ఎనిమిది వందల యాభై)",
            keyPhonemesOrKeywords = listOf("నాలుగు వేల", "ఎనిమిది వందల", "యాభై", "జమ")
        ),
        EvaluationUtterance(
            id = "TEL_NUM_03",
            domain = EvaluationDomain.NUMBERS_AND_CURRENCY,
            referenceText = "పదిహేను క్వింటాళ్ల మిర్చిని క్వింటాకు ఏడు వేల రూపాయల చొప్పున అమ్మాము",
            durationSec = 4.3,
            primaryChallenge = "Commercial weight units (క్వింటాళ్ల) and multiplication unit pricing (చొప్పున)",
            keyPhonemesOrKeywords = listOf("పదిహేను", "క్వింటాళ్ల", "మిర్చి", "ఏడు వేల")
        ),

        // Category 4: Regional Dialects (Telangana, Coastal Andhra, Rayalaseema)
        EvaluationUtterance(
            id = "TEL_DLT_TS_01",
            domain = EvaluationDomain.DIALECT_TELANGANA,
            referenceText = "దుకాణంలో సరుకులన్నీ అమ్మిన పైసలు సక్కగ లెక్కపెట్టి గల్లాపెట్టెలో పెట్టిన",
            durationSec = 4.7,
            primaryChallenge = "Telangana colloquial lexicon (పైసలు, సక్కగ, గల్లాపెట్టె, పెట్టిన)",
            keyPhonemesOrKeywords = listOf("పైసలు", "సక్కగ", "గల్లాపెట్టెలో", "పెట్టిన")
        ),
        EvaluationUtterance(
            id = "TEL_DLT_TS_02",
            domain = EvaluationDomain.DIALECT_TELANGANA,
            referenceText = "కరెంట్ ఎప్పుడు వస్తదో ఎట్ల తెలుస్తది సార్ పొలాల కాడ నీళ్లు పారాలే",
            durationSec = 4.2,
            primaryChallenge = "Telangana dialect verb endings (వస్తదో, తెలుస్తది, పారాలే) and connective (ఎట్ల)",
            keyPhonemesOrKeywords = listOf("ఎట్ల", "తెలుస్తది", "పొలాల కాడ", "పారాలే")
        ),
        EvaluationUtterance(
            id = "TEL_DLT_AP_01",
            domain = EvaluationDomain.DIALECT_COASTAL_ANDHRA,
            referenceText = "ఈరోజు మార్కెట్లో కూరగాయల ధరలు చాలా ఎక్కువగా ఉన్నాయండి",
            durationSec = 3.8,
            primaryChallenge = "Coastal Andhra polite suffix (ఉన్నాయండి) and soft retroflex sounds",
            keyPhonemesOrKeywords = listOf("మార్కెట్లో", "కూరగాయల", "ఎక్కువగా", "ఉన్నాయండి")
        ),
        EvaluationUtterance(
            id = "TEL_DLT_RS_01",
            domain = EvaluationDomain.DIALECT_RAYALASEEMA,
            referenceText = "రైతు భరోసా కేంద్రానికి యాడికి పోవాల బాగుండాది పని",
            durationSec = 3.5,
            primaryChallenge = "Rayalaseema interrogative direction (యాడికి) and modal inflection (పోవాల)",
            keyPhonemesOrKeywords = listOf("రైతు భరోసా", "యాడికి", "పోవాల", "బాగుండాది")
        ),

        // Category 5: Formal Public Grievance / Administrative Petitions
        EvaluationUtterance(
            id = "TEL_CMP_01",
            domain = EvaluationDomain.FORMAL_COMPLAINTS,
            referenceText = "గ్రామ పంచాయతీ పరిధిలో వీధి దీపాలు వెలగడం లేదు దయచేసి బాగు చేయించండి",
            durationSec = 4.6,
            primaryChallenge = "Administrative civic terms (గ్రామ పంచాయతీ, వీధి దీపాలు) and polite plea syntax",
            keyPhonemesOrKeywords = listOf("గ్రామ పంచాయతీ", "వీధి దీపాలు", "బాగు చేయించండి")
        ),
        EvaluationUtterance(
            id = "TEL_CMP_02",
            domain = EvaluationDomain.FORMAL_COMPLAINTS,
            referenceText = "ప్రధాన రహదారిపై మురుగు కాలువ పొంగి దుర్వాసన వస్తోంది తక్షణమే శుభ్రం చేయగలరు",
            durationSec = 5.1,
            primaryChallenge = "Sanskritized formal grievance vocabulary (ప్రధాన రహదారి, దుర్వాసన, తక్షణమే)",
            keyPhonemesOrKeywords = listOf("ప్రధాన రహదారి", "మురుగు కాలువ", "దుర్వాసన", "తక్షణమే")
        ),

        // Category 6: Code-Mixed Telugu + English Loanwords
        EvaluationUtterance(
            id = "TEL_MIX_01",
            domain = EvaluationDomain.CODE_MIXED_COMMERCE,
            referenceText = "ఆన్‌లైన్ ఆర్డర్ బిల్లు చెక్ చేసి కౌంటర్ దగ్గర పేమెంట్ చేయండి",
            durationSec = 3.9,
            primaryChallenge = "Phonetically borrowed English words (ఆర్డర్, బిల్లు, కౌంటర్, పేమెంట్)",
            keyPhonemesOrKeywords = listOf("ఆర్డర్", "బిల్లు", "కౌంటర్", "పేమెంట్")
        )
    )
}
