# Daniel VTU Web

Web companion and full conversion of the **Daniel VTU** Android application built with React, TypeScript, Vite, and Tailwind CSS.

## Features
- **2-Step Secure Auth**: Password + 6-digit email OTP sign-in, email OTP sign-up, password reset OTP, and email change verification.
- **Instant VTU Vending**:
  - **Airtime** (MTN, Airtel, GLO, 9mobile) with Nigerian prefix auto-detection and live cashback rates from `public.vtu_prices`.
  - **Data Bundles** (SME, Corporate, Gifting, Monthly, Daily/Weekly) loaded live from `public.vtu_prices`.
  - **Bills & Utilities**: Electricity tokens (10 Nigerian DisCos, Prepaid/Postpaid), Cable TV bouquets (DStv, GOtv, StarTimes, Showmax), and Education PINs (WAEC, NECO, NABTEB, JAMB).
- **Wallet & Funding**:
  - Live wallet balance + transaction history sync (`public.users` & `get_my_transactions`).
  - **Flutterwave Checkout** initialization + automatic redirect verification.
  - **Dynamic Virtual Account** instant generation.
  - **Permanent Virtual Account** via NIN KYC verification.
  - **Wallet Debit / Withdraw** via `debit_wallet` RPC.
- **Security**: 4-digit server-verified Transaction PIN (`set_transaction_pin`, `verify_transaction_pin`, `change_transaction_pin`), optional Idle Auto-Lock, and Account Deletion (`delete_user`).
- **Developer Hub**: Live Secret API Key generation/rotation (`Generate-api-key` & `api_clients`), Webhook URL configuration, API Quickstart snippets, and Community Forum.
- **Receipts**: Interactive printable & shareable transaction receipts with Token/PIN copy and Airtime cashback breakdown.

## Local Development

```bash
cd web
npm install
npm run dev
```

## Production Build & Deployment

```bash
cd web
npm run build
```

Deploy the `web/dist` directory to **Vercel**, **Netlify**, **Cloudflare Pages**, or any static host (set Root Directory to `web`, Build Command `npm run build`, Output Directory `dist`).

## Backend Gateway Architecture

By default, all Supabase Auth, PostgREST, RPC, and Edge Function calls route through `/functions/v1/swift-processor` using the `x-target-path` header—mirroring the Android client so no Supabase API key needs to be embedded in the frontend bundle.
