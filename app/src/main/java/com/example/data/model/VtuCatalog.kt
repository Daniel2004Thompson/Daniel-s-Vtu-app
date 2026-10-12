package com.example.data.model

object VtuCatalog {
    // Data bundle metadata only (service_id + plan code + label/validity).
    // All prices are fetched live from public.vtu_prices on every load — never hardcoded here.
    val dataPlans: List<DataPlan> = listOf(
        // MTN SME & Gifting (service_id: mtn_sme, mtn_gifting)
        DataPlan(id = "mtn_sme_500mb", network = NetworkProvider.MTN, category = DataCategory.SME, dataAmount = "500 MB", validity = "7 Days", planCode = "179", gsubzServiceId = "mtn_sme"),
        DataPlan(id = "mtn_sme_1gb", network = NetworkProvider.MTN, category = DataCategory.SME, dataAmount = "1.0 GB", validity = "30 Days", planCode = "166", gsubzServiceId = "mtn_sme"),
        DataPlan(id = "mtn_sme_2gb", network = NetworkProvider.MTN, category = DataCategory.SME, dataAmount = "2.0 GB", validity = "30 Days", planCode = "167", gsubzServiceId = "mtn_sme"),
        DataPlan(id = "mtn_sme_3gb", network = NetworkProvider.MTN, category = DataCategory.SME, dataAmount = "3.0 GB", validity = "30 Days", planCode = "168", gsubzServiceId = "mtn_sme"),
        DataPlan(id = "mtn_sme_5gb", network = NetworkProvider.MTN, category = DataCategory.SME, dataAmount = "5.0 GB", validity = "30 Days", planCode = "357", gsubzServiceId = "mtn_sme"),
        DataPlan(id = "mtn_cg_500mb", network = NetworkProvider.MTN, category = DataCategory.CORPORATE, dataAmount = "500 MB", validity = "7 Days", planCode = "179", gsubzServiceId = "mtn_sme"),
        DataPlan(id = "mtn_cg_1gb", network = NetworkProvider.MTN, category = DataCategory.CORPORATE, dataAmount = "1.0 GB", validity = "30 Days", planCode = "166", gsubzServiceId = "mtn_gifting"),
        DataPlan(id = "mtn_cg_2gb", network = NetworkProvider.MTN, category = DataCategory.CORPORATE, dataAmount = "2.0 GB", validity = "30 Days", planCode = "366", gsubzServiceId = "mtn_gifting"),
        DataPlan(id = "mtn_cg_3gb", network = NetworkProvider.MTN, category = DataCategory.CORPORATE, dataAmount = "3.0 GB", validity = "30 Days", planCode = "168", gsubzServiceId = "mtn_gifting"),
        DataPlan(id = "mtn_cg_5gb", network = NetworkProvider.MTN, category = DataCategory.CORPORATE, dataAmount = "5.0 GB", validity = "30 Days", planCode = "357", gsubzServiceId = "mtn_gifting"),
        DataPlan(id = "mtn_daily_750mb", network = NetworkProvider.MTN, category = DataCategory.DAILY_WEEKLY, dataAmount = "750 MB + 1Hr YT/IG/TT", validity = "3 Days", planCode = "373", gsubzServiceId = "mtn_gifting"),
        DataPlan(id = "mtn_social_1_2gb", network = NetworkProvider.MTN, category = DataCategory.GIFTING, dataAmount = "1.2 GB (All Socials)", validity = "30 Days", planCode = "374", gsubzServiceId = "mtn_gifting"),
        DataPlan(id = "mtn_daily_1_5gb", network = NetworkProvider.MTN, category = DataCategory.DAILY_WEEKLY, dataAmount = "1.5 GB", validity = "2 Days", planCode = "355", gsubzServiceId = "mtn_gifting"),
        DataPlan(id = "mtn_weekly_2_5gb", network = NetworkProvider.MTN, category = DataCategory.DAILY_WEEKLY, dataAmount = "2.5 GB", validity = "2 Days", planCode = "365", gsubzServiceId = "mtn_gifting"),
        DataPlan(id = "mtn_gifting_2gb", network = NetworkProvider.MTN, category = DataCategory.GIFTING, dataAmount = "2.0 GB Gifting", validity = "30 Days", planCode = "366", gsubzServiceId = "mtn_gifting"),
        DataPlan(id = "mtn_daily_3_2gb", network = NetworkProvider.MTN, category = DataCategory.DAILY_WEEKLY, dataAmount = "3.2 GB", validity = "2 Days", planCode = "360", gsubzServiceId = "mtn_gifting"),
        DataPlan(id = "mtn_daily_3_5gb", network = NetworkProvider.MTN, category = DataCategory.DAILY_WEEKLY, dataAmount = "3.5 GB", validity = "1 Day", planCode = "376", gsubzServiceId = "mtn_gifting"),

        // Airtel SME & Gifting (service_id: airtel_sme, airtel_gifting)
        DataPlan(id = "airtel_sme_300mb", network = NetworkProvider.AIRTEL, category = DataCategory.SME, dataAmount = "300 MB", validity = "2 Days", planCode = "476", gsubzServiceId = "airtel_sme"),
        DataPlan(id = "airtel_sme_600mb", network = NetworkProvider.AIRTEL, category = DataCategory.SME, dataAmount = "600 MB", validity = "2 Days", planCode = "478", gsubzServiceId = "airtel_sme"),
        DataPlan(id = "airtel_sme_1gb", network = NetworkProvider.AIRTEL, category = DataCategory.SME, dataAmount = "1.5 GB", validity = "1 Day", planCode = "484", gsubzServiceId = "airtel_sme"),
        DataPlan(id = "airtel_sme_2gb", network = NetworkProvider.AIRTEL, category = DataCategory.SME, dataAmount = "3.0 GB", validity = "2 Days", planCode = "485", gsubzServiceId = "airtel_sme"),
        DataPlan(id = "airtel_sme_5gb", network = NetworkProvider.AIRTEL, category = DataCategory.SME, dataAmount = "10.0 GB", validity = "30 Days", planCode = "483", gsubzServiceId = "airtel_sme"),
        DataPlan(id = "airtel_cg_300mb", network = NetworkProvider.AIRTEL, category = DataCategory.CORPORATE, dataAmount = "300 MB", validity = "2 Days", planCode = "555", gsubzServiceId = "airtel_gifting"),
        DataPlan(id = "airtel_cg_500mb", network = NetworkProvider.AIRTEL, category = DataCategory.CORPORATE, dataAmount = "500 MB", validity = "7 Days", planCode = "144", gsubzServiceId = "airtel_gifting"),
        DataPlan(id = "airtel_cg_1gb", network = NetworkProvider.AIRTEL, category = DataCategory.CORPORATE, dataAmount = "1.0 GB", validity = "7 Days", planCode = "324", gsubzServiceId = "airtel_gifting"),
        DataPlan(id = "airtel_cg_2gb", network = NetworkProvider.AIRTEL, category = DataCategory.CORPORATE, dataAmount = "2.0 GB", validity = "30 Days", planCode = "427", gsubzServiceId = "airtel_gifting"),
        DataPlan(id = "airtel_cg_5gb", network = NetworkProvider.AIRTEL, category = DataCategory.CORPORATE, dataAmount = "4.0 GB", validity = "30 Days", planCode = "148", gsubzServiceId = "airtel_gifting"),
        DataPlan(id = "airtel_daily_100mb", network = NetworkProvider.AIRTEL, category = DataCategory.DAILY_WEEKLY, dataAmount = "100 MB", validity = "1 Day", planCode = "552", gsubzServiceId = "airtel_gifting"),
        DataPlan(id = "airtel_daily_230mb", network = NetworkProvider.AIRTEL, category = DataCategory.DAILY_WEEKLY, dataAmount = "230 MB", validity = "2 Days", planCode = "389", gsubzServiceId = "airtel_gifting"),
        DataPlan(id = "airtel_daily_1gb", network = NetworkProvider.AIRTEL, category = DataCategory.DAILY_WEEKLY, dataAmount = "1.0 GB (Socials)", validity = "1 Day", planCode = "426", gsubzServiceId = "airtel_gifting"),
        DataPlan(id = "airtel_weekly_500mb", network = NetworkProvider.AIRTEL, category = DataCategory.DAILY_WEEKLY, dataAmount = "500 MB", validity = "7 Days", planCode = "144", gsubzServiceId = "airtel_gifting"),
        DataPlan(id = "airtel_daily_2gb", network = NetworkProvider.AIRTEL, category = DataCategory.DAILY_WEEKLY, dataAmount = "2.0 GB", validity = "2 Days", planCode = "145", gsubzServiceId = "airtel_gifting"),
        DataPlan(id = "airtel_weekly_1gb", network = NetworkProvider.AIRTEL, category = DataCategory.DAILY_WEEKLY, dataAmount = "1.0 GB", validity = "7 Days", planCode = "324", gsubzServiceId = "airtel_gifting"),
        DataPlan(id = "airtel_daily_4gb", network = NetworkProvider.AIRTEL, category = DataCategory.DAILY_WEEKLY, dataAmount = "4.0 GB", validity = "2 Days", planCode = "146", gsubzServiceId = "airtel_gifting"),

        // Glo Data & SME (service_id: glo_data, glo_sme)
        DataPlan(id = "glo_cg_200mb", network = NetworkProvider.GLO, category = DataCategory.CORPORATE, dataAmount = "200 MB", validity = "14 Days", planCode = "200", gsubzServiceId = "glo_data"),
        DataPlan(id = "glo_cg_500mb", network = NetworkProvider.GLO, category = DataCategory.CORPORATE, dataAmount = "500 MB", validity = "30 Days", planCode = "500", gsubzServiceId = "glo_data"),
        DataPlan(id = "glo_sme_1gb", network = NetworkProvider.GLO, category = DataCategory.CORPORATE, dataAmount = "1.0 GB", validity = "30 Days", planCode = "1000", gsubzServiceId = "glo_data"),
        DataPlan(id = "glo_sme_2gb", network = NetworkProvider.GLO, category = DataCategory.CORPORATE, dataAmount = "2.0 GB", validity = "30 Days", planCode = "2000", gsubzServiceId = "glo_data"),
        DataPlan(id = "glo_cg_3gb_3d", network = NetworkProvider.GLO, category = DataCategory.DAILY_WEEKLY, dataAmount = "3.0 GB", validity = "3 Days", planCode = "3003", gsubzServiceId = "glo_data"),
        DataPlan(id = "glo_cg_3gb_7d", network = NetworkProvider.GLO, category = DataCategory.DAILY_WEEKLY, dataAmount = "3.0 GB", validity = "7 Days", planCode = "3007", gsubzServiceId = "glo_data"),
        DataPlan(id = "glo_sme_750mb", network = NetworkProvider.GLO, category = DataCategory.SME, dataAmount = "750 MB", validity = "1 Day", planCode = "491", gsubzServiceId = "glo_sme"),
        DataPlan(id = "glo_sme_1_5gb", network = NetworkProvider.GLO, category = DataCategory.SME, dataAmount = "1.5 GB", validity = "1 Day", planCode = "492", gsubzServiceId = "glo_sme"),
        DataPlan(id = "glo_sme_2_5gb", network = NetworkProvider.GLO, category = DataCategory.SME, dataAmount = "2.5 GB", validity = "2 Days", planCode = "493", gsubzServiceId = "glo_sme"),
        DataPlan(id = "glo_sme_10gb", network = NetworkProvider.GLO, category = DataCategory.SME, dataAmount = "10.0 GB", validity = "7 Days", planCode = "494", gsubzServiceId = "glo_sme"),

        // 9mobile (service_id: etisalat_data)
        DataPlan(id = "9mob_sme_500mb", network = NetworkProvider.NINEMOBILE, category = DataCategory.SME, dataAmount = "500 MB", validity = "30 Days", planCode = "182", gsubzServiceId = "etisalat_data"),
        DataPlan(id = "9mob_sme_1gb", network = NetworkProvider.NINEMOBILE, category = DataCategory.SME, dataAmount = "1.0 GB", validity = "30 Days", planCode = "298", gsubzServiceId = "etisalat_data"),
        DataPlan(id = "9mob_sme_1_5gb", network = NetworkProvider.NINEMOBILE, category = DataCategory.SME, dataAmount = "1.5 GB", validity = "30 Days", planCode = "300", gsubzServiceId = "etisalat_data"),
        DataPlan(id = "9mob_sme_2gb", network = NetworkProvider.NINEMOBILE, category = DataCategory.SME, dataAmount = "2.0 GB", validity = "30 Days", planCode = "299", gsubzServiceId = "etisalat_data"),
        DataPlan(id = "9mob_sme_3gb", network = NetworkProvider.NINEMOBILE, category = DataCategory.CORPORATE, dataAmount = "3.0 GB", validity = "30 Days", planCode = "303", gsubzServiceId = "etisalat_data"),
        DataPlan(id = "9mob_sme_4gb", network = NetworkProvider.NINEMOBILE, category = DataCategory.CORPORATE, dataAmount = "4.0 GB", validity = "30 Days", planCode = "347", gsubzServiceId = "etisalat_data"),
        DataPlan(id = "9mob_sme_4_5gb", network = NetworkProvider.NINEMOBILE, category = DataCategory.CORPORATE, dataAmount = "4.5 GB", validity = "30 Days", planCode = "348", gsubzServiceId = "etisalat_data"),
        DataPlan(id = "9mob_sme_5gb", network = NetworkProvider.NINEMOBILE, category = DataCategory.CORPORATE, dataAmount = "5.0 GB", validity = "30 Days", planCode = "304", gsubzServiceId = "etisalat_data")
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
