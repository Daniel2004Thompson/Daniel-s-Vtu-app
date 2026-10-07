package com.example.data.model

object VtuCatalog {
    val dataPlans: List<DataPlan> = listOf(
        // MTN SME & Gifting (Live Gsubz Plan IDs & Prices)
        DataPlan("mtn_sme_500mb", NetworkProvider.MTN, DataCategory.SME, "500 MB", "7 Days", 299.0, 350.0, planCode = "179", gsubzServiceId = "mtn_sme"),
        DataPlan("mtn_sme_1gb", NetworkProvider.MTN, DataCategory.SME, "1.0 GB", "30 Days", 399.0, 450.0, planCode = "166", gsubzServiceId = "mtn_sme"),
        DataPlan("mtn_sme_2gb", NetworkProvider.MTN, DataCategory.SME, "2.0 GB", "30 Days", 899.0, 1000.0, planCode = "167", gsubzServiceId = "mtn_sme"),
        DataPlan("mtn_sme_3gb", NetworkProvider.MTN, DataCategory.SME, "3.0 GB", "30 Days", 1348.0, 1500.0, planCode = "168", gsubzServiceId = "mtn_sme"),
        DataPlan("mtn_sme_5gb", NetworkProvider.MTN, DataCategory.SME, "5.0 GB", "30 Days", 1799.0, 2000.0, planCode = "357", gsubzServiceId = "mtn_sme"),
        DataPlan("mtn_cg_500mb", NetworkProvider.MTN, DataCategory.CORPORATE, "500 MB", "7 Days", 299.0, 350.0, planCode = "179", gsubzServiceId = "mtn_sme"),
        DataPlan("mtn_cg_1gb", NetworkProvider.MTN, DataCategory.CORPORATE, "1.0 GB", "30 Days", 399.0, 450.0, planCode = "166", gsubzServiceId = "mtn_gifting"),
        DataPlan("mtn_cg_2gb", NetworkProvider.MTN, DataCategory.CORPORATE, "2.0 GB", "30 Days", 899.0, 1000.0, planCode = "366", gsubzServiceId = "mtn_gifting"),
        DataPlan("mtn_cg_3gb", NetworkProvider.MTN, DataCategory.CORPORATE, "3.0 GB", "30 Days", 1348.0, 1500.0, planCode = "168", gsubzServiceId = "mtn_gifting"),
        DataPlan("mtn_cg_5gb", NetworkProvider.MTN, DataCategory.CORPORATE, "5.0 GB", "30 Days", 1799.0, 2000.0, planCode = "357", gsubzServiceId = "mtn_gifting"),
        DataPlan("mtn_daily_750mb", NetworkProvider.MTN, DataCategory.DAILY_WEEKLY, "750 MB + 1Hr YT/IG/TT", "3 Days", 436.0, 500.0, planCode = "373", gsubzServiceId = "mtn_gifting"),
        DataPlan("mtn_social_1_2gb", NetworkProvider.MTN, DataCategory.GIFTING, "1.2 GB (All Socials)", "30 Days", 436.0, 500.0, planCode = "374", gsubzServiceId = "mtn_gifting"),
        DataPlan("mtn_daily_1_5gb", NetworkProvider.MTN, DataCategory.DAILY_WEEKLY, "1.5 GB", "2 Days", 582.0, 650.0, planCode = "355", gsubzServiceId = "mtn_gifting"),
        DataPlan("mtn_weekly_2_5gb", NetworkProvider.MTN, DataCategory.DAILY_WEEKLY, "2.5 GB", "2 Days", 873.0, 950.0, planCode = "365", gsubzServiceId = "mtn_gifting"),
        DataPlan("mtn_gifting_2gb", NetworkProvider.MTN, DataCategory.GIFTING, "2.0 GB Gifting", "30 Days", 899.0, 1000.0, planCode = "366", gsubzServiceId = "mtn_gifting"),
        DataPlan("mtn_daily_3_2gb", NetworkProvider.MTN, DataCategory.DAILY_WEEKLY, "3.2 GB", "2 Days", 970.0, 1100.0, planCode = "360", gsubzServiceId = "mtn_gifting"),
        DataPlan("mtn_daily_3_5gb", NetworkProvider.MTN, DataCategory.DAILY_WEEKLY, "3.5 GB", "1 Day", 970.0, 1100.0, planCode = "376", gsubzServiceId = "mtn_gifting"),

        // Airtel SME & Gifting (Live Gsubz Plan IDs & Prices)
        DataPlan("airtel_sme_300mb", NetworkProvider.AIRTEL, DataCategory.SME, "300 MB", "2 Days", 110.0, 150.0, planCode = "476", gsubzServiceId = "airtel_sme"),
        DataPlan("airtel_sme_600mb", NetworkProvider.AIRTEL, DataCategory.SME, "600 MB", "2 Days", 210.0, 250.0, planCode = "478", gsubzServiceId = "airtel_sme"),
        DataPlan("airtel_sme_1gb", NetworkProvider.AIRTEL, DataCategory.SME, "1.5 GB", "1 Day", 392.0, 450.0, planCode = "484", gsubzServiceId = "airtel_sme"),
        DataPlan("airtel_sme_2gb", NetworkProvider.AIRTEL, DataCategory.SME, "3.0 GB", "2 Days", 735.0, 850.0, planCode = "485", gsubzServiceId = "airtel_sme"),
        DataPlan("airtel_sme_5gb", NetworkProvider.AIRTEL, DataCategory.SME, "10.0 GB", "30 Days", 3010.0, 3500.0, planCode = "483", gsubzServiceId = "airtel_sme"),
        DataPlan("airtel_cg_300mb", NetworkProvider.AIRTEL, DataCategory.CORPORATE, "300 MB", "2 Days", 294.0, 340.0, planCode = "555", gsubzServiceId = "airtel_gifting"),
        DataPlan("airtel_cg_500mb", NetworkProvider.AIRTEL, DataCategory.CORPORATE, "500 MB", "7 Days", 490.0, 550.0, planCode = "144", gsubzServiceId = "airtel_gifting"),
        DataPlan("airtel_cg_1gb", NetworkProvider.AIRTEL, DataCategory.CORPORATE, "1.0 GB", "7 Days", 780.0, 860.0, planCode = "324", gsubzServiceId = "airtel_gifting"),
        DataPlan("airtel_cg_2gb", NetworkProvider.AIRTEL, DataCategory.CORPORATE, "2.0 GB", "30 Days", 1470.0, 1600.0, planCode = "427", gsubzServiceId = "airtel_gifting"),
        DataPlan("airtel_cg_5gb", NetworkProvider.AIRTEL, DataCategory.CORPORATE, "4.0 GB", "30 Days", 2462.5, 2700.0, planCode = "148", gsubzServiceId = "airtel_gifting"),
        DataPlan("airtel_daily_100mb", NetworkProvider.AIRTEL, DataCategory.DAILY_WEEKLY, "100 MB", "1 Day", 98.0, 120.0, planCode = "552", gsubzServiceId = "airtel_gifting"),
        DataPlan("airtel_daily_230mb", NetworkProvider.AIRTEL, DataCategory.DAILY_WEEKLY, "230 MB", "2 Days", 196.0, 220.0, planCode = "389", gsubzServiceId = "airtel_gifting"),
        DataPlan("airtel_daily_1gb", NetworkProvider.AIRTEL, DataCategory.DAILY_WEEKLY, "1.0 GB (Socials)", "1 Day", 294.5, 350.0, planCode = "426", gsubzServiceId = "airtel_gifting"),
        DataPlan("airtel_weekly_500mb", NetworkProvider.AIRTEL, DataCategory.DAILY_WEEKLY, "500 MB", "7 Days", 490.0, 550.0, planCode = "144", gsubzServiceId = "airtel_gifting"),
        DataPlan("airtel_daily_2gb", NetworkProvider.AIRTEL, DataCategory.DAILY_WEEKLY, "2.0 GB", "2 Days", 586.0, 650.0, planCode = "145", gsubzServiceId = "airtel_gifting"),
        DataPlan("airtel_weekly_1gb", NetworkProvider.AIRTEL, DataCategory.DAILY_WEEKLY, "1.0 GB", "7 Days", 780.0, 850.0, planCode = "324", gsubzServiceId = "airtel_gifting"),
        DataPlan("airtel_daily_4gb", NetworkProvider.AIRTEL, DataCategory.DAILY_WEEKLY, "4.0 GB", "2 Days", 980.0, 1100.0, planCode = "146", gsubzServiceId = "airtel_gifting"),

        // Glo Data & SME (Live Gsubz Plan IDs & Prices)
        DataPlan("glo_cg_200mb", NetworkProvider.GLO, DataCategory.CORPORATE, "200 MB", "14 Days", 120.0, 150.0, planCode = "200", gsubzServiceId = "glo_data"),
        DataPlan("glo_cg_500mb", NetworkProvider.GLO, DataCategory.CORPORATE, "500 MB", "30 Days", 200.0, 250.0, planCode = "500", gsubzServiceId = "glo_data"),
        DataPlan("glo_sme_1gb", NetworkProvider.GLO, DataCategory.CORPORATE, "1.0 GB", "30 Days", 399.0, 450.0, planCode = "1000", gsubzServiceId = "glo_data"),
        DataPlan("glo_sme_2gb", NetworkProvider.GLO, DataCategory.CORPORATE, "2.0 GB", "30 Days", 780.0, 850.0, planCode = "2000", gsubzServiceId = "glo_data"),
        DataPlan("glo_cg_3gb_3d", NetworkProvider.GLO, DataCategory.DAILY_WEEKLY, "3.0 GB", "3 Days", 1017.0, 1150.0, planCode = "3003", gsubzServiceId = "glo_data"),
        DataPlan("glo_cg_3gb_7d", NetworkProvider.GLO, DataCategory.DAILY_WEEKLY, "3.0 GB", "7 Days", 1065.0, 1200.0, planCode = "3007", gsubzServiceId = "glo_data"),
        DataPlan("glo_sme_750mb", NetworkProvider.GLO, DataCategory.SME, "750 MB", "1 Day", 189.0, 220.0, planCode = "491", gsubzServiceId = "glo_sme"),
        DataPlan("glo_sme_1_5gb", NetworkProvider.GLO, DataCategory.SME, "1.5 GB", "1 Day", 284.0, 320.0, planCode = "492", gsubzServiceId = "glo_sme"),
        DataPlan("glo_sme_2_5gb", NetworkProvider.GLO, DataCategory.SME, "2.5 GB", "2 Days", 473.0, 550.0, planCode = "493", gsubzServiceId = "glo_sme"),
        DataPlan("glo_sme_10gb", NetworkProvider.GLO, DataCategory.SME, "10.0 GB", "7 Days", 1890.0, 2100.0, planCode = "494", gsubzServiceId = "glo_sme"),

        // 9mobile (Live Gsubz Plan IDs & Prices)
        DataPlan("9mob_sme_500mb", NetworkProvider.NINEMOBILE, DataCategory.SME, "500 MB", "30 Days", 250.0, 300.0, planCode = "182", gsubzServiceId = "etisalat_data"),
        DataPlan("9mob_sme_1gb", NetworkProvider.NINEMOBILE, DataCategory.SME, "1.0 GB", "30 Days", 499.0, 550.0, planCode = "298", gsubzServiceId = "etisalat_data"),
        DataPlan("9mob_sme_1_5gb", NetworkProvider.NINEMOBILE, DataCategory.SME, "1.5 GB", "30 Days", 748.0, 850.0, planCode = "300", gsubzServiceId = "etisalat_data"),
        DataPlan("9mob_sme_2gb", NetworkProvider.NINEMOBILE, DataCategory.SME, "2.0 GB", "30 Days", 998.0, 1100.0, planCode = "299", gsubzServiceId = "etisalat_data"),
        DataPlan("9mob_sme_3gb", NetworkProvider.NINEMOBILE, DataCategory.CORPORATE, "3.0 GB", "30 Days", 1498.0, 1650.0, planCode = "303", gsubzServiceId = "etisalat_data"),
        DataPlan("9mob_sme_4gb", NetworkProvider.NINEMOBILE, DataCategory.CORPORATE, "4.0 GB", "30 Days", 1997.0, 2200.0, planCode = "347", gsubzServiceId = "etisalat_data"),
        DataPlan("9mob_sme_4_5gb", NetworkProvider.NINEMOBILE, DataCategory.CORPORATE, "4.5 GB", "30 Days", 2247.0, 2450.0, planCode = "348", gsubzServiceId = "etisalat_data"),
        DataPlan("9mob_sme_5gb", NetworkProvider.NINEMOBILE, DataCategory.CORPORATE, "5.0 GB", "30 Days", 2496.0, 2700.0, planCode = "304", gsubzServiceId = "etisalat_data")
    )

