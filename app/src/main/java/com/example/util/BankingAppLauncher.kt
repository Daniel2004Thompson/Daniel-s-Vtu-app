package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

data class BankingApp(
    val id: String,
    val name: String,
    val packageName: String,
    val isAvailable: Boolean = false
)

object BankingAppLauncher {

    const val OPAY_PACKAGE = "team.opay.pay"

    val NIGERIAN_BANKING_APPS = listOf(
        BankingApp("opay", "OPay", "team.opay.pay"),
        BankingApp("palmpay", "PalmPay", "com.transsnet.palmpay"),
        BankingApp("kuda", "Kuda Bank", "com.kuda.android"),
        BankingApp("moniepoint_personal", "Moniepoint", "com.moniepoint.consumer"),
        BankingApp("moniepoint_business", "Moniepoint Business", "com.moniepoint.business"),
        BankingApp("gtworld", "GTBank (GTWorld)", "com.gtbank.gtworldv1"),
        BankingApp("zenith", "Zenith Bank", "com.zenithBank.eazymobile"),
        BankingApp("access", "Access Bank", "com.accessbank.neomobile"),
        BankingApp("firstbank", "FirstMobile", "com.firstbank.firstmobile"),
        BankingApp("uba", "UBA Mobile Banking", "com.uba.verver3"),
        BankingApp("stanbic", "Stanbic IBTC", "com.stanbicibtc.mobile")
    )

    fun copyToClipboard(context: Context, text: String, label: String = "Bank Detail") {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            clipboard.setPrimaryClip(clip)
        } catch (_: Exception) {}
    }

    fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getLaunchIntentForPackage(packageName) != null
        } catch (_: Exception) {
            false
        }
    }

    fun getInstalledBanks(context: Context): List<BankingApp> {
        return NIGERIAN_BANKING_APPS.map { app ->
            app.copy(isAvailable = isAppInstalled(context, app.packageName))
        }
    }

    fun openOpayApp(
        context: Context,
        accountNumber: String,
        amount: Double? = null,
        onNotInstalled: () -> Unit = {}
    ): Boolean {
        copyToClipboard(context, accountNumber, "Account Number")
        val intent = try {
            context.packageManager.getLaunchIntentForPackage(OPAY_PACKAGE)
        } catch (_: Exception) {
            null
        }

        return if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Toast.makeText(context, "Account $accountNumber copied! Opening OPay...", Toast.LENGTH_SHORT).show()
            true
        } else {
            onNotInstalled()
            false
        }
    }

    fun openBankingApp(
        context: Context,
        packageName: String,
        accountNumber: String
    ): Boolean {
        copyToClipboard(context, accountNumber, "Account Number")
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Toast.makeText(context, "Account $accountNumber copied! Opening app...", Toast.LENGTH_SHORT).show()
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun openPlayStore(context: Context, packageName: String = OPAY_PACKAGE) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        }
    }
}
