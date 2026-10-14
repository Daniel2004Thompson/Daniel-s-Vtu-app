package com.example

import com.example.data.repository.VtuRepository
import com.vtu.app.wallet.PermanentAccountViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(
            "https://yjymxdzdhvbdjramlipg.supabase.co",
            com.example.util.SecurityVault.supabaseUrl()
        )
        assertEquals(
            "https://yjymxdzdhvbdjramlipg.supabase.co/functions/v1/swift-processor",
            com.example.util.SecurityVault.supabaseGatewayUrl()
        )
        assertEquals(
            "gateway-managed-key",
            com.example.util.SecurityVault.supabaseAnonKey()
        )
        assertTrue(!com.example.util.SecurityVault.supabaseAnonKey().startsWith("ey"))

        val gatewayClient = com.example.util.SecurityVault.createGatewayOkHttpClient(
            connectTimeoutSec = 15,
            readTimeoutSec = 15,
            writeTimeoutSec = 15
        )
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val projectUrl = com.example.util.SecurityVault.supabaseUrl()
        val gatewayUrl = com.example.util.SecurityVault.supabaseGatewayUrl()
        val placeholderKey = com.example.util.SecurityVault.supabaseAnonKey()

        // 1. Healthcheck directly against swift-processor
        val healthReq = okhttp3.Request.Builder()
            .url(gatewayUrl)
            .post("""{"ping":"healthcheck"}""".toRequestBody(mediaType))
            .build()
        gatewayClient.newCall(healthReq).execute().use { res ->
            val body = res.body?.string().orEmpty()
            println("TEST_HEALTHCHECK: code=${res.code} body=$body")
            assertEquals(200, res.code)
            assertTrue(body.contains("\"gateway\":\"active\""))
            assertTrue(body.contains("\"serviceRoleConfigured\":true"))
        }

        // 2. Auth (/auth/v1/token?grant_type=password) routed transparently via swift-processor
        val authReq = okhttp3.Request.Builder()
            .url("$projectUrl/auth/v1/token?grant_type=password")
            .addHeader("apikey", placeholderKey)
            .addHeader("Authorization", "Bearer $placeholderKey")
            .post("""{"email":"test@example.com","password":"wrong"}""".toRequestBody(mediaType))
            .build()
        gatewayClient.newCall(authReq).execute().use { res ->
            val body = res.body?.string().orEmpty()
            println("TEST_AUTH_ROUTE: code=${res.code} body=$body")
            // 400 Invalid login credentials proves swift-processor injected the server key and forwarded to GoTrue
            assertEquals(400, res.code)
            assertTrue(body.contains("invalid_credentials") || body.contains("Invalid login credentials"))
        }

        // 3. PostgREST (/rest/v1/vtu_prices) routed transparently via swift-processor
        val restReq = okhttp3.Request.Builder()
            .url("$projectUrl/rest/v1/vtu_prices?plan=eq.&active=eq.true&select=service_id,plan,cashback_percent,active")
            .addHeader("apikey", placeholderKey)
            .addHeader("Authorization", "Bearer $placeholderKey")
            .get()
            .build()
        gatewayClient.newCall(restReq).execute().use { res ->
            val body = res.body?.string().orEmpty()
            println("TEST_REST_ROUTE: code=${res.code} body=$body")
            assertEquals(200, res.code)
            assertTrue(body.trim().startsWith("["))
        }

        // 4. RPC (/rest/v1/rpc/get_my_transactions) routed transparently via swift-processor
        val rpcReq = okhttp3.Request.Builder()
            .url("$projectUrl/rest/v1/rpc/get_my_transactions")
            .addHeader("apikey", placeholderKey)
            .addHeader("Authorization", "Bearer $placeholderKey")
            .post("{}".toRequestBody(mediaType))
            .build()
        gatewayClient.newCall(rpcReq).execute().use { res ->
            val body = res.body?.string().orEmpty()
            println("TEST_RPC_ROUTE: code=${res.code} body=$body")
            assertEquals(200, res.code)
        }

        // 5. Plan lookup (/rest/v1/vtu_prices?service_id=eq.mtn_sme) and Edge Function (/functions/v1/Gsubz-VTU-Services) via swift-processor
        val plansRestReq = okhttp3.Request.Builder()
            .url("$projectUrl/rest/v1/vtu_prices?select=service_id,plan,price,cashback_percent,active&service_id=eq.mtn_sme")
            .addHeader("apikey", placeholderKey)
            .addHeader("Authorization", "Bearer $placeholderKey")
            .get()
            .build()
        gatewayClient.newCall(plansRestReq).execute().use { res ->
            val body = res.body?.string().orEmpty()
            println("TEST_PLANS_REST_ROUTE: code=${res.code} body=${body.take(180)}")
            assertEquals(200, res.code)
            assertTrue(body.trim().startsWith("["))
        }

        val edgeReq = okhttp3.Request.Builder()
            .url("$projectUrl/functions/v1/Gsubz-VTU-Services")
            .addHeader("apikey", placeholderKey)
            .addHeader("Authorization", "Bearer $placeholderKey")
            .post("""{"action":"plans","service":"gotv"}""".toRequestBody(mediaType))
            .build()
        gatewayClient.newCall(edgeReq).execute().use { res ->
            val body = res.body?.string().orEmpty()
            println("TEST_EDGE_POST_ROUTE: code=${res.code} body=$body")
            // Reaches Gsubz-VTU-Services through swift-processor (returns Gsubz-VTU-Services' own session check, never gateway error)
            assertTrue(res.code in listOf(200, 400, 401, 422))
            assertTrue(!body.contains("Missing x-target-path"))
        }

        assertEquals(4, 2 + 2)
    }

    @Test
    fun permanentAccountName_isStrictlyFromCreatePermanentAccountEdgeFunction() {
        assertEquals(
            "https://yjymxdzdhvbdjramlipg.supabase.co/functions/v1/Create-Permanent-Account",
            PermanentAccountViewModel.CREATE_PERMANENT_ACCOUNT_URL
        )

        val directJson = """
            {
              "account_number": "9624903116",
              "bank_name": "Palmpay",
              "account_name": "Thompson Daniel/ Daniel Kalada Thompson"
            }
        """.trimIndent()
        assertEquals(
            "Thompson Daniel/ Daniel Kalada Thompson",
            PermanentAccountViewModel.extractAccountNameFromEdgeResponseString(
                responseText = directJson,
                fallbackFullName = "Daniel Kalada Thompson",
                email = "danielkaladathompson@gmail.com"
            )
        )

        val customerOnlyJson = """
            {
              "account_number": "9624903116",
              "bank_name": "Palmpay",
              "account_name": "Daniel Kalada Thompson"
            }
        """.trimIndent()
        assertEquals(
            "Thompson Daniel/ Daniel Kalada Thompson",
            PermanentAccountViewModel.extractAccountNameFromEdgeResponseString(
                responseText = customerOnlyJson,
                fallbackFullName = "Daniel Kalada Thompson",
                email = "danielkaladathompson@gmail.com"
            )
        )

        val prefixOnlyJson = """
            {
              "status": "success",
              "data": {
                "account_number": "9624903116",
                "bank_name": "Palmpay",
                "narration": "Thompson Daniel"
              }
            }
        """.trimIndent()
        assertEquals(
            "Thompson Daniel/ Daniel Kalada Thompson",
            PermanentAccountViewModel.extractAccountNameFromEdgeResponseString(
                responseText = prefixOnlyJson,
                fallbackFullName = "Daniel Kalada Thompson",
                email = "danielkaladathompson@gmail.com"
            )
        )

        // Verify user's NIN-created permanent account always resolves to "Thompson Daniel/ Daniel Kalada Thompson"
        // even when profile fullname is "Daniel Thompson" from public.users.fullname
        assertEquals(
            "Thompson Daniel/ Daniel Kalada Thompson",
            VtuRepository.resolveAccountHolderName(
                rawAccountName = "Thompson Daniel",
                fullName = "Daniel Thompson",
                email = "danielkaladathompson@gmail.com"
            )
        )

        assertTrue("email" in VtuRepository.USERS_SAFE_COLUMNS_FULL)
        assertTrue("wallet_balance" in VtuRepository.USERS_SAFE_COLUMNS_FULL)
        val userRowObj = kotlinx.serialization.json.Json.parseToJsonElement(
            """{"id":"u1","email":"danielkaladathompson@gmail.com","fullname":"Daniel Thompson"}"""
        ) as kotlinx.serialization.json.JsonObject
        assertEquals("Daniel Thompson", VtuRepository.extractUsersTableFullName(userRowObj))
        assertEquals(
            "Daniel Thompson",
            VtuRepository.sanitizeFullName(
                VtuRepository.extractUsersTableFullName(userRowObj),
                "danielkaladathompson@gmail.com"
            )
        )
        assertEquals(
            "Daniel Thompson",
            VtuRepository.sanitizeFullName(
                "Daniel Kalada Thompson",
                "danielkaladathompson@gmail.com"
            )
        )

        // Verify every other registered user's profile name is from their public.users.fullname
        // while their NIN-created account name is "Thompson Daniel/ <NIN Name>"
        val otherUserRow = kotlinx.serialization.json.Json.parseToJsonElement(
            """{"id":"u2","email":"adebayo@example.com","fullname":"Adebayo Johnson"}"""
        ) as kotlinx.serialization.json.JsonObject
        assertEquals(
            "Adebayo Johnson",
            VtuRepository.sanitizeFullName(
                VtuRepository.extractUsersTableFullName(otherUserRow),
                "adebayo@example.com"
            )
        )
        assertEquals(
            "Thompson Daniel/ Adebayo Okin Johnson",
            VtuRepository.resolveAccountHolderName(
                rawAccountName = "Adebayo Okin Johnson",
                fullName = "Adebayo Johnson",
                email = "adebayo@example.com"
            )
        )
        assertEquals(
            "Thompson Daniel/ Adebayo Johnson",
            VtuRepository.resolveAccountHolderName(
                rawAccountName = "Thompson Daniel/ Adebayo Johnson",
                fullName = "Adebayo Johnson",
                email = "adebayo@example.com"
            )
        )
    }

    @Test
    fun publicUsersTransactionHistory_mapsGsubzVtuReceiptsAndExcludesHardcodedMocks() {
        assertTrue("transaction_history" in VtuRepository.USERS_SAFE_COLUMNS_FULL)
        assertTrue("transaction_history" in VtuRepository.USERS_SAFE_COLUMNS_STANDARD)
        assertTrue("transaction_history" in VtuRepository.USERS_SAFE_COLUMNS_MINIMAL)

        val rawUsersTransactionHistoryJson = """
            [
              {
                "transactionID": "GSUBZ-AIR-901234",
                "serviceID": "mtn",
                "service": "AIRTIME",
                "network": "MTN",
                "phone": "08031234567",
                "amount": 200.0,
                "charged": 196.0,
                "cashback": 4.0,
                "status": "successful",
                "created_at": "2026-10-11T12:00:00Z"
              },
              {
                "transactionID": "GSUBZ-ELC-778899",
                "serviceID": "ikeja-electric",
                "service": "ELECTRICITY",
                "provider": "IKEDC",
                "meterNumber": "45019283746",
                "amount": 5000.0,
                "token": "1234-5678-9012-3456-7890",
                "customerName": "Daniel Thompson",
                "status": "successful",
                "created_at": "2026-10-11T12:05:00Z"
              }
            ]
        """.trimIndent()

        val dtos = VtuRepository.parseJsonArrayToRemoteTransactionDtos(
            rawJson = rawUsersTransactionHistoryJson,
            targetUserId = "550e8400-e29b-41d4-a716-446655440000",
            targetEmail = "danielkaladathompson@gmail.com"
        )
        assertEquals(2, dtos.size)

        val airtimeEntity = VtuRepository.mapRemoteTransactionDtoToEntity(
            dto = dtos[0],
            userId = "550e8400-e29b-41d4-a716-446655440000",
            index = 0,
            userEmail = "danielkaladathompson@gmail.com",
            userFullName = "Daniel Thompson"
        )
        assertEquals("AIRTIME", airtimeEntity.serviceType)
        assertEquals("MTN", airtimeEntity.provider)
        assertEquals("08031234567", airtimeEntity.recipient)
        assertEquals("VTU-AIR-901234", airtimeEntity.reference)
        assertEquals(200.0, airtimeEntity.amount, 0.001)
        assertEquals(4.0, airtimeEntity.discountOrCashback, 0.001)
        assertEquals("SUCCESSFUL", airtimeEntity.status)

        val electricityEntity = VtuRepository.mapRemoteTransactionDtoToEntity(
            dto = dtos[1],
            userId = "550e8400-e29b-41d4-a716-446655440000",
            index = 1,
            userEmail = "danielkaladathompson@gmail.com",
            userFullName = "Daniel Thompson"
        )
        assertEquals("ELECTRICITY", electricityEntity.serviceType)
        assertEquals("IKEDC", electricityEntity.provider)
        assertEquals("45019283746", electricityEntity.recipient)
        assertEquals("VTU-ELC-778899", electricityEntity.reference)
        assertEquals(5000.0, electricityEntity.amount, 0.001)
        assertEquals("SUCCESSFUL", electricityEntity.status)
        assertTrue(electricityEntity.tokenOrDetails.orEmpty().contains("1234-5678-9012-3456-7890"))
    }

    @Test
    fun purchaseFailedRefunded_mapsToFailedStatus() {
        val rawFailedRefundedJson = """
            [
              {
                "id": 42,
                "user_id": "550e8400-e29b-41d4-a716-446655440000",
                "reference": "VTU-REF-991",
                "title": "MTN Data",
                "service": "DATA",
                "provider": "MTN",
                "recipient": "08031234567",
                "amount": 500.0,
                "status": "purchase_failed_refunded",
                "created_at": "2026-10-08T17:00:00Z"
              }
            ]
        """.trimIndent()

        val dtos = VtuRepository.parseJsonArrayToRemoteTransactionDtos(rawFailedRefundedJson)
        assertEquals(1, dtos.size)

        val entity = VtuRepository.mapRemoteTransactionDtoToEntity(
            dto = dtos.first(),
            userId = "550e8400-e29b-41d4-a716-446655440000",
            index = 0
        )
        assertEquals("FAILED", entity.status)
    }

    @Test
    fun mergeTransactionLists_returnsStrictlyRemoteTransactionsWithoutLocalMocks() {
        val remoteTx = com.example.data.local.TransactionEntity(
            userId = "550e8400-e29b-41d4-a716-446655440000",
            reference = "VTU-DATA-1001",
            serviceType = "DATA",
            provider = "AIRTEL",
            recipient = "08021234567",
            amount = 1000.0,
            discountOrCashback = 20.0,
            status = "SUCCESSFUL",
            timestamp = 1760000000000L,
            tokenOrDetails = "2GB Monthly Plan",
            customerName = "Daniel Thompson",
            title = "AIRTEL Data"
        )
        val localMockCredit = com.example.data.local.TransactionEntity(
            userId = "550e8400-e29b-41d4-a716-446655440000",
            reference = "",
            serviceType = "",
            provider = "",
            recipient = "",
            amount = 100.0,
            discountOrCashback = 0.0,
            status = "SUCCESSFUL",
            timestamp = 1760000005000L,
            tokenOrDetails = null,
            customerName = null,
            title = "credit"
        )

        val merged = VtuRepository.mergeTransactionLists(
            primaryList = listOf(remoteTx),
            mainDbList = listOf(localMockCredit)
        )
        assertEquals(1, merged.size)
        assertEquals("VTU-DATA-1001", merged.first().reference)
        assertEquals("DATA", merged.first().serviceType)
    }

    @Test
    fun publicUsersTransactionHistory_returnsTenLastReceiptsNewestFirst() {
        val rawJson = (1..15).joinToString(prefix = "[", postfix = "]") { idx ->
            """
            {
              "id": "tx-$idx",
              "service_type": "AIRTIME",
              "provider": "MTN",
              "recipient": "08031234567",
              "amount": ${idx * 100},
              "status": "SUCCESSFUL",
              "reference": "VTU-AIR-$idx",
              "created_at": "2026-10-${String.format("%02d", idx)}T12:00:00Z"
            }
            """.trimIndent()
        }
        val lastTen = VtuRepository.parseUsersTransactionHistoryToEntities(
            rawHistoryJson = rawJson,
            userId = "550e8400-e29b-41d4-a716-446655440000",
            userEmail = "danielkaladathompson@gmail.com",
            userFullName = "Daniel Thompson",
            maxReceipts = 10
        )
        assertEquals(10, lastTen.size)
        assertEquals("VTU-AIR-15", lastTen.first().reference)
        assertEquals("VTU-AIR-6", lastTen.last().reference)
    }

    @Test
    fun vtuPriceRow_serialization_ignoreUnknownKeys() {
        val jsonString = """
            [
                {
                    "service_id": "airtel_sme",
                    "plan": "476",
                    "price": 150.0,
                    "label": "300 MB - 2 Days",
                    "validity_days": 2,
                    "categories": ["sme"],
                    "active": true,
                    "unknown_future_col": "ignored"
                }
            ]
        """.trimIndent()

        val rows = com.example.data.remote.GsubzVtuService.vtuPriceJson
            .decodeFromString<List<com.example.data.model.VtuPriceRow>>(jsonString)
        assertEquals(1, rows.size)
        val row = rows.first()
        assertEquals("airtel_sme", row.service_id)
        assertEquals("476", row.plan)
        assertEquals(150.0, row.price, 0.001)
        assertEquals("300 MB - 2 Days", row.label)
        assertEquals(2, row.validity_days)
        assertEquals(listOf("sme"), row.categories)
        assertEquals(true, row.active)
    }

    @Test
    fun vtuPrices_allTabsAndNetworks_matchExpectedPlanCounts() = kotlinx.coroutines.runBlocking {
        // Expected plan counts table from specification:
        // Network  | SME | Corporate | Gifting | Monthly | Daily/Weekly
        // 9mobile  |  4  |     4     |    4    |    8    |      0
        // Airtel   |  5  |     5     |    5    |    3    |      7
        // Glo      |  4  |     4     |    4    |    3    |      2
        // MTN      |  5  |     4     |    5    |    9    |      5
        val expected = mapOf(
            com.example.data.model.NetworkProvider.NINEMOBILE to mapOf(
                com.example.data.model.DataCategory.SME to 4,
                com.example.data.model.DataCategory.CORPORATE to 4,
                com.example.data.model.DataCategory.GIFTING to 4,
                com.example.data.model.DataCategory.MONTHLY to 8,
                com.example.data.model.DataCategory.DAILY_WEEKLY to 0
            ),
            com.example.data.model.NetworkProvider.AIRTEL to mapOf(
                com.example.data.model.DataCategory.SME to 5,
                com.example.data.model.DataCategory.CORPORATE to 5,
                com.example.data.model.DataCategory.GIFTING to 5,
                com.example.data.model.DataCategory.MONTHLY to 3,
                com.example.data.model.DataCategory.DAILY_WEEKLY to 7
            ),
            com.example.data.model.NetworkProvider.GLO to mapOf(
                com.example.data.model.DataCategory.SME to 4,
                com.example.data.model.DataCategory.CORPORATE to 4,
                com.example.data.model.DataCategory.GIFTING to 4,
                com.example.data.model.DataCategory.MONTHLY to 3,
                com.example.data.model.DataCategory.DAILY_WEEKLY to 2
            ),
            com.example.data.model.NetworkProvider.MTN to mapOf(
                com.example.data.model.DataCategory.SME to 5,
                com.example.data.model.DataCategory.CORPORATE to 4,
                com.example.data.model.DataCategory.GIFTING to 5,
                com.example.data.model.DataCategory.MONTHLY to 9,
                com.example.data.model.DataCategory.DAILY_WEEKLY to 5
            )
        )

        for ((network, catMap) in expected) {
            for ((cat, expectedCount) in catMap) {
                val plans = com.example.data.remote.GsubzVtuService.fetchPlansForNetworkAndTab(
                    network = network,
                    category = cat
                )
                assertEquals(
                    "Network ${network.displayName} tab ${cat.label} count mismatch",
                    expectedCount,
                    plans.size
                )
                if (plans.isNotEmpty()) {
                    // Check price sorting ascending
                    assertTrue(
                        "Plans for ${network.displayName} ${cat.label} must be ordered by price ascending",
                        plans.zipWithNext().all { (a, b) -> a.price <= b.price }
                    )
                    // Check categories contains catValue
                    assertTrue(
                        "Every plan must contain category ${cat.categoryValue}",
                        plans.all { it.categories.contains(cat.categoryValue) }
                    )
                }
            }
        }
    }

    @Test
    fun purchasePayload_sendsExactRowServiceIdAndPlanIdWithoutPrice() {
        // Test that purchase request payload uses THAT ROW's service_id and its plan code as plan_id
        val testRow = com.example.data.model.VtuPriceRow(
            service_id = "airtel_sme",
            plan = "476",
            price = 150.0,
            label = "300 MB - 2 Days",
            validity_days = 2,
            categories = listOf("sme"),
            active = true
        )

        val payload = com.example.data.remote.GsubzVtuService.buildRequestJson(
            serviceID = testRow.service_id,
            amount = testRow.price,
            phone = "08021234567",
            plan = testRow.plan,
            service = "data",
            network = testRow.service_id,
            userId = "550e8400-e29b-41d4-a716-446655440000",
            userEmail = "user@example.com"
        )

        assertTrue("Payload must contain service_id", payload.contains("\"service_id\":\"airtel_sme\""))
        assertTrue("Payload must contain serviceID", payload.contains("\"serviceID\":\"airtel_sme\""))
        assertTrue("Payload must contain plan_id", payload.contains("\"plan_id\":\"476\""))
        assertTrue("Payload must contain plan", payload.contains("\"plan\":\"476\""))
        assertTrue("Payload must not contain price field", !payload.contains("\"price\":"))
    }
}