    val electricityDiscos: List<ElectricityProvider> = listOf(
        ElectricityProvider(
            id = "ikedc",
            name = "Ikeja Electric (IKEDC)",
            shortName = "IKEDC",
            stateCoverage = "Lagos Mainland & Environs",
            logoRes = com.example.R.drawable.ic_disco_ikedc,
            brandColor = androidx.compose.ui.graphics.Color(0xFFC4161C),
            gsubzServiceId = "ikeja-electric"
        ),
        ElectricityProvider(
            id = "ekedc",
            name = "Eko Electric (EKEDC)",
            shortName = "EKEDC",
            stateCoverage = "Lagos Island & Lekki/VI",
            logoRes = com.example.R.drawable.ic_disco_ekedc,
            brandColor = androidx.compose.ui.graphics.Color(0xFF1E1B6A),
            gsubzServiceId = "eko-electric"
        ),
        ElectricityProvider(
            id = "aedc",
            name = "Abuja Electricity (AEDC)",
            shortName = "AEDC",
            stateCoverage = "FCT Abuja, Nasarawa, Kogi, Niger",
            logoRes = com.example.R.drawable.ic_disco_aedc,
            brandColor = androidx.compose.ui.graphics.Color(0xFF070D6E),
            gsubzServiceId = "abuja-electric"
        ),
        ElectricityProvider(
            id = "ibedc",
            name = "Ibadan Electricity (IBEDC)",
            shortName = "IBEDC",
            stateCoverage = "Oyo, Ogun, Osun, Kwara",
            logoRes = com.example.R.drawable.ic_disco_ibedc,
            brandColor = androidx.compose.ui.graphics.Color(0xFF003359),
            gsubzServiceId = "ibadan-electric"
        ),
        ElectricityProvider(
            id = "phed",
            name = "Port Harcourt Electric (PHED)",
            shortName = "PHED",
            stateCoverage = "Rivers, Bayelsa, Cross River, Akwa Ibom",
            logoRes = com.example.R.drawable.ic_disco_phed,
            brandColor = androidx.compose.ui.graphics.Color(0xFF164194),
            gsubzServiceId = "portharcourt-electric"
        ),
        ElectricityProvider(
            id = "eedc",
            name = "Enugu Electricity (EEDC)",
            shortName = "EEDC",
            stateCoverage = "Enugu, Abia, Anambra, Imo, Ebonyi",
            logoRes = com.example.R.drawable.ic_disco_eedc,
            brandColor = androidx.compose.ui.graphics.Color(0xFFE30613),
            gsubzServiceId = "enugu-electric"
        ),
        ElectricityProvider(
            id = "kedco",
            name = "Kano Electricity (KEDCO)",
            shortName = "KEDCO",
            stateCoverage = "Kano, Katsina, Jigawa",
            logoRes = com.example.R.drawable.ic_disco_kedco,
            brandColor = androidx.compose.ui.graphics.Color(0xFF1F2278),
            gsubzServiceId = "kano-electric"
        ),
        ElectricityProvider(
            id = "jed",
            name = "Jos Electricity (JED)",
            shortName = "JED",
            stateCoverage = "Plateau, Bauchi, Benue, Gombe",
            logoRes = com.example.R.drawable.ic_disco_jed,
            brandColor = androidx.compose.ui.graphics.Color(0xFF0E5A8A),
            gsubzServiceId = "jos-electric"
        ),
        ElectricityProvider(
            id = "kaedco",
            name = "Kaduna Electricity (KAEDCO)",
            shortName = "KAEDCO",
            stateCoverage = "Kaduna, Kebbi, Sokoto, Zamfara",
            logoRes = com.example.R.drawable.ic_disco_kaedco,
            brandColor = androidx.compose.ui.graphics.Color(0xFF006838),
            gsubzServiceId = "kaduna-electric"
        ),
        ElectricityProvider(
            id = "bedc",
            name = "Benin Electricity (BEDC)",
            shortName = "BEDC",
            stateCoverage = "Edo, Delta, Ondo, Ekiti",
            logoRes = com.example.R.drawable.ic_disco_bedc,
            brandColor = androidx.compose.ui.graphics.Color(0xFF8B1D41),
            gsubzServiceId = "benin-electric"
        )
    )

