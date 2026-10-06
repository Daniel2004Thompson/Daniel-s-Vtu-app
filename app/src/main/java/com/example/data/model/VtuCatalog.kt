package com.example.data.model

object VtuCatalog {
    val dataPlans: List<DataPlan> = listOf(
        // MTN SME & Corporate
        DataPlan("mtn_sme_500mb", NetworkProvider.MTN, DataCategory.SME, "500 MB", "30 Days", 150.0, 200.0),
        DataPlan("mtn_sme_1gb", NetworkProvider.MTN, DataCategory.SME, "1.0 GB", "30 Days", 280.0, 350.0),
        DataPlan("mtn_sme_2gb", NetworkProvider.MTN, DataCategory.SME, "2.0 GB", "30 Days", 560.0, 700.0),
        DataPlan("mtn_sme_3gb", NetworkProvider.MTN, DataCategory.SME, "3.0 GB", "30 Days", 840.0, 1050.0),
        DataPlan("mtn_sme_5gb", NetworkProvider.MTN, DataCategory.SME, "5.0 GB", "30 Days", 1400.0, 1750.0),
        DataPlan("mtn_sme_10gb", NetworkProvider.MTN, DataCategory.SME, "10.0 GB", "30 Days", 2800.0, 3500.0),
        DataPlan("mtn_corp_15gb", NetworkProvider.MTN, DataCategory.CORPORATE, "15.0 GB", "30 Days", 4200.0, 5000.0),
        DataPlan("mtn_corp_20gb", NetworkProvider.MTN, DataCategory.CORPORATE, "20.0 GB", "30 Days", 5600.0, 6800.0),
        DataPlan("mtn_daily_1gb", NetworkProvider.MTN, DataCategory.DAILY_WEEKLY, "1.0 GB", "1 Day", 350.0, 400.0),
        DataPlan("mtn_weekly_2gb", NetworkProvider.MTN, DataCategory.DAILY_WEEKLY, "2.5 GB", "7 Days", 750.0, 900.0),

        // Airtel
        DataPlan("airtel_sme_1gb", NetworkProvider.AIRTEL, DataCategory.SME, "1.0 GB", "30 Days", 290.0, 350.0),
        DataPlan("airtel_sme_2gb", NetworkProvider.AIRTEL, DataCategory.SME, "2.0 GB", "30 Days", 580.0, 700.0),
        DataPlan("airtel_sme_5gb", NetworkProvider.AIRTEL, DataCategory.SME, "5.0 GB", "30 Days", 1450.0, 1750.0),
        DataPlan("airtel_corp_10gb", NetworkProvider.AIRTEL, DataCategory.CORPORATE, "10.0 GB", "30 Days", 2900.0, 3500.0),
        DataPlan("airtel_corp_20gb", NetworkProvider.AIRTEL, DataCategory.CORPORATE, "20.0 GB", "30 Days", 5800.0, 7000.0),
        DataPlan("airtel_daily_1gb", NetworkProvider.AIRTEL, DataCategory.DAILY_WEEKLY, "1.0 GB", "1 Day", 330.0, 400.0),
        DataPlan("airtel_weekly_3gb", NetworkProvider.AIRTEL, DataCategory.DAILY_WEEKLY, "3.0 GB", "7 Days", 800.0, 1000.0),

        // Glo
        DataPlan("glo_sme_1gb", NetworkProvider.GLO, DataCategory.SME, "1.0 GB", "30 Days", 260.0, 300.0),
        DataPlan("glo_sme_2gb", NetworkProvider.GLO, DataCategory.SME, "2.0 GB", "30 Days", 520.0, 600.0),
        DataPlan("glo_sme_5gb", NetworkProvider.GLO, DataCategory.SME, "5.0 GB", "30 Days", 1300.0, 1500.0),
        DataPlan("glo_corp_10gb", NetworkProvider.GLO, DataCategory.CORPORATE, "10.0 GB", "30 Days", 2600.0, 3000.0),
        DataPlan("glo_monthly_14gb", NetworkProvider.GLO, DataCategory.MONTHLY, "14.0 GB", "30 Days", 3800.0, 4500.0),

        // 9mobile
        DataPlan("9mob_sme_1gb", NetworkProvider.NINEMOBILE, DataCategory.SME, "1.0 GB", "30 Days", 220.0, 300.0),
        DataPlan("9mob_sme_2gb", NetworkProvider.NINEMOBILE, DataCategory.SME, "2.0 GB", "30 Days", 440.0, 600.0),
        DataPlan("9mob_sme_5gb", NetworkProvider.NINEMOBILE, DataCategory.SME, "5.0 GB", "30 Days", 1100.0, 1500.0),
        DataPlan("9mob_corp_10gb", NetworkProvider.NINEMOBILE, DataCategory.CORPORATE, "10.0 GB", "30 Days", 2200.0, 3000.0)
    )

