package com.vernai.document.processing

import com.vernai.core.model.Language

enum class TestDocumentType(
    val titleTelugu: String,
    val titleEnglish: String,
    val fileName: String,
    val mimeType: String,
    val isScanned: Boolean
) {
    SCHOLARSHIP_APPLICATION_FORM(
        titleTelugu = "స్కాలర్‌షిప్ దరఖాస్తు మార్గదర్శకాలు (Scholarship Guidelines)",
        titleEnglish = "Post-Matric Merit-cum-Means Scholarship Guidelines & Form",
        fileName = "PostMatric_Scholarship_Form_2026.pdf",
        mimeType = "application/pdf",
        isScanned = false
    ),
    REVENUE_PATTADAR_NOTICE(
        titleTelugu = "రెవెన్యూ పట్టా నోటీసు (Land Deed)",
        titleEnglish = "Revenue Pattadar Passbook Notice",
        fileName = "Revenue_Notice_142A.pdf",
        mimeType = "application/pdf",
        isScanned = false
    ),
    ELECTRICITY_DISCONNECTION_NOTICE(
        titleTelugu = "విద్యుత్ బిల్లు హెచ్చరిక (TSSPDCL Notice)",
        titleEnglish = "Electricity Disconnection Notice",
        fileName = "TSSPDCL_Notice_44091.pdf",
        mimeType = "application/pdf",
        isScanned = false
    ),
    RATION_CARD_KYC_NOTICE(
        titleTelugu = "రేషన్ కార్డు e-KYC నోటీసు (Civil Supplies)",
        titleEnglish = "Ration Card e-KYC Notice",
        fileName = "CivilSupplies_eKYC_Notice.pdf",
        mimeType = "application/pdf",
        isScanned = false
    ),
    SCANNED_PHOTOSTAT_DOCUMENT(
        titleTelugu = "స్కాన్ చేసిన జెరాక్స్ పత్రం (Scanned Photostat)",
        titleEnglish = "Scanned Low-DPI Photostat Notice",
        fileName = "Scanned_Photostat_Notice.jpg",
        mimeType = "image/jpeg",
        isScanned = true
    ),
    CORRUPTED_MALFORMED_FILE(
        titleTelugu = "పాడైన ఫైలు పరీక్ష (Corrupted Document Test)",
        titleEnglish = "Corrupted / Unreadable File",
        fileName = "Corrupted_File_Test.pdf",
        mimeType = "application/pdf",
        isScanned = false
    )
}

object TestDocuments {