    val cableProviders: List<CableProvider> = listOf(
        CableProvider(
            id = "dstv",
            name = "DStv",
            logoRes = com.example.R.drawable.ic_cable_dstv,
            brandColor = androidx.compose.ui.graphics.Color(0xFF005DAA),
            tagline = "So much more",
            gsubzServiceId = "dstv",
            bouquets = listOf(
                CableBouquet("dstv_padi", "DStv Padi", 4400.0, 45, planCode = "dstv-padi"),
                CableBouquet("dstv_yanga", "DStv Yanga", 6000.0, 85, planCode = "dstv-yanga"),
                CableBouquet("dstv_confam", "DStv Confam", 11000.0, 120, planCode = "dstv-confam"),
                CableBouquet("dstv_compact", "DStv Compact", 19000.0, 145, planCode = "dstv79"),
                CableBouquet("dstv_compact_plus", "DStv Compact Plus", 30000.0, 160, planCode = "dstv7"),
                CableBouquet("dstv_premium", "DStv Premium", 44500.0, 175, planCode = "dstv3"),
                CableBouquet("dstv_premium_asia", "DStv Premium-Asia", 50500.0, 185, planCode = "dstv10"),
                CableBouquet("dstv_premium_french", "DStv Premium-French", 69000.0, 195, planCode = "dstv9")
            )
        ),
        CableProvider(
            id = "gotv",
            name = "GOtv",
            logoRes = com.example.R.drawable.ic_cable_gotv,
            brandColor = androidx.compose.ui.graphics.Color(0xFFED1C24),
            tagline = "Live it. Love it.",
            gsubzServiceId = "gotv",
            bouquets = listOf(
                CableBouquet("gotv_smallie", "GOtv Smallie (Monthly)", 1900.0, 35, planCode = "gotv-smallie"),
                CableBouquet("gotv_jinja", "GOtv Jinja", 3900.0, 47, planCode = "gotv-jinja"),
                CableBouquet("gotv_jolli", "GOtv Jolli", 5800.0, 68, planCode = "gotv-jolli"),
                CableBouquet("gotv_max", "GOtv Max", 8500.0, 75, planCode = "gotv-max"),
                CableBouquet("gotv_supa", "GOtv Supa (Monthly)", 11400.0, 80, planCode = "gotv-supa"),
                CableBouquet("gotv_supa_plus", "GOtv Supa Plus (Monthly)", 16800.0, 85, planCode = "gotv-supa-plus"),
                CableBouquet("gotv_smallie_quarterly", "GOtv Smallie (Quarterly)", 5100.0, 35, planCode = "gotv-smallie-3months"),
                CableBouquet("gotv_smallie_yearly", "GOtv Smallie (Yearly)", 15000.0, 35, planCode = "gotv-smallie-1year")
            )
        ),
        CableProvider(
            id = "startimes",
            name = "StarTimes",
            logoRes = com.example.R.drawable.ic_cable_startimes,
            brandColor = androidx.compose.ui.graphics.Color(0xFF0077C0),
            tagline = "Enjoy Digital Life",
            gsubzServiceId = "startimes",
            bouquets = listOf(
                CableBouquet("star_nova", "Nova Monthly", 2100.0, 38, planCode = "nova"),
                CableBouquet("star_basic", "Basic (Antenna) Monthly", 4000.0, 60, planCode = "basic"),
                CableBouquet("star_smart", "Basic (Dish) / Smart Monthly", 5100.0, 70, planCode = "smart"),
                CableBouquet("star_classic", "Classic (Antenna) Monthly", 6000.0, 80, planCode = "classic"),
                CableBouquet("star_super", "Super (Dish) Monthly", 9800.0, 95, planCode = "super"),
                CableBouquet("star_nova_weekly", "Nova Weekly", 700.0, 38, planCode = "nova-weekly"),
                CableBouquet("star_basic_weekly", "Basic Weekly", 1400.0, 60, planCode = "basic-weekly"),
                CableBouquet("star_smart_weekly", "Smart Weekly", 1700.0, 70, planCode = "smart-weekly")
            )
        ),
        CableProvider(
            id = "showmax",
            name = "Showmax",
            logoRes = com.example.R.drawable.ic_cable_showmax,
            brandColor = androidx.compose.ui.graphics.Color(0xFFE91E63),
            tagline = "Stream Original Series & Live Football",
            gsubzServiceId = "showmax",
            bouquets = listOf(
                CableBouquet("showmax_mobile", "Showmax Mobile Only", 1600.0, 50, planCode = "mobile_only_3"),
                CableBouquet("showmax_full", "Showmax Full", 3500.0, 80, planCode = "full_3"),
                CableBouquet("showmax_sports_mobile", "Showmax Sports Mobile Only", 4000.0, 95, planCode = "sports_mobile_only_3"),
                CableBouquet("showmax_sports_full", "Showmax Sports Full", 6500.0, 120, planCode = "sports_full_3")
            )
        )
    )