    val electricityDiscos: List<ElectricityProvider> = listOf(
        ElectricityProvider(
            id = "ikedc",
            name = "Ikeja Electric (IKEDC)",
            shortName = "IKEDC",
            stateCoverage = "Lagos Mainland & Environs",
            logoRes = com.example.R.drawable.ic_disco_ikedc,
            brandColor = androidx.compose.ui.graphics.Color(0xFFC4161C)
        ),
        ElectricityProvider(
            id = "ekedc",
            name = "Eko Electric (EKEDC)",
            shortName = "EKEDC",
            stateCoverage = "Lagos Island & Lekki/VI",
            logoRes = com.example.R.drawable.ic_disco_ekedc,
            brandColor = androidx.compose.ui.graphics.Color(0xFF1E1B6A)
        ),
        ElectricityProvider(
            id = "aedc",
            name = "Abuja Electricity (AEDC)",
            shortName = "AEDC",
            stateCoverage = "FCT Abuja, Nasarawa, Kogi, Niger",
            logoRes = com.example.R.drawable.ic_disco_aedc,
            brandColor = androidx.compose.ui.graphics.Color(0xFF070D6E)
        ),
        ElectricityProvider(
            id = "ibedc",
            name = "Ibadan Electricity (IBEDC)",
            shortName = "IBEDC",
            stateCoverage = "Oyo, Ogun, Osun, Kwara",
            logoRes = com.example.R.drawable.ic_disco_ibedc,
            brandColor = androidx.compose.ui.graphics.Color(0xFF003359)
        ),
        ElectricityProvider(
            id = "phed",
            name = "Port Harcourt Electric (PHED)",
            shortName = "PHED",
            stateCoverage = "Rivers, Bayelsa, Cross River, Akwa Ibom",
            logoRes = com.example.R.drawable.ic_disco_phed,
            brandColor = androidx.compose.ui.graphics.Color(0xFF164194)
        ),
        ElectricityProvider(
            id = "eedc",
            name = "Enugu Electricity (EEDC)",
            shortName = "EEDC",
            stateCoverage = "Enugu, Abia, Anambra, Imo, Ebonyi",
            logoRes = com.example.R.drawable.ic_disco_eedc,
            brandColor = androidx.compose.ui.graphics.Color(0xFFE30613)
        ),
        ElectricityProvider(
            id = "kedco",
            name = "Kano Electricity (KEDCO)",
            shortName = "KEDCO",
            stateCoverage = "Kano, Katsina, Jigawa",
            logoRes = com.example.R.drawable.ic_disco_kedco,
            brandColor = androidx.compose.ui.graphics.Color(0xFF1F2278)
        )
    )

    val cableProviders: List<CableProvider> = listOf(
        CableProvider(
            id = "dstv",
            name = "DStv",
            logoRes = com.example.R.drawable.ic_cable_dstv,
            brandColor = androidx.compose.ui.graphics.Color(0xFF005DAA),
            tagline = "So much more",
            bouquets = listOf(
                CableBouquet("dstv_padi", "DStv Padi", 3600.0, 45),
                CableBouquet("dstv_yanga", "DStv Yanga", 5100.0, 85),
                CableBouquet("dstv_confam", "DStv Confam", 9300.0, 120),
                CableBouquet("dstv_compact", "DStv Compact", 15700.0, 145),
                CableBouquet("dstv_compact_plus", "DStv Compact Plus", 25000.0, 160),
                CableBouquet("dstv_premium", "DStv Premium", 37000.0, 175)
            )
        ),
        CableProvider(
            id = "gotv",
            name = "GOtv",
            logoRes = com.example.R.drawable.ic_cable_gotv,
            brandColor = androidx.compose.ui.graphics.Color(0xFFED1C24),
            tagline = "Live it. Love it.",
            bouquets = listOf(
                CableBouquet("gotv_smallie", "GOtv Smallie", 1575.0, 35),
                CableBouquet("gotv_jinja", "GOtv Jinja", 3300.0, 47),
                CableBouquet("gotv_jolli", "GOtv Jolli", 4850.0, 68),
                CableBouquet("gotv_max", "GOtv Max", 7200.0, 75),
                CableBouquet("gotv_supa", "GOtv Supa", 9600.0, 80),
                CableBouquet("gotv_supa_plus", "GOtv Supa Plus", 15700.0, 85)
            )
        ),
        CableProvider(
            id = "startimes",
            name = "StarTimes",
            logoRes = com.example.R.drawable.ic_cable_startimes,
            brandColor = androidx.compose.ui.graphics.Color(0xFF0077C0),
            tagline = "Enjoy Digital Life",
            bouquets = listOf(
                CableBouquet("star_nova", "Nova Monthly", 1700.0, 38),
                CableBouquet("star_basic", "Basic Monthly", 3300.0, 60),
                CableBouquet("star_smart", "Smart Monthly", 4200.0, 70),
                CableBouquet("star_classic", "Classic Monthly", 5000.0, 80),
                CableBouquet("star_super", "Super Monthly", 8200.0, 95)
            )
        )
    )

