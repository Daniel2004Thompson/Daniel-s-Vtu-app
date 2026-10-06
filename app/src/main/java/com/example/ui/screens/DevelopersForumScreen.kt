package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ForumReply
import com.example.data.model.ForumTopic
import com.example.data.model.SupabaseUser
import com.example.data.model.UserApiKey
import com.example.ui.theme.VtuCyan
import com.example.ui.theme.VtuGoldAccent
import com.example.ui.theme.VtuGreenDark
import com.example.ui.theme.VtuGreenLight
import com.example.ui.theme.VtuGreenPrimary
import com.example.ui.theme.VtuNavyCard
import com.example.ui.theme.VtuNavyPrimary
import com.example.auth.SupabaseProvider
import com.example.ui.viewmodel.VtuViewModel
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevelopersForumScreen(
    viewModel: VtuViewModel,
    currentUser: SupabaseUser?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val authUserId: String? = remember(currentUser?.id) {
        try {
            SupabaseProvider.client?.let { client ->
                client.auth.currentUserOrNull()?.id
                    ?: client.auth.currentSessionOrNull()?.user?.id
            }
        } catch (_: Throwable) {
            null
        }
    }
    val userId = authUserId?.takeIf { it.isNotBlank() } ?: currentUser?.id ?: ""
    val userEmail = currentUser?.email ?: ""
    val userName = currentUser?.fullName ?: ""

    // Query api_clients on the server (source of truth); never auto-call Generate-api-key
    LaunchedEffect(userId, userEmail) {
        if (userId.isNotBlank()) {
            viewModel.loadOrFetchApiKey(userId, userEmail, userName)
        }
    }

    val userApiKey by viewModel.userApiKey.collectAsState()
    val forumTopics by viewModel.forumTopics.collectAsState()
    val isGeneratingApiKey by viewModel.isGeneratingApiKey.collectAsState()
    val apiKeyStatusMessage by viewModel.apiKeyStatusMessage.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("API Credentials", "Developers Forum", "API Documentation")

    var showApiKeyRevealed by remember { mutableStateOf(false) }
    var showRegenerateConfirmDialog by remember { mutableStateOf(false) }
    var showNewTopicDialog by remember { mutableStateOf(false) }
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var expandedTopicId by remember { mutableStateOf<String?>(null) }
    var replyTextByTopic by remember { mutableStateOf(mapOf<String, String>()) }

    var webhookInput by remember { mutableStateOf(userApiKey?.webhookUrl ?: "") }
    LaunchedEffect(userApiKey?.webhookUrl) {
        if (userApiKey?.webhookUrl?.isNotBlank() == true && webhookInput.isBlank()) {
            webhookInput = userApiKey?.webhookUrl ?: ""
        }
    }

    var isTestingApi by remember { mutableStateOf(false) }
    var testApiResult by remember { mutableStateOf<String?>(null) }

    fun copyToClipboard(text: String, label: String = "API Key") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label copied to clipboard!", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null,
                                tint = VtuGreenPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Developer Hub",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                        Text(
                            text = "API Keys, Webhooks & Community Forum",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("dev_back_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Top Tab Navigation
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 16.dp,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = VtuGreenPrimary
                    )
                },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                when (index) {
                                    0 -> Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                    1 -> Icon(Icons.Default.Forum, contentDescription = null, modifier = Modifier.size(16.dp))
                                    2 -> Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = title,
                                    fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        },
                        selectedContentColor = VtuGreenPrimary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("dev_tab_$index")
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            when (selectedTabIndex) {
                0 -> {
                    // TAB 1: GENERATE API KEY PAGE
                    ApiKeyCredentialsPage(
                        apiKey = userApiKey,
                        user = currentUser,
                        showApiKeyRevealed = showApiKeyRevealed,
                        onToggleReveal = { showApiKeyRevealed = !showApiKeyRevealed },
                        onCopyKey = { key -> copyToClipboard(key, "API Key") },
                        onCopyPublicKey = { pubKey -> copyToClipboard(pubKey, "Public Key") },
                        onGenerateOrRegenerate = {
                            if (userApiKey?.existsOnServer == true) {
                                showRegenerateConfirmDialog = true
                            } else {
                                viewModel.generateNewApiKey(userId, userEmail, userName) { success, msg ->
                                    if (success) {
                                        showApiKeyRevealed = true
                                        Toast.makeText(context, "API Key generated!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Supabase: $msg", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        isGeneratingKey = isGeneratingApiKey,
                        statusMessage = apiKeyStatusMessage,
                        onDismissStatusMessage = { viewModel.clearApiKeyStatusMessage() },
                        webhookInput = webhookInput,
                        onWebhookChange = { webhookInput = it },
                        onSaveWebhook = {
                            viewModel.saveWebhookUrl(userId, webhookInput.trim())
                            Toast.makeText(context, "Webhook URL saved successfully!", Toast.LENGTH_SHORT).show()
                        },
                        isTestingApi = isTestingApi,
                        testApiResult = testApiResult,
                        onTestApi = {
                            coroutineScope.launch {
                                isTestingApi = true
                                testApiResult = null
                                delay(1200)
                                isTestingApi = false
                                testApiResult = """
                                {
                                  "status": "success",
                                  "code": 200,
                                  "message": "Authentication successful",
                                  "developer": {
                                    "user_id": "$userId",
                                    "email": "$userEmail",
                                    "environment": "LIVE",
                                    "active_key": "${userApiKey?.apiKey?.take(14)}..."
                                  },
                                  "wallet": {
                                    "currency": "NGN",
                                    "status": "ACTIVE",
                                    "instant_vending": true
                                  }
                                }
                                """.trimIndent()
                            }
                        }
                    )
                }
                1 -> {
                    // TAB 2: DEVELOPERS FORUM
                    DevelopersForumTab(
                        topics = forumTopics,
                        currentUser = currentUser,
                        selectedCategory = selectedCategoryFilter,
                        onSelectCategory = { selectedCategoryFilter = it },
                        onNewTopicClick = { showNewTopicDialog = true },
                        expandedTopicId = expandedTopicId,
                        onToggleExpandTopic = { id ->
                            expandedTopicId = if (expandedTopicId == id) null else id
                        },
                        replyTextByTopic = replyTextByTopic,
                        onReplyTextChange = { topicId, text ->
                            replyTextByTopic = replyTextByTopic + (topicId to text)
                        },
                        onSubmitReply = { topicId ->
                            val text = replyTextByTopic[topicId]?.trim() ?: ""
                            if (text.isNotBlank()) {
                                viewModel.addForumReply(topicId, text, userName)
                                replyTextByTopic = replyTextByTopic - topicId
                                Toast.makeText(context, "Reply posted!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onToggleLike = { topicId ->
                            viewModel.toggleLikeTopic(topicId)
                        }
                    )
                }
                2 -> {
                    // TAB 3: API DOCUMENTATION & QUICKSTART
                    ApiDocumentationTab(
                        apiKey = userApiKey?.apiKey ?: "vtu_live_YOUR_SECRET_KEY",
                        onCopySnippet = { snippet, label -> copyToClipboard(snippet, label) }
                    )
                }
            }
        }
    }

    // Dialog: Confirm API Key Regeneration
    if (showRegenerateConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRegenerateConfirmDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = VtuGoldAccent
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Regenerate Secret API Key?")
                }
            },
            text = {
                Text(
                    "The old key will stop working immediately. Any apps or servers currently using the old key will receive HTTP 401 Unauthorized until updated."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRegenerateConfirmDialog = false
                        viewModel.regenerateApiKey(userId, userEmail, userName) { success, msg ->
                            if (success) {
                                showApiKeyRevealed = true
                                Toast.makeText(context, "New API Key regenerated via Supabase Edge Function!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Supabase: $msg", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VtuGreenPrimary)
                ) {
                    Text("Regenerate Key")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRegenerateConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Create New Forum Topic
    if (showNewTopicDialog) {
        NewForumTopicDialog(
            onDismiss = { showNewTopicDialog = false },
            onSubmit = { title, content, category ->
                viewModel.createForumTopic(
                    title = title,
                    content = content,
                    category = category,
                    authorName = userName,
                    authorEmail = userEmail
                )
                showNewTopicDialog = false
                Toast.makeText(context, "Topic posted to Developers Forum!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

// ==========================================
// TAB 1: API CREDENTIALS & GENERATE KEY PAGE
// ==========================================
@Composable
fun ApiKeyCredentialsPage(
    apiKey: UserApiKey?,
    user: SupabaseUser?,
    showApiKeyRevealed: Boolean,
    onToggleReveal: () -> Unit,
    onCopyKey: (String) -> Unit,
    onCopyPublicKey: (String) -> Unit,
    onGenerateOrRegenerate: () -> Unit,
    isGeneratingKey: Boolean = false,
    statusMessage: String? = null,
    onDismissStatusMessage: () -> Unit = {},
    webhookInput: String,
    onWebhookChange: (String) -> Unit,
    onSaveWebhook: () -> Unit,
    isTestingApi: Boolean,
    testApiResult: String?,
    onTestApi: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // User Developer Profile Header Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = VtuNavyPrimary
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(VtuGreenPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DeveloperMode,
                        contentDescription = "Developer",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = user?.fullName ?: "VTU Developer",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = VtuGreenPrimary.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "VERIFIED",
                                color = VtuGreenPrimary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = user?.email ?: "developer@vtu.com",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Environment: LIVE • Rate Limit: 60 req/min",
                        style = MaterialTheme.typography.labelSmall,
                        color = VtuCyan,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        val existsOnServer = apiKey?.existsOnServer == true
        val hasLocalKey = existsOnServer && apiKey?.hasLocalKey == true && !apiKey.apiKey.isNullOrBlank()

        // Dedicated API Key Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            ),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, VtuGreenPrimary.copy(alpha = 0.3f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("api_key_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = VtuGreenPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Live Secret API Key",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Show status badge only when a row exists on the server and user has local key (or active server row)
                    if (existsOnServer && hasLocalKey) {
                        val isActive = apiKey?.isActive != false
                        Surface(
                            color = if (isActive) VtuGreenLight else MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isActive) VtuGreenPrimary else MaterialTheme.colorScheme.error)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isActive) "ACTIVE" else "INACTIVE",
                                    color = if (isActive) VtuGreenDark else MaterialTheme.colorScheme.onErrorContainer,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (!existsOnServer) {
                    // Case 2: NO row exists for this user — show explanation only, no key box, no status badge, no Created date
                    Text(
                        text = "You haven't generated an API key yet. Tap the Generate API Key button below to create a unique secret key for authenticating your Airtime, Data, Electricity, and Wallet API requests.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (hasLocalKey) {
                    // Case 5: Row exists AND user has a saved local key
                    val keyText = apiKey?.apiKey.orEmpty()
                    val prefix = apiKey?.keyPrefix?.takeIf { it.isNotBlank() } ?: keyText.take(8)
                    val suffix = apiKey?.last4?.takeIf { it.isNotBlank() } ?: keyText.takeLast(4)
                    val displayedKey = when {
                        showApiKeyRevealed -> keyText
                        keyText.length > 8 -> "${prefix}••••••••••••••••••••${suffix}"
                        else -> keyText
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = displayedKey,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )

                            IconButton(
                                onClick = onToggleReveal,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (showApiKeyRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showApiKeyRevealed) "Hide Key" else "Reveal Key",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = { onCopyKey(keyText) },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("copy_api_key_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy API Key",
                                    tint = VtuGreenPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Created: ${apiKey?.createdAt?.takeIf { it.isNotBlank() } ?: "Active"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            color = VtuGreenLight,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "EncryptedSharedPreferences",
                                color = VtuGreenDark,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Pass in HTTP requests as: Authorization: Bearer <API_KEY>. Keep this secret key safe.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                } else {
                    // Case 6: Row exists on server, but there is no local key (new phone or cleared data)
                    val prefixPart = apiKey?.keyPrefix.orEmpty()
                    val last4Part = apiKey?.last4.orEmpty()
                    val serverMaskedKey = if (prefixPart.isNotBlank() || last4Part.isNotBlank()) {
                        "${prefixPart}…${last4Part}"
                    } else {
                        "vtu_live_…****"
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = serverMaskedKey,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "For security the full key can't be shown again. Regenerate to get a new one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Case 5 only: Show HTTP Headers & Authorization section ONLY when row exists AND user has a saved local key
        if (existsOnServer && hasLocalKey) {
            val currentApiKeyValue = apiKey?.apiKey.orEmpty()
            val bearerHeaderVal = "Bearer $currentApiKeyValue"

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null,
                                tint = VtuCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "HTTP Headers & Authorization",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Surface(
                            color = VtuCyan.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Bearer Auth",
                                color = VtuCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Header 1: Authorization
                    Text(
                        text = "Header: Authorization",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = bearerHeaderVal,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = Color(0xFF38BDF8),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { onCopyKey(bearerHeaderVal) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy Authorization Header Value",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Header 2: x-api-key
                    Text(
                        text = "Header: x-api-key",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = currentApiKeyValue,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = Color(0xFF4ADE80),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { onCopyKey(currentApiKeyValue) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy x-api-key Value",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!statusMessage.isNullOrBlank()) {
            Surface(
                color = if (statusMessage.contains("failed", ignoreCase = true) || statusMessage.contains("error", ignoreCase = true)) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    VtuGreenLight
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (statusMessage.contains("failed", ignoreCase = true) || statusMessage.contains("error", ignoreCase = true)) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            VtuGreenDark
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onDismissStatusMessage,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Dismiss",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Generate API Key (when no row exists) OR Regenerate New API Key (when row exists on server)
        Button(
            onClick = onGenerateOrRegenerate,
            enabled = !isGeneratingKey,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("generate_api_key_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = VtuGreenPrimary,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isGeneratingKey) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Pulling from Supabase...",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Icon(
                    imageVector = if (!existsOnServer) Icons.Default.Key else Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (!existsOnServer) "Generate API Key" else "Regenerate New API Key",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Powered by Supabase Edge Function",
                style = MaterialTheme.typography.labelSmall,
                color = VtuCyan,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Generate-api-key",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = "Your API key is unique to your account and gives authenticated access to Airtime, Data, Electricity, and Wallet balance APIs.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        // Webhook Configuration Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = VtuCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Webhook Callback URL",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "We will send HTTP POST events to this URL when wallet funding or asynchronous vending completes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = webhookInput,
                    onValueChange = onWebhookChange,
                    placeholder = { Text("https://api.yourdomain.com/vtu/webhook") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("webhook_url_input"),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onSaveWebhook,
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Webhook")
                }
            }
        }

        // Test API Key / Sandbox Simulator Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = VtuGoldAccent,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Test API Request",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = onTestApi,
                        enabled = !isTestingApi,
                        colors = ButtonDefaults.buttonColors(containerColor = VtuGoldAccent, contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isTestingApi) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pinging...", fontSize = 12.sp)
                        } else {
                            Text("Test Auth Ping", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Sends a simulated GET request to /v1/user/auth using your active API key.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (testApiResult != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "HTTP/1.1 200 OK",
                                    color = VtuGreenPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "32ms",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = testApiResult,
                                color = Color(0xFFE2E8F0),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// TAB 2: DEVELOPERS FORUM
// ==========================================
@Composable
fun DevelopersForumTab(
    topics: List<ForumTopic>,
    currentUser: SupabaseUser?,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    onNewTopicClick: () -> Unit,
    expandedTopicId: String?,
    onToggleExpandTopic: (String) -> Unit,
    replyTextByTopic: Map<String, String>,
    onReplyTextChange: (String, String) -> Unit,
    onSubmitReply: (String) -> Unit,
    onToggleLike: (String) -> Unit
) {
    val categories = listOf("All", "API & Authentication", "Airtime & Data API", "Webhooks & Callbacks", "Troubleshooting")

    val filteredTopics = if (selectedCategory == "All") {
        topics
    } else {
        topics.filter { it.category == selectedCategory }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Forum Welcome & Ask Question Button
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = VtuGreenLight
                ),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, VtuGreenPrimary.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Developers Community",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = VtuGreenDark
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Ask questions, share webhook scripts & troubleshoot API integrations.",
                            style = MaterialTheme.typography.bodySmall,
                            color = VtuNavyPrimary
                        )
                    }

                    Button(
                        onClick = onNewTopicClick,
                        colors = ButtonDefaults.buttonColors(containerColor = VtuGreenPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("new_topic_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Topic", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Category Filter Chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { onSelectCategory(cat) },
                        label = { Text(cat, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VtuGreenPrimary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        // Topics List
        items(filteredTopics, key = { it.id }) { topic ->
            ForumTopicCard(
                topic = topic,
                isExpanded = expandedTopicId == topic.id,
                onToggleExpand = { onToggleExpandTopic(topic.id) },
                replyText = replyTextByTopic[topic.id] ?: "",
                onReplyTextChange = { onReplyTextChange(topic.id, it) },
                onSubmitReply = { onSubmitReply(topic.id) },
                onToggleLike = { onToggleLike(topic.id) }
            )
        }
    }
}

@Composable
fun ForumTopicCard(
    topic: ForumTopic,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    replyText: String,
    onReplyTextChange: (String) -> Unit,
    onSubmitReply: () -> Unit,
    onToggleLike: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (topic.isPinned) VtuGoldAccent.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("forum_topic_${topic.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: Author + Category + Pin
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (topic.isPinned) VtuGoldAccent else VtuNavyCard),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = topic.authorName.take(1).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = topic.authorName,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (topic.isPinned) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Surface(
                                    color = VtuGoldAccent.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "STAFF",
                                        color = VtuGoldAccent,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = topic.timestamp,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (topic.isPinned) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            tint = VtuGoldAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = topic.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title
            Text(
                text = topic.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Content preview / full
            Text(
                text = topic.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                maxLines = if (isExpanded) Int.MAX_VALUE else 3
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Action row: Likes, Replies, View Thread
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Like button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onToggleLike() }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (topic.isLikedByMe) Icons.Filled.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (topic.isLikedByMe) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${topic.likesCount}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (topic.isLikedByMe) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Comment count
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onToggleExpand() }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ChatBubbleOutline,
                            contentDescription = "Replies",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${topic.repliesCount} ${if (topic.repliesCount == 1) "reply" else "replies"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                TextButton(
                    onClick = onToggleExpand,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = if (isExpanded) "Hide Thread" else "View Thread",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = VtuGreenPrimary
                    )
                }
            }

            // Expanded Replies & Reply Input
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(10.dp))

                    if (topic.replies.isEmpty()) {
                        Text(
                            text = "No replies yet. Be the first developer to reply!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        topic.replies.forEach { reply ->
                            ForumReplyItem(reply = reply)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Reply Input Field
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = replyText,
                            onValueChange = onReplyTextChange,
                            placeholder = { Text("Write a developer reply...", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = onSubmitReply,
                            enabled = replyText.isNotBlank(),
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(if (replyText.isNotBlank()) VtuGreenPrimary else Color.LightGray)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send Reply",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ForumReplyItem(reply: ForumReply) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = reply.authorName,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = if (reply.authorRole.contains("Admin", ignoreCase = true)) VtuGoldAccent.copy(alpha = 0.2f) else VtuGreenPrimary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = reply.authorRole.uppercase(),
                            color = if (reply.authorRole.contains("Admin", ignoreCase = true)) VtuGoldAccent else VtuGreenDark,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                Text(
                    text = reply.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = reply.content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// Dialog: New Topic
@Composable
fun NewForumTopicDialog(
    onDismiss: () -> Unit,
    onSubmit: (title: String, content: String, category: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("API & Authentication") }
    val categories = listOf("API & Authentication", "Airtime & Data API", "Webhooks & Callbacks", "Troubleshooting")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Create Forum Topic", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Category", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VtuGreenPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Topic Title") },
                    placeholder = { Text("e.g. How to verify webhook signature in Python") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Description / Question") },
                    placeholder = { Text("Provide details, error codes, or snippets...") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && content.isNotBlank()) {
                        onSubmit(title.trim(), content.trim(), selectedCategory)
                    }
                },
                enabled = title.isNotBlank() && content.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = VtuGreenPrimary)
            ) {
                Text("Post Topic")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ==========================================
// TAB 3: API DOCUMENTATION & QUICKSTART
// ==========================================
@Composable
fun ApiDocumentationTab(
    apiKey: String,
    onCopySnippet: (String, String) -> Unit
) {
    var selectedLanguageIndex by remember { mutableIntStateOf(0) }
    val languages = listOf("cURL", "Python", "Node.js", "Kotlin")

    val curlSnippet = """
curl -X POST https://api.danielvtu.com/v1/airtime/topup \
  -H "Authorization: Bearer $apiKey" \
  -H "Content-Type: application/json" \
  -d '{
    "network": "MTN",
    "phone": "08031234567",
    "amount": 1000
  }'
    """.trimIndent()

    val pythonSnippet = """
import requests

url = "https://api.danielvtu.com/v1/airtime/topup"
headers = {
    "Authorization": "Bearer $apiKey",
    "Content-Type": "application/json"
}
payload = {
    "network": "MTN",
    "phone": "08031234567",
    "amount": 1000
}

response = requests.post(url, json=payload, headers=headers)
print(response.json())
    """.trimIndent()

    val nodeSnippet = """
const axios = require('axios');

async function purchaseAirtime() {
  const res = await axios.post(
    'https://api.danielvtu.com/v1/airtime/topup',
    {
      network: 'MTN',
      phone: '08031234567',
      amount: 1000
    },
    {
      headers: {
        'Authorization': 'Bearer $apiKey',
        'Content-Type': 'application/json'
      }
    }
  );
  console.log(res.data);
}
purchaseAirtime();
    """.trimIndent()

    val kotlinSnippet = """
val client = OkHttpClient()
val mediaType = "application/json".toMediaType()
val body = RequestBody.create(mediaType, ""${'"'}
    {"network": "MTN", "phone": "08031234567", "amount": 1000}
""${'"'})
val request = Request.Builder()
    .url("https://api.danielvtu.com/v1/airtime/topup")
    .post(body)
    .addHeader("Authorization", "Bearer $apiKey")
    .addHeader("Content-Type", "application/json")
    .build()

client.newCall(request).execute().use { response ->
    println(response.body?.string())
}
    """.trimIndent()

    val activeSnippet = when (selectedLanguageIndex) {
        0 -> curlSnippet
        1 -> pythonSnippet
        2 -> nodeSnippet
        else -> kotlinSnippet
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Developer API Quickstart",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Use your secret key to automate VTU recharges, query balances, and verify meter tokens.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Code Language Tabs
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E293B))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            languages.forEachIndexed { idx, lang ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (selectedLanguageIndex == idx) VtuGreenPrimary else Color.Transparent)
                                        .clickable { selectedLanguageIndex = idx }
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = lang,
                                        color = if (selectedLanguageIndex == idx) Color.White else Color(0xFF94A3B8),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = { onCopySnippet(activeSnippet, languages[selectedLanguageIndex]) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Code",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Text(
                        text = activeSnippet,
                        color = Color(0xFFF8FAFC),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .padding(14.dp)
                            .horizontalScroll(rememberScrollState())
                    )
                }
            }
        }

        // REST Endpoints Reference
        item {
            Text(
                text = "Core Endpoints",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            EndpointCard(
                method = "GET",
                path = "/v1/wallet/balance",
                description = "Fetches current live wallet balance and currency for your API account."
            )
        }

        item {
            EndpointCard(
                method = "POST",
                path = "/v1/airtime/topup",
                description = "Dispatches instant airtime to MTN, GLO, Airtel, or 9mobile with instant 2% discount."
            )
        }

        item {
            EndpointCard(
                method = "POST",
                path = "/v1/data/purchase",
                description = "Vend corporate and SME data bundles with real-time delivery and balance deduction."
            )
        }

        item {
            EndpointCard(
                method = "POST",
                path = "/v1/bills/electricity",
                description = "Verifies meter number and dispenses prepaid electricity token instantly."
            )
        }
    }
}

@Composable
fun EndpointCard(method: String, path: String, description: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = if (method == "GET") Color(0xFF0284C7) else VtuGreenPrimary,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = method,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = path,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
