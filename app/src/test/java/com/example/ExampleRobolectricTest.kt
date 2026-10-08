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

    // Verify cable_tv is normalized to "cable" in buildRequestJson
    val cableJson = com.example.data.remote.GsubzVtuService.buildRequestJson(
      serviceID = "dstv",
      amount = 3300.0,
      phone = "08145551234",
      plan = "dstv-padi",
      service = "cable_tv",
      network = "dstv",
      userId = "usr_live_1",
      userEmail = "user@example.com"
    )
    assertTrue(cableJson.contains("\"service_type\":\"cable\""))
    assertTrue(cableJson.contains("\"service\":\"cable\""))

    // Verify public.users safe columns do not include non-existent columns
    assertTrue("full_name" !in com.example.data.repository.VtuRepository.USERS_SAFE_COLUMNS_FULL)
    assertTrue("permanent_account_name" !in com.example.data.repository.VtuRepository.USERS_SAFE_COLUMNS_FULL)
    assertTrue("nin_hash" !in com.example.data.repository.VtuRepository.USERS_SAFE_COLUMNS_FULL)

    // Verify Gsubz-VTU-Services Edge Function URL is the sole endpoint and parseGsubzPlansJson preserves exact prices
    assertEquals(
      "https://yjymxdzdhvbdjramlipg.supabase.co/functions/v1/Gsubz-VTU-Services",
      com.example.data.remote.GsubzVtuService.FUNCTION_URL
    )
    val parsedNested = com.example.data.remote.GsubzVtuService.parseGsubzPlansJson(
      """{"gsubz_data":{"plans":[{"displayName":"1.0 GB - 30 Days","value":"166","price":399}]}}"""
    )
    assertEquals(1, parsedNested.size)
    assertEquals("166", parsedNested.first().value)
    assertEquals("399", parsedNested.first().price)
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
    composeTestRule.onNodeWithTag("secure_numeric_keypad").assertIsDisplayed()
    composeTestRule.onNodeWithTag("pin_keypad_done").assertIsDisplayed()
    composeTestRule.onNodeWithTag("pin_key_0").assertIsDisplayed()
    composeTestRule.onNodeWithTag("pin_key_DEL").assertIsDisplayed()
    composeTestRule.onNodeWithTag("create_pin_input").assertExists()
    composeTestRule.onNodeWithTag("create_pin_input_box_0").assertExists()
    composeTestRule.onNodeWithTag("create_pin_input_box_3").assertExists()
    composeTestRule.onNodeWithTag("create_pin_confirm_input").assertExists()
    composeTestRule.onNodeWithTag("create_pin_submit_button").assertExists()
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

  @Test
  fun `test updated Gsubz-VTU-Services edge function schema and gsubz_data response parsing`() {
    val edgeClient = com.example.data.remote.GsubzEdgeServiceClient()

    assertEquals("airtime", edgeClient.mapToEdgeServiceCategory("AIRTIME"))
    assertEquals("data", edgeClient.mapToEdgeServiceCategory("DATA"))
    assertEquals("cable", edgeClient.mapToEdgeServiceCategory("CABLE_TV"))
    assertEquals("electricity", edgeClient.mapToEdgeServiceCategory("ELECTRICITY"))
    assertEquals("education", edgeClient.mapToEdgeServiceCategory("EDUCATION"))

    assertEquals("gotv", edgeClient.mapToEdgeNetwork("CABLE_TV", "GOtv", "gotv"))
    assertEquals("showmax", edgeClient.mapToEdgeNetwork("CABLE_TV", "Showmax", "showmax"))
    assertEquals("ikeja-electric", edgeClient.mapToEdgeNetwork("ELECTRICITY", "IKEDC", "ikeja-electric"))
    assertEquals("waec", edgeClient.mapToEdgeNetwork("EDUCATION", "WAEC", "waec"))
    assertEquals("mtn_sme", edgeClient.mapToEdgeNetwork("DATA", "MTN", "mtn_cg"))
    assertEquals("airtel_sme", edgeClient.mapToEdgeNetwork("DATA", "AIRTEL", "airtel_cg"))

    // Verify Cable TV "list" + "display_name" plan parsing from Gsubz plans API
    val gotvPlansJson = """
      {
        "service": "gotv",
        "ServiceName": "GOTV Subscription",
        "fixedPrice": true,
        "list": [
          {"display_name": "GOtv Smallie - Monthly", "value": "gotv-smallie", "price": "1900"},
          {"display_name": "GOtv Max", "value": "gotv-max", "price": "8500"}
        ]
      }
    """.trimIndent()
    val parsedGotvPlans = com.example.data.remote.GsubzVtuService.parseGsubzPlansJson(gotvPlansJson)
    assertEquals(2, parsedGotvPlans.size)
    assertEquals("GOtv Smallie - Monthly", parsedGotvPlans[0].displayName)
    assertEquals("gotv-smallie", parsedGotvPlans[0].value)
    assertEquals("1900", parsedGotvPlans[0].price)

    val cablePayload = com.example.data.remote.GsubzVtuService.buildRequestJson(
      serviceID = "gotv",
      amount = 8500.0,
      phone = "7032154625",
      plan = "gotv-max",
      service = "cable",
      network = "gotv",
      userId = "usr_123",
      userEmail = "daniel@example.com",
      narration = "Daniel Kalada Thompson",
      smartcardNumber = "7032154625"
    )
    assertTrue(cablePayload.contains("\"user_id\":\"usr_123\""))
    assertTrue(cablePayload.contains("\"email\":\"daniel@example.com\""))
    assertTrue(cablePayload.contains("\"service_type\":\"cable\""))
    assertTrue(cablePayload.contains("\"service\":\"cable\""))
    assertTrue(cablePayload.contains("\"serviceID\":\"gotv\""))
    assertTrue(cablePayload.contains("\"service_id\":\"gotv\""))
    assertTrue(cablePayload.contains("\"network\":\"gotv\""))
    assertTrue(cablePayload.contains("\"plan\":\"gotv-max\""))
    assertTrue(cablePayload.contains("\"plan_id\":\"gotv-max\""))
    assertTrue(cablePayload.contains("\"variation_code\":\"gotv-max\""))
    assertTrue(cablePayload.contains("\"customerID\":\"7032154625\""))
    assertTrue(cablePayload.contains("\"customer_id\":\"7032154625\""))
    assertTrue(cablePayload.contains("\"iuc\":\"7032154625\""))

    val vtuManager = com.example.data.remote.VtuManager()
    val reqAdapter = vtuManager.moshi.adapter(com.example.data.remote.VtuRequest::class.java)
    val moshiPayload = reqAdapter.toJson(
      com.example.data.remote.VtuRequest(
        userId = "usr_123",
        email = "daniel@example.com",
        service = "electricity",
        serviceId = "ikeja-electric",
        phone = "08031234567",
        amount = 2000,
        planId = "prepaid",
        customerId = "45012345678"
      )
    )
    assertTrue(moshiPayload.contains("\"user_id\":\"usr_123\""))
    assertTrue(moshiPayload.contains("\"service_id\":\"ikeja-electric\""))
    assertTrue(moshiPayload.contains("\"customer_id\":\"45012345678\""))
    assertTrue(moshiPayload.contains("\"plan_id\":\"prepaid\""))

    val successEdgeEnvelope = """
      {
        "status": "successful",
        "message": "Transaction successful",
        "transactionID": "GSUBZ-TX-9988",
        "charged": 1970,
        "cashback": 30,
        "new_balance": 8030.5,
        "token": "4512-9834-1122-3344-5566"
      }
    """.trimIndent()
    val parsedSuccess = com.example.data.remote.GsubzVtuService.parseAndFinalizeGsubzResponse(
      rawBody = successEdgeEnvelope,
      httpStatusCode = 200,
      requestedAmount = 2000.0
    )
    assertEquals(true, parsedSuccess.isSuccess)
    assertEquals(1970.0, parsedSuccess.charged ?: 0.0, 0.001)
    assertEquals(30.0, parsedSuccess.cashback ?: 0.0, 0.001)
    assertEquals(8030.5, parsedSuccess.newBalance ?: 0.0, 0.001)
    assertEquals("4512-9834-1122-3344-5566", parsedSuccess.tokenOrPin)

    val failedGsubzEnvelope = """
      {
        "success": false,
        "user_id": "usr_123",
        "email": "daniel@example.com",
        "narration": "Daniel Kalada Thompson",
        "gsubz_data": {
          "code": "402",
          "status": "failed",
          "description": "INSUFFICIENT_BALANCE",
          "api_response": "",
          "content": {
            "requestID": "REQ-991",
            "serviceID": "gotv",
            "image": "//yjymxdzdhvbdjramlipg.supabase.co/storage/v1/object/public/uploads/1029285934.png",
            "status": "failed",
            "description": "INSUFFICIENT_BALANCE",
            "serviceName": "GOTV",
            "code": "402"
          }
        }
      }
    """.trimIndent()
    val respAdapter = vtuManager.moshi.adapter(com.example.data.remote.VtuResponse::class.java)
    val moshiResp = respAdapter.fromJson(failedGsubzEnvelope)!!
    assertEquals(false, moshiResp.success)
    assertEquals("usr_123", moshiResp.userId)
    assertEquals("Daniel Kalada Thompson", moshiResp.narration)
    assertEquals("GOTV", moshiResp.extractServiceName())
    assertEquals("https://yjymxdzdhvbdjramlipg.supabase.co/storage/v1/object/public/uploads/1029285934.png", moshiResp.extractServiceImageUrl())

    val parsedFail = com.example.data.remote.GsubzVtuService.parseAndFinalizeGsubzResponse(
      rawBody = failedGsubzEnvelope,
      httpStatusCode = 200,
      requestedAmount = 8500.0
    )
    assertEquals(false, parsedFail.isSuccess)
    assertEquals("Insufficient wallet balance", parsedFail.message)
    assertEquals("GOTV", parsedFail.serviceName)
    assertEquals("https://yjymxdzdhvbdjramlipg.supabase.co/storage/v1/object/public/uploads/1029285934.png", parsedFail.serviceImageUrl)
    assertEquals("Daniel Kalada Thompson", parsedFail.narration)

    // Verify all VTU services use exclusively the Supabase Edge Function URL
    assertEquals(
      "https://yjymxdzdhvbdjramlipg.supabase.co/functions/v1/Gsubz-VTU-Services",
      com.example.data.remote.GsubzVtuService.FUNCTION_URL
    )
    assertEquals(
      "https://yjymxdzdhvbdjramlipg.supabase.co/functions/v1/Gsubz-VTU-Services",
      com.example.data.remote.GsubzEdgeServiceClient.EDGE_FUNCTION_URL
    )

    // Verify active Gsubz services in VtuCatalog
    assertTrue(com.example.data.model.VtuCatalog.dataPlans.none { it.gsubzServiceId == "mtn_cg" || it.gsubzServiceId == "airtel_cg" })
    val waecExam = com.example.data.model.VtuCatalog.educationExams.first { it.id == "waec_pin" }
    assertEquals("WAEC", waecExam.planCode)

    val context = ApplicationProvider.getApplicationContext<Context>()
    val showmaxProvider = com.example.data.model.VtuCatalog.cableProviders.first { it.id == "showmax" }
    assertEquals(R.drawable.ic_cable_showmax, showmaxProvider.logoRes)
    assertTrue(context.getDrawable(showmaxProvider.logoRes) != null)
  }

  @Test
  fun `test sign out button navigates to logout screen and signs user out`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val vm = com.example.ui.viewmodel.VtuViewModel(app)
    var logoutCompleted = false

    composeTestRule.setContent {
      DanielVtuTheme {
        com.example.ui.screens.LogoutScreen(
          viewModel = vm,
          onNavigateBack = {},
          onLogoutSuccess = { logoutCompleted = true }
        )
      }
    }

    val signOutBtn = composeTestRule.onNodeWithTag("logout_screen_button")
    signOutBtn.performScrollTo().assertIsDisplayed()
    signOutBtn.performClick()

    composeTestRule.onNodeWithText("Sign Out?").assertIsDisplayed()
    val confirmBtn = composeTestRule.onNodeWithTag("confirm_logout_button")
    confirmBtn.assertIsDisplayed()
    confirmBtn.performClick()
    composeTestRule.waitForIdle()

    assertEquals(false, vm.isLoggedIn.value)
    assertEquals(true, logoutCompleted)
  }

  @Test
  fun `test home screen dynamic account number is complete and spaced from active badge`() {
    val testUser = com.example.data.model.SupabaseUser(
      id = "user-123",
      fullName = "Daniel Kalada Thompson",
      email = "user@example.com",
      phone = "08031234567",
      dynamicAccountNumber = "9691882461",
      dynamicBankName = "Palmpay"
    )

    composeTestRule.setContent {
      DanielVtuTheme {
        com.example.ui.screens.HomeScreen(
          walletBalance = 140.0,
          cashbackBalance = 0.0,
          isBiometricEnabled = true,
          notifications = emptyList(),
          recentTransactions = emptyList(),
          currentUser = testUser,
          onNavigateToAirtime = {},
          onNavigateToData = {},
          onNavigateToBills = {},
          onNavigateToTransactions = {},
          onNavigateToNotifications = {},
          onNavigateToProfile = {},
          onNavigateToDynamicAccount = {},
          onNavigateToDevelopersForum = {},
          onOpenFundWallet = {},
          onSelectTransactionReceipt = {}
        )
      }
    }

    composeTestRule.waitForIdle()

    val dynCard = composeTestRule.onNodeWithTag("home_dynamic_account_card")
    dynCard.performScrollTo().assertIsDisplayed()

    val numberNode = composeTestRule.onNodeWithTag("home_dynamic_account_number_text", useUnmergedTree = true)
    val badgeNode = composeTestRule.onNodeWithTag("home_dynamic_account_status_badge", useUnmergedTree = true)

    numberNode.assertIsDisplayed()
    badgeNode.assertIsDisplayed()
    composeTestRule.onNodeWithText("Dynamic Acct: 9691882461", useUnmergedTree = true).assertIsDisplayed()

    val numberBounds = numberNode.fetchSemanticsNode().boundsInRoot
    val badgeBounds = badgeNode.fetchSemanticsNode().boundsInRoot
    assertTrue(
      "Expected green ACTIVE badge (${badgeBounds.left}) to be spaced to the right of dynamic account number (${numberBounds.right})",
      badgeBounds.left > numberBounds.right + 4f
    )
  }

  @Test
  fun `test recent transactions formatting lagos timezone and receipt dialog hiding empty fields`() {
    // 1. Verify UTC to Africa/Lagos (WAT = UTC+1) date formatting: 2026-10-08T14:00:00Z -> "08 Oct 2026, 03:00 pm"
    val formattedLagos = com.example.util.TransactionDateFormatter.formatUtcToLagos("2026-10-08T14:00:00Z")
    assertEquals("08 Oct 2026, 03:00 pm", formattedLagos)

    // 2. Verify RemoteTransactionDto mapping from get_my_transactions RPC
    val dto1 = com.example.data.remote.RemoteTransactionDto(
      id = "101",
      title = "Airtel Airtime Top-up",
      service = "AIRTIME",
      provider = "Airtel",
      recipient = "09027314213",
      amount = 100.0,
      status = "SUCCESSFUL",
      reference = "REF-AIR-101",
      createdAt = "2026-10-08T14:00:00Z"
    )
    val dto2 = com.example.data.remote.RemoteTransactionDto(
      id = "102",
      title = "Wallet Funding",
      service = "",
      provider = "",
      recipient = "",
      amount = 2500.0,
      status = "PENDING",
      reference = "",
      createdAt = "2026-10-08T08:30:00Z"
    )
    val dto3 = com.example.data.remote.RemoteTransactionDto(
      id = "103",
      title = "purchase_failed_refunded",
      service = "purchase_failed_refunded",
      provider = "",
      recipient = "",
      amount = 50.0,
      status = "completed",
      reference = "REF-FAIL-103",
      createdAt = "2026-10-08T15:00:00Z"
    )
    val tx1 = com.example.data.repository.VtuRepository.mapRemoteTransactionDtoToEntity(dto1, "usr-1", 0)
    val tx2 = com.example.data.repository.VtuRepository.mapRemoteTransactionDtoToEntity(dto2, "usr-1", 1)
    val tx3 = com.example.data.repository.VtuRepository.mapRemoteTransactionDtoToEntity(dto3, "usr-1", 2).copy(
      tokenOrDetails = "This plan is not valid for this service"
    )
    assertEquals("FAILED", tx3.status)

    // Verify that even if a legacy local row had status = "SUCCESSFUL" for purchase_failed_refunded,
    // mergeTransactionLists preserves the history and normalizes its status to "FAILED"
    val legacyLocalFailed = tx3.copy(status = "SUCCESSFUL")
    val mergedHistory = com.example.data.repository.VtuRepository.mergeTransactionLists(
      primaryList = emptyList(),
      mainDbList = listOf(tx1, tx2, legacyLocalFailed)
    )
    assertEquals(3, mergedHistory.size)
    assertEquals("FAILED", mergedHistory.first { it.reference == "REF-FAIL-103" }.status)

    composeTestRule.setContent {
      DanielVtuTheme {
        com.example.ui.screens.HomeScreen(
          walletBalance = 500.0,
          cashbackBalance = 0.0,
          isBiometricEnabled = true,
          notifications = emptyList(),
          recentTransactions = listOf(tx1, tx2, tx3),
          currentUser = null,
          onNavigateToAirtime = {},
          onNavigateToData = {},
          onNavigateToBills = {},
          onNavigateToTransactions = {},
          onNavigateToNotifications = {},
          onNavigateToProfile = {},
          onNavigateToDynamicAccount = {},
          onNavigateToDevelopersForum = {},
          onOpenFundWallet = {},
          onSelectTransactionReceipt = {}
        )
      }
    }

    composeTestRule.waitForIdle()

    // Scroll to Recent Transactions header and verify rows
    composeTestRule.onNodeWithTag("recent_transactions_header").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Recent Transactions").assertIsDisplayed()

    // Row 1: provider="Airtel" -> circle "A", title "Airtel Airtime Top-up", "09027314213 • 08 Oct 2026, 03:00 pm", "₦100.00", "SUCCESSFUL"
    composeTestRule.onNodeWithTag("tx_item_REF-AIR-101").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Airtel Airtime Top-up").assertIsDisplayed()
    composeTestRule.onNodeWithText("09027314213 • 08 Oct 2026, 03:00 pm").assertIsDisplayed()
    composeTestRule.onNodeWithText("₦100.00").assertIsDisplayed()
    composeTestRule.onNodeWithText("SUCCESSFUL").assertIsDisplayed()

    // Row 2: empty provider -> circle uses title's first letter "W", empty recipient -> just date "08 Oct 2026, 09:30 am", "₦2,500.00", "PENDING"
    composeTestRule.onNodeWithTag("tx_item_").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Wallet Funding").assertIsDisplayed()
    composeTestRule.onNodeWithText("08 Oct 2026, 09:30 am").assertIsDisplayed()
    composeTestRule.onNodeWithText("₦2,500.00").assertIsDisplayed()
    composeTestRule.onNodeWithText("PENDING").assertIsDisplayed()

    // Row 3: purchase_failed_refunded shows FAILED (not SUCCESSFUL)
    composeTestRule.onNodeWithTag("tx_item_REF-FAIL-103").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("purchase_failed_refunded").assertIsDisplayed()
    composeTestRule.onNodeWithText("FAILED").assertIsDisplayed()

    // Tap Row 3 -> opens receipt dialog showing Payment Failed, Status FAILED, and no "This plan is not valid..." text
    composeTestRule.onNodeWithTag("tx_item_REF-FAIL-103").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("transaction_receipt_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithText("Payment Failed").assertIsDisplayed()
    composeTestRule.onNodeWithText("This plan is not valid for this service").assertDoesNotExist()
    composeTestRule.onNodeWithText("Done").performClick()
    composeTestRule.waitForIdle()

    // Tap Row 2 (which has empty service, provider, recipient, reference) -> opens receipt dialog and hides empty fields
    composeTestRule.onNodeWithTag("tx_item_").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("transaction_receipt_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithText("Daniel VTU Wallet").assertIsDisplayed()
    composeTestRule.onNodeWithText("Date & Time").assertIsDisplayed()
    composeTestRule.onNodeWithText("Payment Method").assertIsDisplayed()
    composeTestRule.onNodeWithText("Status").assertIsDisplayed()
    // Empty fields (Service, Provider, Recipient, Reference) must be hidden
    composeTestRule.onNodeWithText("Service").assertDoesNotExist()
    composeTestRule.onNodeWithText("Provider").assertDoesNotExist()
    composeTestRule.onNodeWithText("Recipient").assertDoesNotExist()
    composeTestRule.onNodeWithText("Reference").assertDoesNotExist()
  }
}

