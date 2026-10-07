package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.auth.SupabaseInstance
import com.vtu.app.wallet.observeWalletBalance
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.contentOrNull
import com.example.ui.components.BiometricPinDialog
import com.example.ui.components.FundWalletSheet
import com.example.ui.components.InAppNotificationBanner
import com.example.ui.components.TransactionReceiptDialog
import com.example.ui.screens.AirtimeScreen
import com.example.ui.screens.AppLockScreen
import com.example.ui.screens.BillsScreen
import com.example.ui.screens.ChangeEmailScreen
import com.example.ui.screens.ChangePinScreen
import com.example.ui.screens.CreatePinScreen
import com.example.ui.screens.DataScreen
import com.example.ui.screens.DevelopersForumScreen
import com.example.ui.screens.DynamicAccountScreen
import com.example.ui.screens.ForgotPasswordScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.LogoutScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.ProfileSecurityScreen
import com.example.ui.screens.SignUpScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.TransactionsScreen
import com.example.ui.theme.DanielVtuTheme
import com.example.ui.theme.VtuGreenPrimary
import com.example.ui.viewmodel.PinSetupCheckState
import com.example.ui.viewmodel.VtuViewModel
import com.example.util.NotificationHelper
import kotlinx.coroutines.launch

object NavigationRoutes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val SIGN_UP = "signup"
    const val CREATE_PIN = "create_pin"
    const val CHANGE_PIN = "change_pin"
    const val FORGOT_PASSWORD = "forgot_password?email={email}"
    const val FORGOT_PASSWORD_BASE = "forgot_password"
    const val CHANGE_EMAIL = "change_email"
    const val LOGOUT = "logout"
    const val HOME = "home"
    const val AIRTIME = "airtime"
    const val DATA = "data"
    const val BILLS = "bills/{category}"
    const val BILLS_BASE = "bills"
    const val TRANSACTIONS = "transactions"
    const val NOTIFICATIONS = "notifications"
    const val PROFILE = "profile"
    const val DYNAMIC_ACCOUNT = "dynamic_account"
    const val DEVELOPERS_FORUM = "developers_forum"
}

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (com.example.util.SecurityVault.isDynamicInstrumentationDetected()) {
            window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE
            )
        }
        enableEdgeToEdge()
        NotificationHelper.createNotificationChannel(this)

        setContent {
            DanielVtuTheme {
                MainAppContainer()
            }
        }
    }
}

