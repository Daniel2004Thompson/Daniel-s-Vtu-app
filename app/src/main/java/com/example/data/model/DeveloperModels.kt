package com.example.data.model

data class UserApiKey(
    val userId: String,
    val userEmail: String,
    val apiKey: String,
    val publicKey: String = "",
    val createdAt: String = "",
    val status: String = "ACTIVE",
    val webhookUrl: String = "",
    val environment: String = "LIVE",
    val callsToday: Int = 0,
    val rateLimitPerMin: Int = 60,
    val keyPrefix: String = "",
    val last4: String = "",
    val isActive: Boolean = true,
    val existsOnServer: Boolean = false,
    val hasLocalKey: Boolean = false
)

data class ForumReply(
    val id: String,
    val authorName: String,
    val authorRole: String = "Verified Developer",
    val content: String,
    val timestamp: String
)

data class ForumTopic(
    val id: String,
    val authorName: String,
    val authorEmail: String,
    val title: String,
    val content: String,
    val category: String,
    val timestamp: String,
    val likesCount: Int = 0,
    val repliesCount: Int = 0,
    val isPinned: Boolean = false,
    val isLikedByMe: Boolean = false,
    val replies: List<ForumReply> = emptyList()
)
