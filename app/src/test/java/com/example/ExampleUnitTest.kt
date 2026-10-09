package com.example

import com.example.data.repository.VtuRepository
import com.vtu.app.wallet.PermanentAccountViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
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

        // Verify user's account always resolves to "Thompson Daniel/ Daniel Kalada Thompson" even if rawAccountName was "Thompson Daniel"
        assertEquals(
            "Thompson Daniel/ Daniel Kalada Thompson",
            VtuRepository.resolveAccountHolderName(
                rawAccountName = "Thompson Daniel",
                fullName = "Daniel Kalada Thompson",
                email = "danielkaladathompson@gmail.com"
            )
        )

        // Verify every other permanent account created resolves to "Thompson Daniel/ <Customer Name>"
        assertEquals(
            "Thompson Daniel/ Adebayo Johnson",
            VtuRepository.resolveAccountHolderName(
                rawAccountName = "Adebayo Johnson",
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
    fun flutterwaveWebhookTransaction_mapsToWalletFundingReceipt() {
        val rawWebhookRowJson = """
            [
              {
                "id": "9f8e7d6c-5b4a-3210-fedc-ba9876543210",
                "user_id": "550e8400-e29b-41d4-a716-446655440000",
                "reference": "FLW-TX-102-987654",
                "amount": 102.0,
                "status": "successful",
                "created_at": "2026-10-08T18:00:00Z"
              }
            ]
        """.trimIndent()

        val dtos = VtuRepository.parseJsonArrayToRemoteTransactionDtos(
            rawJson = rawWebhookRowJson,
            defaultService = "WALLET_FUNDING",
            defaultProvider = "Flutterwave",
            defaultRecipient = "danielkaladathompson@gmail.com",
            defaultCustomerName = "Daniel Kalada Thompson",
            targetUserId = "550e8400-e29b-41d4-a716-446655440000",
            targetEmail = "danielkaladathompson@gmail.com"
        )
        assertEquals(1, dtos.size)

        val entity = VtuRepository.mapRemoteTransactionDtoToEntity(
            dto = dtos.first(),
            userId = "550e8400-e29b-41d4-a716-446655440000",
            index = 0,
            userEmail = "danielkaladathompson@gmail.com",
            userFullName = "Daniel Kalada Thompson"
        )

        assertEquals("credit", entity.title)
        assertEquals("", entity.serviceType)
        assertEquals("", entity.provider)
        assertEquals("", entity.recipient)
        assertEquals("", entity.reference)
        assertEquals(null, entity.tokenOrDetails)
        assertEquals(null, entity.customerName)
        assertEquals(102.0, entity.amount, 0.001)
        assertEquals("SUCCESSFUL", entity.status)
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
    fun futureFundingAndOtherUsers_mapToTheirOwnWalletFundingReceipts() {
        // 1. Future funding by danielkaladathompson@gmail.com (e.g. ₦500.00)
        val danielFutureTx = VtuRepository.buildWebhookFundingReceiptEntity(
            userId = "550e8400-e29b-41d4-a716-446655440000",
            userEmail = "danielkaladathompson@gmail.com",
            userFullName = "Daniel Kalada Thompson",
            amount = 500.0,
            reference = "FLW-WEBHOOK-500-1"
        )
        assertTrue(VtuRepository.isWalletFundingEntity(danielFutureTx))
        assertEquals("credit", danielFutureTx.title)
        assertEquals("", danielFutureTx.serviceType)
        assertEquals("", danielFutureTx.provider)
        assertEquals("", danielFutureTx.recipient)
        assertEquals("", danielFutureTx.reference)
        assertEquals(null, danielFutureTx.tokenOrDetails)
        assertEquals(null, danielFutureTx.customerName)
        assertEquals(500.0, danielFutureTx.amount, 0.001)
        assertEquals("SUCCESSFUL", danielFutureTx.status)

        // 2. Funding made by another user for their own account
        val otherUserWebhookJson = """
            [
              {
                "id": "11223344-5566-7788-99aa-bbccddeeff00",
                "user_id": "99999999-8888-7777-6666-555555555555",
                "reference": "FLW-USER2-2500",
                "amount": 2500.0,
                "status": "successful",
                "created_at": "2026-10-09T09:00:00Z"
              }
            ]
        """.trimIndent()

        val otherUserDtos = VtuRepository.parseJsonArrayToRemoteTransactionDtos(
            rawJson = otherUserWebhookJson,
            defaultService = "WALLET_FUNDING",
            defaultProvider = "Flutterwave",
            defaultRecipient = "adebayo@example.com",
            defaultCustomerName = "Adebayo Johnson",
            targetUserId = "99999999-8888-7777-6666-555555555555",
            targetEmail = "adebayo@example.com"
        )
        assertEquals(1, otherUserDtos.size)

        val otherUserEntity = VtuRepository.mapRemoteTransactionDtoToEntity(
            dto = otherUserDtos.first(),
            userId = "99999999-8888-7777-6666-555555555555",
            index = 0,
            userEmail = "adebayo@example.com",
            userFullName = "Adebayo Johnson"
        )
        assertTrue(VtuRepository.isWalletFundingEntity(otherUserEntity))
        assertEquals("credit", otherUserEntity.title)
        assertEquals("", otherUserEntity.serviceType)
        assertEquals("", otherUserEntity.provider)
        assertEquals("", otherUserEntity.recipient)
        assertEquals("", otherUserEntity.reference)
        assertEquals(null, otherUserEntity.tokenOrDetails)
        assertEquals(null, otherUserEntity.customerName)
        assertEquals(2500.0, otherUserEntity.amount, 0.001)
        assertEquals("SUCCESSFUL", otherUserEntity.status)
    }
}