@Composable
fun MainAppContainer(viewModel: VtuViewModel = viewModel()) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val walletBalance by viewModel.walletBalance.collectAsState()
    val cashbackBalance by viewModel.cashbackBalance.collectAsState()
    val airtimePrices by viewModel.airtimePrices.collectAsState()
    val biometricEnabled by viewModel.biometricEnabled.collectAsState()
    val appLockEnabled by viewModel.appLockEnabled.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val allTransactions by viewModel.allTransactions.collectAsState()
    val recentTransactions by viewModel.recentTransactions.collectAsState()
    val beneficiaries by viewModel.beneficiaries.collectAsState()
    val inAppNotifications by viewModel.inAppNotifications.collectAsState()

    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    val isAppUnlocked by viewModel.isAppUnlocked.collectAsState()
    val pendingAuthTx by viewModel.pendingTransactionForAuth.collectAsState()
    val pinSetupState by viewModel.pinSetupState.collectAsState()
    val isPinActionLoading by viewModel.isPinActionLoading.collectAsState()
    val pinDialogError by viewModel.pinDialogError.collectAsState()
    val activeReceipt by viewModel.activeReceipt.collectAsState()
    val alertBanner by viewModel.inAppAlertBanner.collectAsState()
    val showFundSheet by viewModel.showFundWalletSheet.collectAsState()
    val uiMessage by viewModel.uiMessage.collectAsState()

    var showPinFallbackForAppLock by remember { mutableStateOf(false) }
    var appLockPinError by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiMessage) {
        uiMessage?.let {
            scope.launch {
                snackbarHostState.showSnackbar(it)
                viewModel.dismissUiMessage()
            }
        }
    }

    LaunchedEffect(currentUser?.id, isLoggedIn) {
        if (!isLoggedIn) return@LaunchedEffect
        viewModel.checkHasTransactionPin()
        val supabase = SupabaseInstance.client ?: return@LaunchedEffect
        val authUser = try { supabase.auth.currentUserOrNull() } catch (_: Throwable) { null }
        val currentUserId = authUser?.id ?: currentUser?.id
        val isValidUuid = !currentUserId.isNullOrBlank() && currentUserId.length == 36 && currentUserId.count { it == '-' } == 4
        if (!isValidUuid || currentUserId.isNullOrBlank()) return@LaunchedEffect

        viewModel.refreshAirtimePrices()

        scope.launch {
            try {
                val authMetaName = try {
                    authUser?.userMetadata?.get("full_name")?.let {
                        (it as? kotlinx.serialization.json.JsonPrimitive)?.content ?: it.toString().trim('"')
                    }?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                } catch (_: Throwable) { null }

                val data = try {
                    supabase.from("users").select(Columns.list("id", "wallet_balance", "phone")) {
                        filter { eq("id", currentUserId) }
                        limit(1)
                    }.decodeSingleOrNull<JsonObject>()
                } catch (_: Throwable) { null }

                if (data != null) {
                    val initialBal = data["wallet_balance"]?.jsonPrimitive?.doubleOrNull
                        ?: data["wallet_balance"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                        ?: data["wallet_balance"]?.toString()?.trim('"')?.toDoubleOrNull()
                    if (initialBal != null) {
                        viewModel.setLiveWalletBalance(initialBal)
                    }
                    val remotePhone = data["phone"]?.jsonPrimitive?.contentOrNull
                        ?: data["phone"]?.toString()?.trim('"')
                    val resolvedName = authMetaName ?: currentUser?.fullName
                    viewModel.updateRemoteUserProfile(remotePhone, resolvedName)
                } else if (!authMetaName.isNullOrBlank()) {
                    viewModel.updateRemoteUserProfile(null, authMetaName)
                }
            } catch (_: Throwable) {}
        }

        try {
            observeWalletBalance(supabase, userId = currentUserId, scope = scope) { newBalance ->
                viewModel.setLiveWalletBalance(newBalance)
            }
        } catch (_: Throwable) {}
    }

    val initialStartDestination = remember {
        if (isLoggedIn) NavigationRoutes.HOME else NavigationRoutes.LOGIN
    }

    val navigateToHomeSafe: () -> Unit = {
        try {
            val cur = navController.currentDestination?.route
            if (cur != NavigationRoutes.HOME) {
                navController.navigate(NavigationRoutes.HOME) {
                    popUpTo(navController.graph.id) { inclusive = true }
                    launchSingleTop = true
                }
            }
        } catch (_: Throwable) {}
    }

    val navigateToLoginSafe: () -> Unit = {
        try {
            val cur = navController.currentDestination?.route
            if (cur != NavigationRoutes.LOGIN) {
                navController.navigate(NavigationRoutes.LOGIN) {
                    popUpTo(navController.graph.id) { inclusive = true }
                    launchSingleTop = true
                }
            }
        } catch (_: Throwable) {}
    }

    val navigateBackSafe: () -> Unit = {
        try {
            val popped = navController.popBackStack()
            if (!popped) {
                if (isLoggedIn) navigateToHomeSafe() else navigateToLoginSafe()
            }
        } catch (_: Throwable) {
            if (isLoggedIn) navigateToHomeSafe() else navigateToLoginSafe()
        }
    }

    val navigateToCreatePinSafe: () -> Unit = {
        try {
            val cur = navController.currentDestination?.route
            if (cur != NavigationRoutes.CREATE_PIN) {
                navController.navigate(NavigationRoutes.CREATE_PIN) {
                    launchSingleTop = true
                }
            }
        } catch (_: Throwable) {}
    }

    LaunchedEffect(isLoggedIn, pinSetupState, currentRoute) {
        val cur = navController.currentDestination?.route
        if (isLoggedIn) {
            if (cur == NavigationRoutes.LOGIN ||
                cur == NavigationRoutes.SIGN_UP
            ) {
                navigateToHomeSafe()
            }
        } else {
            if (cur != null && cur != NavigationRoutes.LOGIN && cur != NavigationRoutes.SIGN_UP && !cur.startsWith(NavigationRoutes.FORGOT_PASSWORD_BASE) && cur != NavigationRoutes.CHANGE_EMAIL && cur != NavigationRoutes.SPLASH) {
                navigateToLoginSafe()
            }
        }
    }

    var showSplash by rememberSaveable { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!isAppUnlocked && appLockEnabled && isLoggedIn) {
            // App Lock Shield is Active
            AppLockScreen(
                onUnlock = {
                    viewModel.unlockApp()
                    showPinFallbackForAppLock = false
                },
                onRequestPinDialog = {
                    appLockPinError = null
                    showPinFallbackForAppLock = true
                }
            )

            if (showPinFallbackForAppLock) {
                BiometricPinDialog(
                    pendingTransaction = null,
                    isVerifying = isPinActionLoading,
                    errorMessage = appLockPinError,
                    onClearError = { appLockPinError = null },
                    onSubmitPin = { pin ->
                        viewModel.verifyTransactionPinAsync(pin) { outcome ->
                            when (outcome) {
                                is com.example.data.remote.PinVerifyOutcome.Verified -> {
                                    appLockPinError = null
                                    viewModel.unlockApp()
                                    showPinFallbackForAppLock = false
                                }
                                is com.example.data.remote.PinVerifyOutcome.WrongPin -> {
                                    appLockPinError = "Wrong PIN"
                                }
                                is com.example.data.remote.PinVerifyOutcome.Locked -> {
                                    appLockPinError = "Too many wrong attempts. Try again in 15 minutes."
                                }
                                is com.example.data.remote.PinVerifyOutcome.Error -> {
                                    appLockPinError = if (outcome.message.contains("locked", ignoreCase = true)) {
                                        "Too many wrong attempts. Try again in 15 minutes."
                                    } else {
                                        outcome.message
                                    }
                                }
                            }
                        }
                    },
                    onDismiss = {
                        appLockPinError = null
                        showPinFallbackForAppLock = false
                    }
                )
            }
        } else {
            val showBottomBar = isLoggedIn && currentRoute in listOf(
                NavigationRoutes.HOME,
                NavigationRoutes.DEVELOPERS_FORUM,
                NavigationRoutes.TRANSACTIONS,
                NavigationRoutes.PROFILE
            )

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (showBottomBar) {
                        NavigationBar(
                            modifier = Modifier.testTag("bottom_nav_bar"),
                            tonalElevation = 6.dp
                        ) {
                            val bottomItems = listOf(
                                Triple(NavigationRoutes.HOME, "Home", Pair(Icons.Filled.Home, Icons.Outlined.Home)),
                                Triple(NavigationRoutes.DEVELOPERS_FORUM, "Dev Forum", Pair(Icons.Filled.Code, Icons.Outlined.Code)),
                                Triple(NavigationRoutes.TRANSACTIONS, "History", Pair(Icons.Filled.ReceiptLong, Icons.Outlined.ReceiptLong)),
                                Triple(NavigationRoutes.PROFILE, "Security", Pair(Icons.Filled.Security, Icons.Outlined.Security))
                            )

                            bottomItems.forEach { (route, label, icons) ->
                                val isSelected = currentRoute == route
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = {
                                        if (currentRoute != route) {
                                            if (route == NavigationRoutes.HOME) {
                                                navigateToHomeSafe()
                                            } else {
                                                try {
                                                    navController.navigate(route) {
                                                        popUpTo(NavigationRoutes.HOME) {
                                                            saveState = true
                                                        }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                } catch (_: Throwable) {
                                                    navController.navigate(route) {
                                                        launchSingleTop = true
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (isSelected) icons.first else icons.second,
                                            contentDescription = label
                                        )
                                    },
                                    label = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = VtuGreenPrimary.copy(alpha = 0.15f),
                                        selectedIconColor = VtuGreenPrimary,
                                        selectedTextColor = VtuGreenPrimary
                                    ),
                                    modifier = Modifier.testTag("nav_tab_$route")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = initialStartDestination,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        composable(NavigationRoutes.SPLASH) {
                            SplashScreen(
                                onSplashFinished = {
                                    if (isLoggedIn) navigateToHomeSafe() else navigateToLoginSafe()
                                }
                            )
                        }

                composable(NavigationRoutes.LOGIN) {
                    LoginScreen(
                        viewModel = viewModel,
                        onNavigateToSignUp = {
                            navController.navigate(NavigationRoutes.SIGN_UP) {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToForgotPassword = { prefilledEmail ->
                            val route = if (prefilledEmail.isNotBlank()) {
                                "${NavigationRoutes.FORGOT_PASSWORD_BASE}?email=$prefilledEmail"
                            } else {
                                NavigationRoutes.FORGOT_PASSWORD_BASE
                            }
                            navController.navigate(route) {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToChangeEmail = {
                            navController.navigate(NavigationRoutes.CHANGE_EMAIL) {
                                launchSingleTop = true
                            }
                        },
                        onLoginSuccess = {
                            viewModel.checkHasTransactionPin()
                            navigateToHomeSafe()
                        }
                    )
                }

                composable(NavigationRoutes.CREATE_PIN) {
                    CreatePinScreen(
                        isLoading = isPinActionLoading,
                        onCreatePin = { newPin, onError ->
                            viewModel.createTransactionPin(
                                pin = newPin,
                                onSuccess = {
                                    navigateBackSafe()
                                },
                                onError = { errMsg ->
                                    onError(errMsg)
                                }
                            )
                        },
                        onBack = { navigateBackSafe() }
                    )
                }

                composable(NavigationRoutes.CHANGE_PIN) {
                    ChangePinScreen(
                        isLoading = isPinActionLoading,
                        onChangePin = { oldPin, newPin, onResult ->
                            viewModel.changeTransactionPin(
                                oldPin = oldPin,
                                newPin = newPin,
                                onResult = onResult
                            )
                        },
                        onBack = { navigateBackSafe() }
                    )
                }

                composable(
                    route = NavigationRoutes.FORGOT_PASSWORD,
                    arguments = listOf(
                        navArgument("email") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = ""
                        }
                    )
                ) { backStackEntry ->
                    val prefilledEmail = backStackEntry.arguments?.getString("email") ?: ""
                    ForgotPasswordScreen(
                        viewModel = viewModel,
                        initialEmail = prefilledEmail,
                        onNavigateBack = {
                            navigateBackSafe()
                        },
                        onPasswordResetSuccess = {
                            if (isLoggedIn) navigateToHomeSafe() else navigateToLoginSafe()
                        }
                    )
                }

                composable(NavigationRoutes.CHANGE_EMAIL) {
                    ChangeEmailScreen(
                        viewModel = viewModel,
                        onNavigateBack = {
                            navigateBackSafe()
                        }
                    )
                }

                composable(NavigationRoutes.SIGN_UP) {
                    SignUpScreen(
                        viewModel = viewModel,
                        onNavigateBack = {
                            navigateBackSafe()
                        },
                        onSignUpSuccess = {
                            viewModel.checkHasTransactionPin()
                            navigateToHomeSafe()
                        }
                    )
                }

                composable(NavigationRoutes.LOGOUT) {
                    LogoutScreen(
                        viewModel = viewModel,
                        onNavigateBack = {
                            navigateBackSafe()
                        },
                        onLogoutSuccess = {
                            navigateToLoginSafe()
                        }
                    )
                }

                composable(NavigationRoutes.HOME) {
                    HomeScreen(
                        walletBalance = walletBalance,
                        cashbackBalance = cashbackBalance,
                        isBiometricEnabled = biometricEnabled,
                        notifications = inAppNotifications,
                        recentTransactions = recentTransactions,
                        currentUser = currentUser,
                        onNavigateToAirtime = { navController.navigate(NavigationRoutes.AIRTIME) },
                        onNavigateToData = { navController.navigate(NavigationRoutes.DATA) },
                        onNavigateToBills = { category ->
                            navController.navigate("${NavigationRoutes.BILLS_BASE}/$category")
                        },
                        onNavigateToTransactions = { navController.navigate(NavigationRoutes.TRANSACTIONS) },
                        onNavigateToNotifications = { navController.navigate(NavigationRoutes.NOTIFICATIONS) },
                        onNavigateToProfile = { navController.navigate(NavigationRoutes.PROFILE) },
                        onNavigateToDynamicAccount = { navController.navigate(NavigationRoutes.DYNAMIC_ACCOUNT) },
                        onNavigateToDevelopersForum = { navController.navigate(NavigationRoutes.DEVELOPERS_FORUM) },
                        onOpenFundWallet = { viewModel.openFundWalletSheet() },
                        onSelectTransactionReceipt = { tx -> viewModel.openReceiptForTransaction(tx) },
                        onUpdateWalletBalance = { liveBal ->
                            viewModel.setLiveWalletBalance(liveBal)
                        },
                        onUserPhoneFetched = { phone, fullName ->
                            viewModel.updateRemoteUserProfile(phone, fullName)
                        },
                        onRefreshRemoteProfile = {
                            viewModel.refreshRemoteBalance()
                        }
                    )
                }

                composable(NavigationRoutes.AIRTIME) {
                    AirtimeScreen(
                        walletBalance = walletBalance,
                        beneficiaries = beneficiaries,
                        onBack = { navigateBackSafe() },
                        onRequestPayment = { pending ->
                            viewModel.prepareTransaction(pending)
                        },
                        airtimePrices = airtimePrices,
                        onRefreshPrices = { viewModel.refreshAirtimePrices() }
                    )
                }

                composable(NavigationRoutes.DATA) {
                    DataScreen(
                        walletBalance = walletBalance,
                        onBack = { navigateBackSafe() },
                        onRequestPayment = { pending ->
                            viewModel.prepareTransaction(pending)
                        }
                    )
                }

                composable(
                    route = NavigationRoutes.BILLS,
                    arguments = listOf(navArgument("category") {
                        type = NavType.StringType
                        defaultValue = "ELECTRICITY"
                    })
                ) { backStackEntry ->
                    val category = backStackEntry.arguments?.getString("category") ?: "ELECTRICITY"
                    BillsScreen(
                        initialCategory = category,
                        walletBalance = walletBalance,
                        onBack = { navigateBackSafe() },
                        onRequestPayment = { pending ->
                            viewModel.prepareTransaction(pending)
                        }
                    )
                }

                composable(NavigationRoutes.TRANSACTIONS) {
                    TransactionsScreen(
                        transactions = allTransactions,
                        onBack = { navigateBackSafe() },
                        onSelectTransaction = { tx -> viewModel.openReceiptForTransaction(tx) },
                        onDeleteTransaction = { tx -> viewModel.deleteTransaction(tx) },
                        onClearAllTransactions = { viewModel.clearAllTransactions() }
                    )
                }

                composable(NavigationRoutes.NOTIFICATIONS) {
                    NotificationsScreen(
                        notifications = inAppNotifications,
                        onBack = { navigateBackSafe() },
                        onMarkRead = { id -> viewModel.markNotificationRead(id) },
                        onClearAll = { viewModel.clearNotifications() }
                    )
                }

                composable(NavigationRoutes.PROFILE) {
                    ProfileSecurityScreen(
                        isBiometricEnabled = biometricEnabled,
                        isAppLockEnabled = appLockEnabled,
                        isNotificationsEnabled = notificationsEnabled,
                        currentUser = currentUser,
                        userAuthToken = viewModel.currentAccessToken ?: "",
                        supabaseAnonKey = viewModel.supabaseAnonKey,
                        onToggleBiometric = { viewModel.toggleBiometric(it) },
                        onToggleAppLock = { viewModel.toggleAppLock(it) },
                        onToggleNotifications = { viewModel.toggleNotifications(it) },
                        onNavigateToCreatePin = { navController.navigate(NavigationRoutes.CREATE_PIN) },
                        onNavigateToChangePin = { navController.navigate(NavigationRoutes.CHANGE_PIN) },
                        hasTransactionPin = pinSetupState is PinSetupCheckState.HasPin,
                        onNavigateToChangeEmail = { navController.navigate(NavigationRoutes.CHANGE_EMAIL) },
                        onNavigateToResetPassword = { navController.navigate(NavigationRoutes.FORGOT_PASSWORD_BASE) },
                        onNavigateToLogout = { navController.navigate(NavigationRoutes.LOGOUT) },
                        onNavigateToDevelopersForum = { navController.navigate(NavigationRoutes.DEVELOPERS_FORUM) },
                        onPermanentAccountCreated = { acc, bank ->
                            viewModel.updateUserVirtualAccount(acc, bank)
                        },
                        onPermanentAccountReset = {
                            currentUser?.id?.let { uid -> viewModel.clearUserVirtualAccount(uid) }
                        },
                        onUserPhoneFetched = { phone, fullName ->
                            viewModel.updateRemoteUserProfile(phone, fullName)
                        },
                        onRefreshRemoteProfile = {
                            viewModel.refreshRemoteBalance()
                        },
                        onDeleteAccount = {
                            viewModel.deleteAccount(
                                onSuccess = {
                                    navigateToLoginSafe()
                                }
                            )
                        },
                        onBack = { navigateBackSafe() }
                    )
                }

                composable(NavigationRoutes.DEVELOPERS_FORUM) {
                    DevelopersForumScreen(
                        viewModel = viewModel,
                        currentUser = currentUser,
                        onBack = { navigateBackSafe() }
                    )
                }

                composable(NavigationRoutes.DYNAMIC_ACCOUNT) {
                    DynamicAccountScreen(
                        currentUser = currentUser,
                        supabaseAnonKey = viewModel.supabaseAnonKey,
                        onBack = { navigateBackSafe() },
                        onAccountDetailsUpdated = { acc, bank ->
                            viewModel.updateDynamicAccount(acc, bank)
                            viewModel.refreshRemoteBalance()
                        },
                        onRefreshBalance = {
                            viewModel.refreshRemoteBalance()
                        }
                    )
                }
            }

            // In-app real-time notification alert banner (slides in from top)
            if (alertBanner != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                ) {
                    InAppNotificationBanner(
                        notification = alertBanner,
                        onDismiss = { viewModel.dismissAlertBanner() },
                        onClick = {
                            val ref = alertBanner?.transactionRef
                            viewModel.dismissAlertBanner()
                            val tx = allTransactions.find { it.reference == ref }
                            if (tx != null) {
                                viewModel.openReceiptForTransaction(tx)
                            } else {
                                navController.navigate(NavigationRoutes.NOTIFICATIONS)
                            }
                        }
                    )
                }
            }
        }
    }
    }

    // Modal Overlays
    // 1. 4-Digit Transaction PIN authorization dialog (before every purchase and withdrawal)
    if (pendingAuthTx != null) {
        BiometricPinDialog(
            pendingTransaction = pendingAuthTx,
            isVerifying = isPinActionLoading,
            errorMessage = pinDialogError,
            onClearError = { viewModel.clearPinDialogError() },
            onSubmitPin = { pin ->
                viewModel.verifyPinAndExecutePending(pin)
            },
            onBiometricSuccess = {
                viewModel.authorizePendingWithBiometric()
            },
            startWithFingerprint = true,
            onDismiss = {
                viewModel.dismissAuthDialog()
            }
        )
    }

    // 2. Transaction Receipt dialog
    if (activeReceipt != null) {
        TransactionReceiptDialog(
            transaction = activeReceipt!!,
            onDismiss = { viewModel.dismissReceipt() },
            onDelete = { tx -> viewModel.deleteTransaction(tx) }
        )
    }

    // 3. Fund Wallet Bottom Sheet
    if (showFundSheet) {
        val isFlutterwaveLoading by viewModel.isFlutterwaveLoading.collectAsState()
        FundWalletSheet(
            onDismiss = { viewModel.closeFundWalletSheet() },
            onSubmitPendingTransfer = { amount, senderName, bankUsed, sessionRef ->
                viewModel.submitPendingBankTransfer(
                    amount = amount,
                    senderName = senderName,
                    bankUsed = bankUsed,
                    sessionRef = sessionRef
                )
            },
            onUpdateBusinessAccount = { accountNumber, bankName, accountName ->
                viewModel.setBusinessAccount(accountNumber, bankName, accountName)
            },
            onFundInstant = { amount ->
                viewModel.submitPendingBankTransfer(
                    amount = amount,
                    senderName = currentUser?.fullName ?: "",
                    bankUsed = "Bank Transfer",
                    sessionRef = ""
                )
            },
            onPayWithFlutterwave = { amount, onReady, onError ->
                viewModel.initiateFlutterwavePayment(amount, onReady, onError)
            },
            isFlutterwaveLoading = isFlutterwaveLoading,
            currentUser = currentUser,
            onPermanentAccountCreated = { acc, bank ->
                viewModel.updateUserVirtualAccount(acc, bank)
            },
            onPermanentAccountReset = {
                currentUser?.id?.let { uid -> viewModel.clearUserVirtualAccount(uid) }
            },
            onNavigateToDynamicAccount = {
                navController.navigate(NavigationRoutes.DYNAMIC_ACCOUNT)
            },
            businessAccount = viewModel.getBusinessAccount()
        )
    }

    // 4. Animated Splash Screen Overlay (fades out smoothly on launch)
    AnimatedVisibility(
        visible = showSplash,
        enter = EnterTransition.None,
        exit = fadeOut(animationSpec = tween(400))
    ) {
        SplashScreen(
            onSplashFinished = {
                showSplash = false
            }
        )
    }
}
}
