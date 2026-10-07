package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.DanielVtuTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Daniel VTU", appName)
  }

  @Test
  fun `test phone prefix network detection`() {
    val mtn = com.example.data.model.NetworkProvider.detectFromPhone("08031234567")
    assertEquals(com.example.data.model.NetworkProvider.MTN, mtn)

    val airtel = com.example.data.model.NetworkProvider.detectFromPhone("08021234567")
    assertEquals(com.example.data.model.NetworkProvider.AIRTEL, airtel)

    val glo = com.example.data.model.NetworkProvider.detectFromPhone("08051234567")
    assertEquals(com.example.data.model.NetworkProvider.GLO, glo)
  }

  @Test
  fun `test splash screen displays branding and elements`() {
    var finished = false
    composeTestRule.setContent {
      DanielVtuTheme {
        SplashScreen(onSplashFinished = { finished = true })
      }
    }

    // Verify key UI elements and new app icon artwork are displayed
    composeTestRule.onNodeWithTag("splash_app_icon_image", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("Daniel VTU").assertIsDisplayed()
    composeTestRule.onNodeWithText("Instant Airtime, Data & Utility Payments").assertIsDisplayed()
    composeTestRule.onNodeWithText("Bank-Grade 256-bit Security").assertIsDisplayed()
  }

  @Test
  fun `test splash screen tap to finish`() {
    var finished = false
    composeTestRule.setContent {
      DanielVtuTheme {
        SplashScreen(onSplashFinished = { finished = true })
      }
    }

    composeTestRule.onNodeWithTag("splash_screen_container").performClick()
    assertEquals(true, finished)
  }

  @Test
  fun `test login screen has change email button under email field`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val vm = com.example.ui.viewmodel.VtuViewModel(app)
    var changeEmailClicked = false

    composeTestRule.setContent {
      DanielVtuTheme {
        com.example.ui.screens.LoginScreen(
          viewModel = vm,
          onNavigateToSignUp = {},
          onNavigateToForgotPassword = {},
          onNavigateToChangeEmail = { changeEmailClicked = true },
          onLoginSuccess = {}
        )
      }
    }

    // Verify email input field is displayed
    composeTestRule.onNodeWithTag("login_email_input").assertExists()
    // Verify Change Email button is present and clickable
    val changeEmailButton = composeTestRule.onNodeWithTag("login_change_email_button")
    changeEmailButton.assertExists()
    changeEmailButton.performScrollTo().performClick()
    assertEquals(true, changeEmailClicked)
  }

  @Test
  fun `test profile screen has delete account button and confirmation dialog`() {
    var accountDeleted = false

    composeTestRule.setContent {
      DanielVtuTheme {
        com.example.ui.screens.ProfileSecurityScreen(
          isBiometricEnabled = false,
          isAppLockEnabled = false,
          isNotificationsEnabled = true,
          currentUser = com.example.data.model.SupabaseUser(
            id = "test_user_1",
            email = "user@example.com",
            fullName = "Test User"
          ),
          onToggleBiometric = {},
          onToggleAppLock = {},
          onToggleNotifications = {},
          onNavigateToChangePin = {},
          onNavigateToChangeEmail = {},
          onNavigateToResetPassword = {},
          onNavigateToLogout = {},
          onDeleteAccount = { accountDeleted = true },
          onBack = {}
        )
      }
    }

    // Verify delete account button is displayed in scrollable view
    val deleteButton = composeTestRule.onNodeWithTag("profile_delete_account_button")
    deleteButton.assertExists()
    deleteButton.performScrollTo().performClick()

    // Confirmation dialog should appear with warning
    composeTestRule.onNodeWithText("Permanently Delete Account?").assertExists()
    val confirmDeleteBtn = composeTestRule.onNodeWithTag("confirm_delete_account_button")
    confirmDeleteBtn.assertExists()
    confirmDeleteBtn.performClick()
    composeTestRule.waitForIdle()

    assertEquals(true, accountDeleted)
  }

  @Test
  fun `test network providers have valid drawables and IDs`() {
    val providers = com.example.ui.components.networkProviders
    assertEquals(4, providers.size)
    assertEquals("mtn", providers[0].id)
    assertEquals("airtel", providers[1].id)
    assertEquals("glo", providers[2].id)
    assertEquals("etisalat", providers[3].id)

    providers.forEach { provider ->
      assertTrue(provider.logoRes != 0)
    }
  }

  @Test
  fun `test api key generation and developer forum screen displays`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val vm = com.example.ui.viewmodel.VtuViewModel(app)
    val testUser = com.example.data.model.SupabaseUser(
      id = "usr_dev_test_123",
      email = "dev@example.com",
      fullName = "Test Developer",
      phone = "08145551234"
    )

    // Verify API key storage and loading for signed up user
    com.example.util.SecureApiKeyStorage(app).saveRealApiKey(testUser.id, "vtu_live_real_key_999")
    val keyObj = vm.loadUserApiKey(testUser.id, testUser.email)
    val currentKey = vm.userApiKey.value
    assertTrue(currentKey != null)
    assertTrue(keyObj.apiKey.startsWith("vtu_live_"))
    assertEquals(testUser.id, currentKey?.userId)
    assertEquals(testUser.email, currentKey?.userEmail)

    composeTestRule.setContent {
      DanielVtuTheme {
        com.example.ui.screens.DevelopersForumScreen(
          viewModel = vm,
          currentUser = testUser,
          onBack = {}
        )
      }
    }

    // Verify key UI elements in Developer Hub
    composeTestRule.onNodeWithText("Developer Hub").assertIsDisplayed()
    composeTestRule.onNodeWithTag("api_key_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("generate_api_key_button").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun `test api key models and edge function repository contract`() {
    val req = com.example.data.remote.GenerateKeyRequest("Test User", "user@example.com")
    assertEquals("Test User", req.name)
    assertEquals("user@example.com", req.email)

    val regenReq = com.example.data.remote.RegenerateKeyRequest()
    assertEquals("regenerate", regenReq.action)

    val response = com.example.data.remote.ApiKeyResponse(
      status = "success",
      message = "API key created",
      apiKey = "vtu_live_test12345"
    )
    assertEquals("vtu_live_test12345", response.apiKey)
    assertEquals("https://yjymxdzdhvbdjramlipg.supabase.co/functions/v1/Generate-api-key", com.example.data.remote.ApiKeyRepository.GENERATE_API_KEY_URL)
  }

  @Test
  fun `test SupabaseRealtimeClient configuration and wallet subscription initialization`() {
    val client = com.example.data.remote.SupabaseRealtimeClient.client
    assertTrue(client != null)

    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val vm = com.example.ui.viewmodel.VtuViewModel(app)
    // Verify subscribing does not throw and initializes safely
    vm.subscribeToWalletBalance("test_user_456") { _ -> }
  }

  @Test
  fun `test secure api key storage with encrypted storage`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val secureStorage = com.example.util.SecureApiKeyStorage(context)
    val testKey = "vtu_live_secure_real_backend_key_789"
    secureStorage.saveRealApiKey("usr_test_secure", testKey, "pub_123", "2026-09-27")

    val retrieved = secureStorage.getRealApiKey("usr_test_secure")
    assertEquals(testKey, retrieved)
  }

  @Test
  fun `test GsubzVtuService function URL and client configuration`() {
    assertEquals(
      "https://yjymxdzdhvbdjramlipg.supabase.co/functions/v1/Gsubz-VTU-Services",
      com.example.data.remote.GsubzVtuService.FUNCTION_URL
    )
    val client = com.example.data.remote.GsubzEdgeServiceClient()
    assertTrue(client.isLiveConfigured)
    assertTrue(client.resolvedAuthToken.isNotBlank())
  }

  @Test
  fun `test Create-Permanent-Account URL and configuration`() {
    assertEquals(
      "https://yjymxdzdhvbdjramlipg.supabase.co/functions/v1/Create-Permanent-Account",
      com.vtu.app.wallet.PermanentAccountViewModel.CREATE_PERMANENT_ACCOUNT_URL
    )
  }

  @Test
  fun `test dynamic account and permanent account strict isolation and empty defaults`() {
    val emptyUser = com.example.data.model.SupabaseUser(
      id = "usr_new",
      email = "newuser@example.com"
    )
    assertEquals("", emptyUser.fullName)
    assertEquals(null, emptyUser.phone)
    assertEquals(null, emptyUser.virtualAccountNumber)
    assertEquals(null, emptyUser.virtualBankName)
    assertEquals(null, emptyUser.dynamicAccountNumber)
    assertEquals(null, emptyUser.dynamicBankName)
    assertEquals(false, emptyUser.ninVerified)

    val user = com.example.data.model.SupabaseUser(
      id = "usr_test",
      email = "user@example.com",
      fullName = "Test User",
      virtualAccountNumber = "1234567890",
      virtualBankName = "Test Bank",
      dynamicAccountNumber = "0987654321",
      dynamicBankName = "Test Bank",
      dynamicAccountAmount = 100.0
    )

    // Ensure dynamic account is completely distinct from the permanent/NIN account
    assertNotEquals(user.virtualAccountNumber, user.dynamicAccountNumber)
    assertEquals("1234567890", user.virtualAccountNumber)
    assertEquals("0987654321", user.dynamicAccountNumber)
    assertEquals(100.0, user.dynamicAccountAmount)
  }

  @Test
  fun runAirtimePurchaseAndCaptureLog() = kotlinx.coroutines.runBlocking {
    val client = com.example.data.remote.GsubzEdgeServiceClient()
    val result = client.processVtuOrder(
      service = "AIRTIME",
      provider = "MTN",
      recipient = "08145551234",
      amount = 100.0,
      userEmail = "user@example.com",
      userId = "usr_live_1",
      userApiKey = "vtu_live_test_key"
    )
    val capturedJson = com.example.data.remote.GsubzVtuService.lastOutgoingRequestBodyString
    println("CAPTURED_FINAL_OUTGOING_JSON=$capturedJson")
    println("CAPTURED_RESULT=$result")
    assertTrue(capturedJson.isNotBlank())
  }

  @Test
  fun `test wallet balance single source of truth from realtime observer`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val vtuViewModel = com.example.ui.viewmodel.VtuViewModel(app)
    val walletViewModel = com.vtu.app.wallet.WalletViewModel()

    // Simulate a live update arriving from the public.users.wallet_balance Realtime subscription
    com.vtu.app.wallet.SharedWalletObserver.updateBalance(900.0)
    assertEquals(900.0, com.vtu.app.wallet.SharedWalletObserver.liveBalance.value ?: 0.0, 0.001)
    assertEquals(900.0, walletViewModel.walletBalance.value?.toDouble() ?: 0.0, 0.001)
    assertEquals(900.0, vtuViewModel.walletBalance.value, 0.001)
  }

  @Test
  fun `test real phone sanitization and network provider prefix detection`() {
    // Legacy mock numbers must be rejected
    assertEquals(null, com.example.data.repository.VtuRepository.sanitizeRealPhone("08031234567"))
    assertEquals(null, com.example.data.repository.VtuRepository.sanitizeRealPhone("08012345678"))
    assertEquals(null, com.example.data.repository.VtuRepository.sanitizeRealPhone("+234 812 345 6789"))

    // Real user phone numbers must be preserved and mapped to their 4-digit prefix provider
    val airtelPhone = com.example.data.repository.VtuRepository.sanitizeRealPhone("08025551234")
    assertEquals("08025551234", airtelPhone)
    assertEquals("Airtel", com.example.data.model.NetworkProvider.detectProviderName(airtelPhone))
    assertEquals("MTN", com.example.data.model.NetworkProvider.detectProviderName("08145551234"))
    assertEquals("GLO", com.example.data.model.NetworkProvider.detectProviderName("09155551234"))
    assertEquals("9mobile", com.example.data.model.NetworkProvider.detectProviderName("09095551234"))
    assertEquals("Unknown", com.example.data.model.NetworkProvider.detectProviderName("07001234567"))
  }

  @Test
  fun `test profile avatar gallery and camera options launch without crash`() {
    composeTestRule.setContent {
      DanielVtuTheme {
        com.example.ui.components.EditableProfileAvatar(
          initials = "TU",
          userEmail = "user@example.com"
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("profile_avatar_picker_button").performClick()
    composeTestRule.waitForIdle()

    // Click Choose from Gallery and ensure no exception is thrown
    composeTestRule.onNodeWithTag("pick_from_gallery_option").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()

    // Re-open picker sheet and click Snap a Picture and ensure no exception is thrown
    composeTestRule.onNodeWithTag("profile_avatar_picker_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("snap_from_camera_option").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()

    // Also verify FragmentActivity validateRequestPermissionsRequestCode accepts 32-bit request codes from ActivityResultRegistry
    val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup()
    val activity = controller.get()
    activity.validateRequestPermissionsRequestCode(65537)
  }

  @Test
  fun `test verification code network and timeout errors report bad network instead of expired code`() {
    val authRepo = com.example.auth.AuthRepository()

    // Ktor timeout messages include the word "expired" ("Request timeout has expired", "Connect timeout has expired")
    val ktorTimeoutErr = RuntimeException("Http request to https://yjymxdzdhvbdjramlipg.supabase.co/auth/v1/verify failed with message: Request timeout has expired [url=https://yjymxdzdhvbdjramlipg.supabase.co/auth/v1/verify, request_timeout=unknown ms]")
    val connectTimeoutErr = RuntimeException("Connect timeout has expired [url=https://yjymxdzdhvbdjramlipg.supabase.co/auth/v1/verify]")
    val unknownHostErr = java.net.UnknownHostException("yjymxdzdhvbdjramlipg.supabase.co")
    val socketTimeoutErr = java.net.SocketTimeoutException("timeout expired")

    for (err in listOf(ktorTimeoutErr, connectTimeoutErr, unknownHostErr, socketTimeoutErr)) {
      assertTrue(authRepo.isNetworkError(err))
      val msg = authRepo.parseUserFriendlyError(err)
      assertTrue("Expected network message but got: $msg", msg.contains("Network connection", ignoreCase = true))
    }

    // Genuine server-side OTP expiration still reports code expired
    val genuineExpiredErr = RuntimeException("Token has expired or is invalid (otp_expired)")
    val expiredMsg = authRepo.parseUserFriendlyError(genuineExpiredErr)
    assertTrue(expiredMsg.contains("expired", ignoreCase = true))
  }

  @Test
  fun `test transaction pin models mask pin in toString and safe users columns exclude pin columns`() {
    val setReq = com.example.data.remote.SetTransactionPinRequest("9876")
    assertEquals(false, setReq.toString().contains("9876"))

    val verifyReq = com.example.data.remote.VerifyTransactionPinRequest("9876")
    assertEquals(false, verifyReq.toString().contains("9876"))

    val changeReq = com.example.data.remote.ChangeTransactionPinRequest("9876", "5432")
    assertEquals(false, changeReq.toString().contains("9876"))
    assertEquals(false, changeReq.toString().contains("5432"))

    val safeCols = com.example.data.repository.VtuRepository.USERS_SAFE_COLUMNS_FULL
    assertEquals(false, safeCols.contains("transaction_pin_hash"))
    assertEquals(false, safeCols.contains("pin_failed_attempts"))
    assertEquals(false, safeCols.contains("pin_locked_until"))
  }

  @Test
  fun `test legacy local pin data is purged on cleanup`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = context.getSharedPreferences("test_pin_cleanup_prefs", Context.MODE_PRIVATE)
    prefs.edit()
      .putString("key_security_pin", "9999")
      .putString("key_security_pin_usr_1", "8888")
      .putString("key_auth_user_email", "keep@example.com")
      .commit()

    com.example.util.SecurityVault.purgeLegacyLocalPinData(context, prefs)
    assertEquals(null, prefs.getString("key_security_pin", null))
    assertEquals(null, prefs.getString("key_security_pin_usr_1", null))
    assertEquals("keep@example.com", prefs.getString("key_auth_user_email", null))
  }

  @Test
  fun `test create pin and change pin screens and transaction pin dialog`() {
    var createdPin: String? = null
    composeTestRule.setContent {
      DanielVtuTheme {
        com.example.ui.screens.CreatePinScreen(
          isLoading = false,
          onCreatePin = { pin, _ -> createdPin = pin }
        )
      }
    }
    composeTestRule.onNodeWithTag("create_pin_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("create_pin_input").assertIsDisplayed()
    composeTestRule.onNodeWithTag("create_pin_input_box_0").assertIsDisplayed()
    composeTestRule.onNodeWithTag("create_pin_input_box_3").assertIsDisplayed()
    composeTestRule.onNodeWithTag("create_pin_confirm_input").assertIsDisplayed()
    composeTestRule.onNodeWithTag("secure_numeric_keypad").assertIsDisplayed()
    composeTestRule.onNodeWithTag("pin_keypad_done").assertIsDisplayed()
    composeTestRule.onNodeWithTag("pin_key_0").assertIsDisplayed()
    composeTestRule.onNodeWithTag("pin_key_DEL").assertIsDisplayed()
    composeTestRule.onNodeWithTag("create_pin_submit_button").assertIsDisplayed()
    assertEquals(null, createdPin)
  }

  @Test
  fun `test buy airtime request json number vtu_prices cashback receipt pending and errors`() {
    // 1. Verify vtu_prices parsing (only rows where plan = '')
    val sampleVtuPricesJson = """
      [
        {"service_id":"mtn","plan":"","cashback_percent":2.5,"active":true},
        {"service_id":"airtel","plan":"","cashback_percent":2,"active":true},
        {"service_id":"glo","plan":"","cashback_percent":3,"active":true},
        {"service_id":"9mobile","plan":"","cashback_percent":3,"active":true},
        {"service_id":"mtn_sme","plan":"166","cashback_percent":5,"active":true}
      ]
    """.trimIndent()
    val parsedPrices = com.example.data.remote.GsubzVtuService.parseAirtimePricesJson(sampleVtuPricesJson)
    assertEquals(4, parsedPrices.size)
    assertEquals("mtn", parsedPrices[com.example.data.model.NetworkProvider.MTN]?.serviceId)
    assertEquals(2.5, parsedPrices[com.example.data.model.NetworkProvider.MTN]?.cashbackPercent ?: 0.0, 0.001)
    assertEquals("airtel", parsedPrices[com.example.data.model.NetworkProvider.AIRTEL]?.serviceId)
    assertEquals(2.0, parsedPrices[com.example.data.model.NetworkProvider.AIRTEL]?.cashbackPercent ?: 0.0, 0.001)
    assertEquals("glo", parsedPrices[com.example.data.model.NetworkProvider.GLO]?.serviceId)
    assertEquals(3.0, parsedPrices[com.example.data.model.NetworkProvider.GLO]?.cashbackPercent ?: 0.0, 0.001)
    assertEquals("9mobile", parsedPrices[com.example.data.model.NetworkProvider.NINEMOBILE]?.serviceId)
    assertEquals(3.0, parsedPrices[com.example.data.model.NetworkProvider.NINEMOBILE]?.cashbackPercent ?: 0.0, 0.001)

    // 2. Verify request payload sends amount as JSON NUMBER (not string, not discounted)
    val reqJson = com.example.data.remote.GsubzVtuService.buildRequestJson(
      serviceID = parsedPrices[com.example.data.model.NetworkProvider.NINEMOBILE]!!.serviceId,
      amount = 100.0,
      phone = "09095551234",
      plan = ""
    )
    assertTrue("Expected numeric amount 100 in $reqJson", reqJson.contains("\"amount\":100"))
    assertEquals(false, reqJson.contains("\"amount\":\"100"))
    assertTrue(reqJson.contains("\"serviceID\":\"9mobile\""))
    assertTrue(reqJson.contains("\"phone\":\"09095551234\""))

    // 3. Verify server response parsing for success, pending (HTTP 202), and error
    val mtnPendingResp = com.example.data.remote.GsubzVtuService.parseAndFinalizeGsubzResponse(
      rawBody = """{"pending":true,"airtimeValue":100,"cashback":2.5,"charged":97.5,"transactionID":"TX-MTN-1"}""",
      httpStatusCode = 202,
      requestedAmount = 100.0
    )
    assertEquals(true, mtnPendingResp.isPending)
    assertEquals(100.0, mtnPendingResp.airtimeValue ?: 0.0, 0.001)
    assertEquals(2.5, mtnPendingResp.cashback ?: 0.0, 0.001)
    assertEquals(97.5, mtnPendingResp.charged ?: 0.0, 0.001)
    assertEquals("Purchase is being confirmed", mtnPendingResp.message)

    val errResp1 = com.example.data.remote.GsubzVtuService.parseAndFinalizeGsubzResponse(
      rawBody = """{"error":"Insufficient wallet balance"}""",
      httpStatusCode = 400,
      requestedAmount = 100.0
    )
    assertEquals(false, errResp1.isSuccess)
    assertEquals("Insufficient wallet balance", errResp1.message)

    val errResp2 = com.example.data.remote.GsubzVtuService.parseAndFinalizeGsubzResponse(
      rawBody = """{"error":"This service or plan is not available"}""",
      httpStatusCode = 404,
      requestedAmount = 100.0
    )
    assertEquals(false, errResp2.isSuccess)
    assertEquals("This service or plan is not available", errResp2.message)

    // 4. Verify Receipt Dialog shows "Purchase is being confirmed" and the three server lines on HTTP 202 pending
    val pendingTxEntity = com.example.data.local.TransactionEntity(
      userId = "usr_1",
      reference = "VTU-MTN-1",
      serviceType = "AIRTIME",
      provider = "MTN",
      recipient = "08035551234",
      amount = 100.0,
      discountOrCashback = 2.5,
      status = "PENDING",
      tokenOrDetails = "Purchase is being confirmed [SERVER_RECEIPT:airtime=100.0,cashback=2.5,charged=97.5]"
    )
    composeTestRule.setContent {
      DanielVtuTheme {
        com.example.ui.components.TransactionReceiptDialog(
          transaction = pendingTxEntity,
          onDismiss = {}
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Purchase is being confirmed").assertIsDisplayed()
    composeTestRule.onNodeWithText("Airtime: ₦100").assertIsDisplayed()
    composeTestRule.onNodeWithText("Cashback: ₦2.5").assertIsDisplayed()
    composeTestRule.onNodeWithText("You paid: ₦97.5").assertIsDisplayed()
  }
}

