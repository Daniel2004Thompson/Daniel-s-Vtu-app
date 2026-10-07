package com.example.data.model

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.example.R
import com.example.ui.theme.Network9mobile
import com.example.ui.theme.NetworkAirtel
import com.example.ui.theme.NetworkGlo
import com.example.ui.theme.NetworkMtn

enum class NetworkProvider(
    val id: String,          // Default fallback airtime serviceID; live service_id is read from vtu_prices
    val displayName: String,
    @DrawableRes val logoRes: Int,
    val brandColor: Color,
    val textColor: Color,
    val prefixes: List<String>
) {
    MTN(
        id = "mtn",
        displayName = "MTN",
        logoRes = R.drawable.ic_provider_mtn,
        brandColor = NetworkMtn,
        textColor = Color(0xFF1E1E1E),
        prefixes = listOf(
            "0703", "0704", "0706", "0803", "0806", "0810", "0813",
            "0814", "0816", "0903", "0906", "0913", "0916"
        )
    ),
    AIRTEL(
        id = "airtel",
        displayName = "Airtel",
        logoRes = R.drawable.ic_provider_airtel,
        brandColor = NetworkAirtel,
        textColor = Color.White,
        prefixes = listOf(
            "0701", "0708", "0802", "0808", "0812", "0901",
            "0902", "0904", "0907", "0911", "0912"
        )
    ),
    GLO(
        id = "glo",
        displayName = "GLO",
        logoRes = R.drawable.ic_provider_glo,
        brandColor = NetworkGlo,
        textColor = Color.White,
        prefixes = listOf(
            "0705", "0805", "0807", "0811", "0815", "0905", "0915"
        )
    ),
    NINEMOBILE(
        id = "etisalat",
        displayName = "9mobile",
        logoRes = R.drawable.ic_provider_9mobile,
        brandColor = Network9mobile,
        textColor = Color.White,
        prefixes = listOf(
            "0809", "0817", "0818", "0908", "0909"
        )
    );

    companion object {
        fun normalizeNigerianPhone(phone: String): String {
            val clean = phone.trim().replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
            return when {
                clean.startsWith("+2340") -> "0" + clean.removePrefix("+2340")
                clean.startsWith("+234") -> "0" + clean.removePrefix("+234")
                clean.startsWith("2340") && clean.length >= 14 -> "0" + clean.removePrefix("2340")
                clean.startsWith("234") && clean.length == 13 -> "0" + clean.removePrefix("234")
                clean.length == 10 && !clean.startsWith("0") && clean.all { it.isDigit() } -> "0$clean"
                else -> clean
            }
        }

        fun detectFromPhone(phone: String): NetworkProvider? {
            val local = normalizeNigerianPhone(phone)
            if (local.length >= 4) {
                val prefix = local.substring(0, 4)
                return entries.firstOrNull { it.prefixes.contains(prefix) }
            }
            return null
        }

        fun detectProviderName(phone: String?): String {
            if (phone.isNullOrBlank()) return "Unknown"
            return detectFromPhone(phone)?.displayName ?: "Unknown"
        }

        fun detectFromTextOrPhone(text: String?, phone: String? = null): NetworkProvider? {
            if (!phone.isNullOrBlank()) {
                detectFromPhone(phone)?.let { return it }
            }
            if (!text.isNullOrBlank()) {
                val lower = text.lowercase()
                when {
                    lower.contains("airtel") -> return AIRTEL
                    lower.contains("mtn") -> return MTN
                    lower.contains("glo") -> return GLO
                    lower.contains("9mobile") || lower.contains("etisalat") || lower.contains("t2 mobile") -> return NINEMOBILE
                }
                // Check if text contains a phone number
                val digits = Regex("\\b(\\+?234|0)\\d{9,10}\\b").find(text)?.value
                if (digits != null) {
                    detectFromPhone(digits)?.let { return it }
                }
            }
            return null
        }
    }
}

enum class DataCategory(val label: String) {
    SME("SME Data"),
    CORPORATE("Corporate"),
    GIFTING("Gifting"),
    MONTHLY("Monthly"),
    DAILY_WEEKLY("Daily/Weekly")
}

data class DataPlan(
    val id: String,
    val network: NetworkProvider,
    val category: DataCategory,
    val dataAmount: String,
    val validity: String,
    val price: Double,
    val originalPrice: Double? = null,
    val planCode: String = id,
    val gsubzServiceId: String = when (network) {
        NetworkProvider.MTN -> if (category == DataCategory.SME) "mtn_sme" else "mtn_gifting"
        NetworkProvider.AIRTEL -> if (category == DataCategory.SME) "airtel_sme" else "airtel_gifting"
        NetworkProvider.GLO -> if (category == DataCategory.SME) "glo_sme" else "glo_data"
        NetworkProvider.NINEMOBILE -> "etisalat_data"
    }
)

enum class ServiceType(val title: String, val iconName: String) {
    AIRTIME("Airtime Top-up", "ic_phone"),
    DATA("Data Bundle", "ic_data"),
    ELECTRICITY("Electricity Bill", "ic_bolt"),
    CABLE_TV("Cable TV", "ic_tv"),
    EDUCATION("Education PIN", "ic_school"),
    WALLET_FUNDING("Wallet Deposit", "ic_wallet")
}

data class ElectricityProvider(
    val id: String,
    val name: String,
    val shortName: String,
    val stateCoverage: String,
    @DrawableRes val logoRes: Int,
    val brandColor: Color = Color(0xFFF59E0B),
    val minAmount: Double = 1000.0,
    val gsubzServiceId: String = "$id-electric"
)

data class CableProvider(
    val id: String,
    val name: String,
    @DrawableRes val logoRes: Int,
    val brandColor: Color = Color(0xFFE11D48),
    val tagline: String = "",
    val bouquets: List<CableBouquet>,
    val gsubzServiceId: String = id
)

data class CableBouquet(
    val id: String,
    val name: String,
    val price: Double,
    val channelsCount: Int,
    val planCode: String = id
)

data class EducationExam(
    val id: String,
    val name: String,
    val shortName: String,
    val description: String,
    val price: Double,
    @DrawableRes val logoRes: Int,
    val brandColor: Color = Color(0xFF7C3AED),
    val gsubzServiceId: String = shortName.lowercase(),
    val planCode: String = shortName.uppercase()
)

data class InAppNotification(
    val id: String,
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val transactionRef: String? = null
)

data class AirtimeNetworkPricing(
    val network: NetworkProvider,
    val serviceId: String,
    val cashbackPercent: Double
)
