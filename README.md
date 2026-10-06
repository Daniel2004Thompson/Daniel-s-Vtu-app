# Daniel VTU — Android Application

**Daniel VTU** is a native Android application built with **Kotlin** and **Jetpack Compose (Material Design 3)** for instant Airtime top-up, SME/CG Data bundles, Electricity token vending, Cable TV subscriptions (DStv, GOtv, StarTimes), Education exam PINs, and automated wallet funding via Permanent and Dynamic Virtual Accounts.

---

## Core Features

- **Instant VTU Services**:
  - **Airtime Top-Up**: Instant recharge for MTN, Airtel, Glo, and 9mobile with automatic 4-digit phone prefix network detection.
  - **Data Bundles**: SME, Corporate Gifting, and Direct data plans across all major Nigerian mobile networks.
  - **Cable TV Subscriptions**: Smartcard/IUC bouquet recharge for **DStv**, **GOtv**, and **StarTimes**.
  - **Electricity Bills**: Prepaid meter token and postpaid bill payment across major distribution companies (IKEDC, EKEDC, AEDC, IBEDC, PHED, KEDCO, etc.).
  - **Education PINs**: WAEC, NECO, NABTEB, and JAMB result/registration PINs.
- **Automated Wallet Funding**:
  - **Permanent Dedicated Virtual Account**: NIN-verified dedicated bank account (`Create-Permanent-Account`) that credits the user's wallet automatically upon bank transfer.
  - **Dynamic Virtual Account (`Wallet Topup`)**: On-demand temporary virtual account (`Create-Dynamic-Account`) generated for a specific top-up amount.
  - **Flutterwave Checkout**: Card and bank checkout (`flutterwave-payment`) with server-side verification.
- **Real-Time Wallet Synchronization**:
  - Subscribes to `public.users.wallet_balance` via **Supabase Realtime** (`postgresChangeFlow`) so balances update live without manual polling.
- **Developer Hub & API Keys**:
  - Per-user live API key generation and rotation (`Generate-api-key`), stored locally in Android Keystore `EncryptedSharedPreferences` (`AES-256-GCM`) and synchronized with `public.api_clients`.
- **Account Security & Customer Support**:
  - 4-digit Transaction PIN & Biometric authentication (`BiometricPrompt`).
  - Direct WhatsApp chat and phone call links to customer support (`09162583986`).

---

## Architecture & Security Model

1. **Single Supabase Client (`SupabaseProvider.client`)**:
   - All authentication, Postgrest queries, Realtime subscriptions, and Edge Function calls share the single `SupabaseProvider.client` instance (`com.example.auth.SupabaseProvider`).
2. **Zero Third-Party Secrets in APK**:
   - The APK contains **only** the Supabase project URL and Supabase `anon` key.
   - All private keys (`FLUTTERWAVE_SECRET_KEY`, `FLUTTERWAVE_ENCRYPTION_KEY`, `GSUBZ_API_KEY`, `SUPABASE_SERVICE_ROLE_KEY`, webhook hashes) reside strictly in **Supabase Edge Function Secrets** on the backend and are never read into `BuildConfig` or compiled into the APK.
3. **Ktor POST Edge Function Routing**:
   - The app never calls Flutterwave or GSubz directly. Every external operation is sent via **Ktor `POST`** to `/functions/v1/<name>` (`SupabaseProvider.postEdgeFunction`) authenticated with the logged-in user's session access token (`Authorization: Bearer <access_token>`).
4. **Local Encryption & Anti-Tamper Hardening**:
   - Sensitive local preferences (session tokens, PINs, per-user developer API keys) are encrypted using Android Keystore `EncryptedSharedPreferences` (`AES256_SIV` / `AES256_GCM`).
   - Runtime instrumentation checks in `SecurityVault` detect dynamic hooking frameworks (Frida, Xposed/LSPosed, Substrate) and enforce `FLAG_SECURE`.

---

## Supabase Edge Function Endpoints

All Edge Function requests are sent via Ktor `POST` to `https://yjymxdzdhvbdjramlipg.supabase.co/functions/v1/<name>`:

| Feature | Primary Edge Function URL | Fallback URL(s) |
| :--- | :--- | :--- |
| **GSubz VTU Purchases** | `/functions/v1/Gsubz-VTU-Services` | — |
| **Permanent Virtual Account (NIN)** | `/functions/v1/Create-Permanent-Account` | `/functions/v1/Create-Virtual-Account`, `/functions/v1/create-virtual-account` |
| **Dynamic Virtual Account (`Wallet Topup`)** | `/functions/v1/Create-Dynamic-Account` | `/functions/v1/create-dynamic-account` |
| **Developer API Key Generation** | `/functions/v1/Generate-api-key` | `/functions/v1/generate-api-key` |
| **Flutterwave Checkout & Verification** | `/functions/v1/flutterwave-payment` | — |
| **Account Deletion** | `/functions/v1/delete-user-account` | `/functions/v1/delete-account`, `/functions/v1/delete_user_account`, `/functions/v1/delete-user` |

---

## Project Structure

```text
├── app/
│   ├── build.gradle.kts                 # App module Gradle configuration (buildConfig = false, no embedded secrets)
│   ├── proguard-rules.pro               # R8/ProGuard obfuscation and log-stripping rules
│   └── src/
│       ├── main/
│       │   ├── java/com/example/
│       │   │   ├── MainActivity.kt      # Entry point, navigation host, & runtime security checks
│       │   │   ├── auth/                # SupabaseProvider singleton & AuthRepository
│       │   │   ├── data/
│       │   │   │   ├── local/           # Room database (transactions, beneficiaries, user profiles)
│       │   │   │   ├── model/           # Domain & serialization models
│       │   │   │   ├── remote/          # Edge Function clients (GsubzVtuService, FlutterwaveEdgeServiceClient, VtuApiService)
│       │   │   │   └── repository/      # VtuRepository
│       │   │   ├── ui/
│       │   │   │   ├── components/      # Reusable Compose UI components & network selectors
│       │   │   │   ├── screens/         # App screens (Home, Airtime, Data, Bills, DynamicAccount, DevelopersForum, Profile, etc.)
│       │   │   │   ├── theme/           # Material 3 color scheme & typography
│       │   │   │   └── viewmodel/       # VtuViewModel
│       │   │   └── util/                # SecurityVault & SecureApiKeyStorage
│       │   ├── java/com/vtu/app/wallet/ # Realtime wallet observer, PermanentAccountFeature, & WalletNetworking
│       │   └── res/                     # Custom vector logos (MTN, Airtel, Glo, 9mobile, DStv, GOtv, StarTimes) & resources
│       └── test/                        # Robolectric & JVM unit tests
└── supabase/
    ├── README.md                        # Supabase Edge Function & SQL setup guide
    ├── functions/                       # Edge Function TypeScript sources
    └── realtime_wallet_setup.sql        # SQL setup for Realtime wallet balance & atomic debit RPC
```

---

## Building & Running Tests

- **Assemble Debug APK**:
  ```bash
  gradle assembleDebug
  ```
- **Run Unit & Robolectric Tests**:
  ```bash
  gradle :app:testDebugUnitTest
  ```