    val educationExams: List<EducationExam> = listOf(
        EducationExam(
            id = "waec_pin",
            name = "WAEC Registration & Result Checker PIN",
            shortName = "WAEC",
            description = "Instant e-PIN with Serial number to check WASSCE results online",
            price = 5100.0,
            logoRes = com.example.R.drawable.ic_exam_waec,
            brandColor = androidx.compose.ui.graphics.Color(0xFF23338B),
            gsubzServiceId = "waec",
            planCode = "WAEC"
        ),
        EducationExam(
            id = "neco_token",
            name = "NECO Result Checker PIN",
            shortName = "NECO",
            description = "Official token for checking SSCE internal & external results",
            price = 2100.0,
            logoRes = com.example.R.drawable.ic_exam_neco,
            brandColor = androidx.compose.ui.graphics.Color(0xFF009E49),
            gsubzServiceId = "neco",
            planCode = "NECO"
        ),
        EducationExam(
            id = "nabteb_pin",
            name = "NABTEB Result Checker PIN",
            shortName = "NABTEB",
            description = "National Business and Technical Examinations Board result checker card",
            price = 900.0,
            logoRes = com.example.R.drawable.ic_exam_nabteb,
            brandColor = androidx.compose.ui.graphics.Color(0xFF007A33),
            gsubzServiceId = "nabteb",
            planCode = "NABTEB"
        ),
        EducationExam(
            id = "jamb_utme",
            name = "JAMB UTME Profile Registration e-PIN",
            shortName = "JAMB",
            description = "Official JAMB UTME profile confirmation code and registration PIN",
            price = 6200.0,
            logoRes = com.example.R.drawable.ic_exam_jamb,
            brandColor = androidx.compose.ui.graphics.Color(0xFF00873E),
            gsubzServiceId = "jamb",
            planCode = "JAMB"
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
            upper.contains("JED") || upper.contains("JOS") -> electricityDiscos.firstOrNull { it.id == "jed" }
            upper.contains("KAEDCO") || upper.contains("KADUNA") -> electricityDiscos.firstOrNull { it.id == "kaedco" }
            upper.contains("BEDC") || upper.contains("BENIN") -> electricityDiscos.firstOrNull { it.id == "bedc" }
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
            upper.contains("SHOWMAX") -> cableProviders.firstOrNull { it.id == "showmax" }
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