    fun getSampleDocumentText(type: TestDocumentType): String {
        return when (type) {
            TestDocumentType.SCHOLARSHIP_APPLICATION_FORM -> """
                POST-MATRIC MERIT-CUM-MEANS SCHOLARSHIP SCHEME (HIGHER EDUCATION)
                GOVERNMENT OF INDIA & STATE SCHOLARSHIP PORTAL
                SCHOLARSHIP NOTIFICATION & APPLICATION GUIDELINES FOR ACADEMIC YEAR 2026-2027
                
                Ref No: SCH/MAHA-PUNE/2026/8842-B
                Application Deadline: 31st October 2026
                
                1. ELIGIBILITY CRITERIA:
                a) The applicant must be a bona fide resident of the state and enrolled in a recognized undergraduate college/university.
                b) Total annual parental/family income from all sources must not exceed Rs. 2,50,000/- (Rupees Two Lakh Fifty Thousand only) per annum.
                c) Minimum 60% marks or equivalent CGPA in the preceding qualifying examination (Higher Secondary / 12th Standard).
                
                2. MANDATORY ENCLOSURES & REQUIRED DOCUMENTS:
                1. Domicile / Residence Certificate issued by the competent Tehsildar / Sub-Divisional Magistrate.
                2. Original Income Certificate for Financial Year 2025-26 from competent revenue authority.
                3. Caste / Category Validity Certificate (if applying under reserved category quotas).
                4. Self-attested copy of 10th and 12th Grade Marksheets.
                5. Student Bank Account Passbook front page (Account must be in the student's name, active, and Aadhaar-seeded / NPCI mapped with IFSC code).
                6. College Bonafide Certificate & Current Academic Year Fee Receipt.
                
                3. STEP-BY-STEP APPLICATION PROCEDURE:
                Section A: Personal and Demographic details (Name exactly as per Aadhaar Card).
                Section B: Academic records and current institutional enrollment number.
                Section C: Bank account details (Carefully enter 11-character IFSC and 16-digit Account Number).
                Section D: Upload certified scanned copies of certificates (PDF format under 200 KB).
                
                4. IMPORTANT DATES & FEES:
                - Application portal opens: 1st August 2026.
                - Last Date for online form submission: 31st October 2026.
                - Defective application correction window: Up to 10th November 2026.
                - Application Fee: Rs. 0/- (Application is 100% Free of Cost. Do not pay any agent).
            """.trimIndent()

            TestDocumentType.REVENUE_PATTADAR_NOTICE -> """
                GOVERNMENT OF TELANGANA / ANDHRA PRADESH
                రెవెన్యూ డిపార్ట్‌మెంట్ - తహశీల్దార్ కార్యాలయం
                పట్టాదార్ పాస్ పుస్తకం & భూమి హక్కుల ధృవీకరణ నోటీసు
                
                నోటీసు సంఖ్య: REV/2026/142-A
                తేదీ: 10-09-2026
                సర్వే నంబర్: 142/A, విస్తీర్ణం: 2.50 ఎకరాలు.
                భూమి స్వభావం: పట్టా మెట్ట (Dry Agricultural Land).
                గ్రామం: శాంతినగర్, మండలం: ఖమ్మం రూరల్.
                
                ముఖ్య ఆదేశాలు & షరతులు:
                1. ఈ నోటీసు అందిన 30 రోజులలోపు సంబంధిత తహశీల్దార్ కార్యాలయంలో మీ ఆధార్ కార్డు, పాత పహాణీ నకలు, పట్టాదార్ పాస్ పుస్తకంతో స్వయంగా హాజరుకావలెను.
                2. సరిహద్దుల డిజిటల్ ల్యాండ్ సర్వే కొరకు నిర్ణీత రుసుము రూ. 150/- మీసేవ ద్వారా చెల్లించి రసీదు సమర్పించవలెను.
                3. నిర్దేశిత 30 రోజుల గడువులోపు హాజరుకానిచో ఈ నోటీసు చట్టరీత్యా రద్దగును మరియు భూ హక్కుల మార్పిడికి అవకాశం ఉండదు.
            """.trimIndent()

            TestDocumentType.ELECTRICITY_DISCONNECTION_NOTICE -> """
                TSSPDCL - TELANGANA STATE SOUTHERN POWER DISTRIBUTION COMPANY LIMITED
                విద్యుత్ సరఫరా నిలిపివేత ముందస్తు హెచ్చరిక నోటీసు (Disconnection Notice)
                
                సర్వీస్ కనెక్షన్ నంబర్: 44091-88231
                వినియోగదారుని పేరు: [గృహ యజమాని]
                మీటర్ కేటగిరీ: LT-1 Domestic
                బకాయి మొత్తం: రూ. 1,420/- (రెండు నెలల విద్యుత్ బిల్లు)
                
                గడువు & ఆదేశాలు:
                1. తేది: 20-09-2026 లోపు బకాయి మొత్తం రూ. 1,420/- చెల్లించవలెను.
                2. గడువు ముగిసినచో ఎటువంటి ముందస్తు సమాచారం లేకుండా విద్యుత్ సరఫరా తాత్కాలికంగా నిలిపివేయబడును (Power Disconnection).
                3. తిరిగి కనెక్షన్ పునరుద్ధరణ కొరకు రీ-కనెక్షన్ ఛార్జీ రూ. 100/- అదనంగా చెల్లించవలసి ఉంటుంది.
            """.trimIndent()

            TestDocumentType.RATION_CARD_KYC_NOTICE -> """
                తెలంగాణ పౌర సరఫరాల శాఖ (Department of Civil Supplies)
                ఆహార భద్రత కార్డు e-KYC ధృవీకరణ నోటీసు
                
                రేషన్ కార్డు నంబర్: FSC-361092401
                గ్రామం / వార్డు: శాంతినగర్
                
                ముఖ్య సమాచారం & మార్గదర్శకాలు:
                1. రేషన్ కార్డులో నమోదైన ప్రతి కుటుంబ సభ్యుడు తమ సమీప రేషన్ దుకాణంలో (Fair Price Shop) ఈ-పాస్ (e-PoS) యంత్రం ద్వారా బయోమెట్రిక్ e-KYC పూర్తి చేసుకోవాలి.
                2. చివరి తేదీ: 15-10-2026.
                3. e-KYC కొరకు ఎటువంటి రుసుము (Fee) చెల్లించనవసరం లేదు. ఇది పూర్తిగా ఉచిత సేవ.
                4. గడువులోపు e-KYC చేయించుకోని సభ్యుల పేర్లు తాత్కాలिकంగా నిలుపుదల చేయబడతాయి.
            """.trimIndent()

            TestDocumentType.SCANNED_PHOTOSTAT_DOCUMENT -> """
                [స్కాన్ చేయబడిన తక్కువ నాణ్యత గల జెరాక్స్ నకలు]
                గ్రామ పంచాయతీ కార్యా..లయం శాంతినగర్
                తేదీ: 01-09-20..
                నోటీసు: ఇంటి పన్ను మరియు తాగునీటి కుళాయి పన్ను బకాయిల వసూలు కొరకు.
                ఇంటి నం: 3-45/B
                బకాయి మొత్తం: రూ. 850/-
                గడువు: 15 రోజుల్లోపు పంచాయతీ కార్యాలయంలో చెల్లించి రసీదు పొందగలరు.
                [అధికారిక రౌండ్ ముద్ర మరియు సంతకం ఉన్నాయి]
            """.trimIndent()

            TestDocumentType.CORRUPTED_MALFORMED_FILE -> ""
        }
    }
}