    val educationExams: List<EducationExam> = listOf(
        EducationExam(
            id = "waec_pin",
            name = "WAEC Result Checker PIN",
            shortName = "WAEC",
            description = "Instant e-PIN with Serial number to check WASSCE results online",
            price = 3700.0,
            logoRes = com.example.R.drawable.ic_exam_waec,
            brandColor = androidx.compose.ui.graphics.Color(0xFF23338B)
        ),
        EducationExam(
            id = "jamb_utme",
            name = "JAMB UTME Profile Registration e-PIN",
            shortName = "JAMB",
            description = "Official JAMB UTME profile confirmation code and registration PIN",
            price = 6200.0,
            logoRes = com.example.R.drawable.ic_exam_jamb,
            brandColor = androidx.compose.ui.graphics.Color(0xFF00873E)
        ),
        EducationExam(
            id = "neco_token",
            name = "NECO Result Token",
            shortName = "NECO",
            description = "Official token for checking SSCE internal & external results",
            price = 1500.0,
            logoRes = com.example.R.drawable.ic_exam_neco,
            brandColor = androidx.compose.ui.graphics.Color(0xFF009E49)
        ),
        EducationExam(
            id = "nabteb_pin",
            name = "NABTEB Scratch Card",
            shortName = "NABTEB",
            description = "National Business and Technical Examinations Board result checker card",
            price = 1400.0,
            logoRes = com.example.R.drawable.ic_exam_nabteb,
            brandColor = androidx.compose.ui.graphics.Color(0xFF007A33)
        )
    )

    fun findDiscoByProvider(providerOrText: String?): ElectricityProvider? {
        if (providerOrText.isNullOrBlank()) return null
        val upper = providerOrText.uppercase()
        return when {
            upper.contains("IKEDC") || upper.contains("IKEJA") -> electricityDiscos.firstOrNull { it.id == "ikedc" }
            upper.contains("EKEDC") || upper.contains("EKO") -> electricityDiscos.firstOrNull { it.id == "ekedc" }
            upper.contains("AEDC") || upper.contains("ABUJA") -> electricityDiscos.firstOrNull { it.id == "aedc" }
            upper.contains("IBEDC") || upper.contains("IBADAN") -> electricityDiscos.firstOrNull { it.id == "ibedc" }
            upper.contains("PHED") || upper.contains("PORT HARCOURT") -> electricityDiscos.firstOrNull { it.id == "phed" }
            upper.contains("EEDC") || upper.contains("ENUGU") -> electricityDiscos.firstOrNull { it.id == "eedc" }
            upper.contains("KEDCO") || upper.contains("KANO") -> electricityDiscos.firstOrNull { it.id == "kedco" }
            else -> null
        }
    }

    fun findCableByProvider(providerOrText: String?): CableProvider? {
        if (providerOrText.isNullOrBlank()) return null
        val upper = providerOrText.uppercase()
        return when {
            upper.contains("DSTV") -> cableProviders.firstOrNull { it.id == "dstv" }
            upper.contains("GOTV") -> cableProviders.firstOrNull { it.id == "gotv" }
            upper.contains("STAR") -> cableProviders.firstOrNull { it.id == "startimes" }
            else -> null
        }
    }

    fun findExamByProvider(providerOrText: String?): EducationExam? {
        if (providerOrText.isNullOrBlank()) return null
        val upper = providerOrText.uppercase()
        return when {
            upper.contains("WAEC") -> educationExams.firstOrNull { it.shortName == "WAEC" }
            upper.contains("JAMB") -> educationExams.firstOrNull { it.shortName == "JAMB" }
            upper.contains("NECO") -> educationExams.firstOrNull { it.shortName == "NECO" }
            upper.contains("NABT") -> educationExams.firstOrNull { it.shortName == "NABTEB" }
            else -> null
        }
    }
}
