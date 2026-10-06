# Supabase Edge Functions: Gsubz VTU & Flutterwave Payments
# Daniel VTU Application

This directory contains the production-ready Supabase Edge Functions and SQL functions for **Daniel VTU**:

1. `gsubz-vtu`: Dispatches Airtime, Data, Electricity, and Cable TV purchases via Gsubz API using secrets stored in your Supabase project.
2. `flutterwave-payment`: Handles Flutterwave checkout initialization and transaction verification for instant wallet funding.
3. `Create-Virtual-Account`: Automatically creates dedicated virtual account numbers via Flutterwave secrets and supports user deletion.
4. `delete-user`: Permanently deletes a user from Supabase Authentication (`auth.users`).

---

### Step 1: Set Edge Function Secrets in Supabase

Run the following commands using the [Supabase CLI](https://supabase.com/docs/guides/cli):

```bash
# 1. Login and link your project
supabase login
supabase link --project-ref yjymxdzdhvbdjramlipg

# 2. Set Gsubz VTU API Secret(s)
supabase secrets set GSUBZ_API_KEY="your_gsubz_api_key_or_token"
supabase secrets set GSUBZ_USERNAME="your_gsubz_username"

# 3. Set Flutterwave API Secret(s)
supabase secrets set FLUTTERWAVE_SECRET_KEY="FLWSECK-xxxxxxxxxxxxxxxxxxxxxxxx-X"
supabase secrets set FLUTTERWAVE_PUBLIC_KEY="FLWPUBK-xxxxxxxxxxxxxxxxxxxxxxxx-X"
supabase secrets set FLUTTERWAVE_ENCRYPTION_KEY="your_flw_encryption_key"
```

---

### Step 2: Enable Account Deletion via Supabase SQL Editor

To allow users who click "Delete Account" to be deleted instantly from `auth.users` under Authentication, run this one-line function in your **Supabase Dashboard -> SQL Editor**:

```sql
create or replace function delete_user()
returns void
language sql
security definer
set search_path = public
as $$
  delete from auth.users where id = auth.uid();
$$;

grant execute on function delete_user() to authenticated;
```

---

### Step 2b: Enable Supabase Realtime for Wallet Balance

To enable live wallet balance updates (via `observeWalletBalance` using `postgresChangeFlow` in the Android app without polling), run `supabase/realtime_wallet_setup.sql` in your **Supabase Dashboard -> SQL Editor**:

```sql
-- Enable Realtime publication on public.users
ALTER PUBLICATION supabase_realtime ADD TABLE public.users;
ALTER TABLE public.users REPLICA IDENTITY FULL;

-- Atomic debit procedure
CREATE OR REPLACE FUNCTION public.debit_wallet(amount numeric)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  current_bal numeric;
BEGIN
  IF amount <= 0 THEN
    RAISE EXCEPTION 'Debit amount must be greater than zero';
  END IF;

  SELECT wallet_balance INTO current_bal
  FROM public.users
  WHERE id = auth.uid()
  FOR UPDATE;

  IF current_bal IS NULL OR current_bal < amount THEN
    RAISE EXCEPTION 'Insufficient wallet balance';
  END IF;

  UPDATE public.users
  SET wallet_balance = wallet_balance - amount,
      updated_at = timezone('utc'::text, now())
  WHERE id = auth.uid();
END;
$$;

GRANT EXECUTE ON FUNCTION public.debit_wallet(numeric) TO authenticated;
```


---

### Step 3: Deploy the Edge Functions

```bash
# Deploy Virtual Account Generator (Dedicated Flutterwave Accounts)
supabase functions deploy Create-Virtual-Account --no-verify-jwt

# Deploy Account Deletion Function
supabase functions deploy delete-user --no-verify-jwt

# Deploy Gsubz VTU Service
supabase functions deploy Gsubz-VTU-Services --no-verify-jwt

# Deploy Flutterwave Payment Gateway
supabase functions deploy flutterwave-payment --no-verify-jwt
```

---

### Step 4: Verify in Android App

The Android app automatically queries:
- `https://yjymxdzdhvbdjramlipg.supabase.co/functions/v1/Gsubz-VTU-Services`
- `https://yjymxdzdhvbdjramlipg.supabase.co/functions/v1/Create-Virtual-Account`
- `https://yjymxdzdhvbdjramlipg.supabase.co/functions/v1/delete-user`
- `https://yjymxdzdhvbdjramlipg.supabase.co/rest/v1/rpc/delete_user`
- `https://yjymxdzdhvbdjramlipg.supabase.co/functions/v1/flutterwave-payment`
