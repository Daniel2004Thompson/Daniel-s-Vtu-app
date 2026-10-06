-- ==============================================================================
-- Supabase Realtime & Wallet Setup SQL
-- ==============================================================================
-- Run this script in your Supabase Project Dashboard -> SQL Editor.
-- It enables Realtime change streams on public.users and provides atomic
-- wallet functions (debit_wallet and credit_wallet).
-- ==============================================================================

-- 1. Ensure public.users table exists with wallet_balance and permanent account columns
CREATE TABLE IF NOT EXISTS public.users (
  id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  email text,
  full_name text,
  phone text,
  bvn text,
  nin text,
  wallet_balance numeric(12, 2) DEFAULT 0.00 NOT NULL,
  cashback_balance numeric(12, 2) DEFAULT 0.00 NOT NULL,
  permanent_account_number text,
  permanent_account_bank text DEFAULT 'Flutterwave MFB',
  permanent_account_name text,
  virtual_account_number text,
  virtual_bank text DEFAULT 'Flutterwave MFB',
  virtual_bank_name text DEFAULT 'Flutterwave MFB',
  virtual_account_name text,
  created_at timestamp with time zone DEFAULT timezone('utc'::text, now()) NOT NULL,
  updated_at timestamp with time zone DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- Ensure columns exist in case table was previously created without them
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS nin text;
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS bvn text;
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS permanent_account_number text;
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS permanent_account_bank text DEFAULT 'Flutterwave MFB';
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS permanent_account_name text;
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS virtual_account_number text;
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS virtual_bank text DEFAULT 'Flutterwave MFB';
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS virtual_bank_name text DEFAULT 'Flutterwave MFB';
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS virtual_account_name text;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'profiles') THEN
    ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS nin text;
    ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS bvn text;
    ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS permanent_account_number text;
    ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS permanent_account_bank text DEFAULT 'Flutterwave MFB';
    ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS permanent_account_name text;
    ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS virtual_account_number text;
    ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS virtual_bank text DEFAULT 'Flutterwave MFB';
    ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS virtual_bank_name text DEFAULT 'Flutterwave MFB';
    ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS virtual_account_name text;
  END IF;
END $$;

-- 2. Add 'users' table to the Supabase Realtime publication
-- This enables postgresChangeFlow in the Kotlin app to listen for live updates
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_publication_tables 
    WHERE pubname = 'supabase_realtime' 
    AND schemaname = 'public' 
    AND tablename = 'users'
  ) THEN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.users;
  END IF;
END $$;

-- 3. Set REPLICA IDENTITY to FULL so update broadcasts include all columns
ALTER TABLE public.users REPLICA IDENTITY FULL;

-- Also support 'profiles' table if your schema uses profiles
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'profiles') THEN
    IF NOT EXISTS (
      SELECT 1 FROM pg_publication_tables 
      WHERE pubname = 'supabase_realtime' 
      AND schemaname = 'public' 
      AND tablename = 'profiles'
    ) THEN
      ALTER PUBLICATION supabase_realtime ADD TABLE public.profiles;
    END IF;
    ALTER TABLE public.profiles REPLICA IDENTITY FULL;
  END IF;
END $$;

-- 4. Enable Row Level Security (RLS)
ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;

-- Allow authenticated users to read their own record (required for Realtime subscription filter)
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies WHERE schemaname = 'public' AND tablename = 'users' AND policyname = 'Users can view own data'
  ) THEN
    CREATE POLICY "Users can view own data"
      ON public.users
      FOR SELECT
      TO authenticated
      USING (auth.uid() = id);
  END IF;
END $$;

-- 5. Atomic Debit Function for Withdrawals & Purchases
CREATE OR REPLACE FUNCTION public.debit_wallet(amount numeric, target_user_id uuid DEFAULT auth.uid())
RETURNS numeric
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  current_bal numeric;
  new_bal numeric;
  target_id uuid;
BEGIN
  IF amount <= 0 THEN
    RAISE EXCEPTION 'Debit amount must be greater than zero';
  END IF;

  target_id := COALESCE(target_user_id, auth.uid());
  IF target_id IS NULL THEN
    RAISE EXCEPTION 'Target user ID cannot be null';
  END IF;

  SELECT wallet_balance INTO current_bal
  FROM public.users
  WHERE id = target_id
  FOR UPDATE;

  IF current_bal IS NULL OR current_bal < amount THEN
    RAISE EXCEPTION 'Insufficient wallet balance';
  END IF;

  UPDATE public.users
  SET wallet_balance = GREATEST(0.00, wallet_balance - amount),
      updated_at = timezone('utc'::text, now())
  WHERE id = target_id
  RETURNING wallet_balance INTO new_bal;

  -- Synchronize public.profiles if present
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'profiles') THEN
    UPDATE public.profiles
    SET wallet_balance = GREATEST(0.00, COALESCE(wallet_balance, 0.00) - amount),
        updated_at = timezone('utc'::text, now())
    WHERE id = target_id;
  END IF;

  RETURN COALESCE(new_bal, 0.00);
END;
$$;

-- Overload for single-argument backward compatibility
CREATE OR REPLACE FUNCTION public.debit_wallet(amount numeric)
RETURNS numeric
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  RETURN public.debit_wallet(amount, auth.uid());
END;
$$;

GRANT EXECUTE ON FUNCTION public.debit_wallet(numeric, uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.debit_wallet(numeric, uuid) TO service_role;
GRANT EXECUTE ON FUNCTION public.debit_wallet(numeric) TO authenticated;
GRANT EXECUTE ON FUNCTION public.debit_wallet(numeric) TO anon;
GRANT EXECUTE ON FUNCTION public.debit_wallet(numeric) TO service_role;

-- 6. Atomic Refund Functions
-- Minuses the refunded amount from the user's dashboard balance and returns the remainder
CREATE OR REPLACE FUNCTION public.refund_wallet(
  amount numeric,
  target_user_id uuid DEFAULT auth.uid(),
  refund_reason text DEFAULT 'Refund deduction'
)
RETURNS numeric
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  new_bal numeric;
  target_id uuid;
BEGIN
  IF amount <= 0 THEN
    RAISE EXCEPTION 'Refund amount must be greater than zero';
  END IF;

  target_id := COALESCE(target_user_id, auth.uid());
  IF target_id IS NULL THEN
    RAISE EXCEPTION 'Target user ID cannot be null';
  END IF;

  -- Minus the refunded amount directly from the dashboard balance
  UPDATE public.users
  SET wallet_balance = GREATEST(0.00, COALESCE(wallet_balance, 0.00) - amount),
      updated_at = timezone('utc'::text, now())
  WHERE id = target_id
  RETURNING wallet_balance INTO new_bal;

  -- Synchronize profiles if table exists
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'profiles') THEN
    UPDATE public.profiles
    SET wallet_balance = GREATEST(0.00, COALESCE(wallet_balance, 0.00) - amount),
        updated_at = timezone('utc'::text, now())
    WHERE id = target_id;
  END IF;

  -- Log into transactions history if available
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'transactions') THEN
    INSERT INTO public.transactions (user_id, type, amount, status, description, created_at)
    VALUES (target_id, 'REFUND_DEBIT', amount, 'SUCCESS', refund_reason, timezone('utc'::text, now()));
  END IF;

  RETURN COALESCE(new_bal, 0.00);
END;
$$;

-- Refund user by their registered email address
CREATE OR REPLACE FUNCTION public.refund_by_email(
  amount numeric,
  user_email text,
  refund_reason text DEFAULT 'Refund deduction'
)
RETURNS numeric
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  uid uuid;
BEGIN
  SELECT id INTO uid FROM public.users WHERE LOWER(email) = LOWER(TRIM(user_email)) LIMIT 1;
  IF uid IS NULL THEN
    SELECT id INTO uid FROM auth.users WHERE LOWER(email) = LOWER(TRIM(user_email)) LIMIT 1;
  END IF;
  IF uid IS NULL THEN
    RAISE EXCEPTION 'No user found with email %', user_email;
  END IF;

  RETURN public.refund_wallet(amount, uid, refund_reason);
END;
$$;

-- Refund user by their registered phone number
CREATE OR REPLACE FUNCTION public.refund_by_phone(
  amount numeric,
  user_phone text,
  refund_reason text DEFAULT 'Refund deduction'
)
RETURNS numeric
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  uid uuid;
  clean_phone text;
BEGIN
  clean_phone := REGEXP_REPLACE(user_phone, '\D', '', 'g');
  SELECT id INTO uid FROM public.users WHERE REGEXP_REPLACE(phone, '\D', '', 'g') = clean_phone LIMIT 1;
  IF uid IS NULL THEN
    RAISE EXCEPTION 'No user found with phone %', user_phone;
  END IF;

  RETURN public.refund_wallet(amount, uid, refund_reason);
END;
$$;

GRANT EXECUTE ON FUNCTION public.refund_wallet(numeric, uuid, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.refund_wallet(numeric, uuid, text) TO service_role;
GRANT EXECUTE ON FUNCTION public.refund_wallet(numeric, uuid, text) TO anon;
GRANT EXECUTE ON FUNCTION public.refund_by_email(numeric, text, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.refund_by_email(numeric, text, text) TO service_role;
GRANT EXECUTE ON FUNCTION public.refund_by_email(numeric, text, text) TO anon;
GRANT EXECUTE ON FUNCTION public.refund_by_phone(numeric, text, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.refund_by_phone(numeric, text, text) TO service_role;
GRANT EXECUTE ON FUNCTION public.refund_by_phone(numeric, text, text) TO anon;

-- 7. Atomic Credit Function for Top-ups & Funding
CREATE OR REPLACE FUNCTION public.credit_wallet(amount numeric, target_user_id uuid DEFAULT auth.uid())
RETURNS numeric
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  new_bal numeric;
  target_id uuid;
BEGIN
  IF amount <= 0 THEN
    RAISE EXCEPTION 'Credit amount must be greater than zero';
  END IF;

  target_id := COALESCE(target_user_id, auth.uid());
  IF target_id IS NULL THEN
    RAISE EXCEPTION 'Target user ID cannot be null';
  END IF;

  UPDATE public.users
  SET wallet_balance = COALESCE(wallet_balance, 0.00) + amount,
      updated_at = timezone('utc'::text, now())
  WHERE id = target_id
  RETURNING wallet_balance INTO new_bal;

  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'profiles') THEN
    UPDATE public.profiles
    SET wallet_balance = COALESCE(wallet_balance, 0.00) + amount,
        updated_at = timezone('utc'::text, now())
    WHERE id = target_id;
  END IF;

  RETURN COALESCE(new_bal, 0.00);
END;
$$;

-- Credit by email
CREATE OR REPLACE FUNCTION public.credit_by_email(amount numeric, user_email text)
RETURNS numeric
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  uid uuid;
BEGIN
  SELECT id INTO uid FROM public.users WHERE LOWER(email) = LOWER(TRIM(user_email)) LIMIT 1;
  IF uid IS NULL THEN
    SELECT id INTO uid FROM auth.users WHERE LOWER(email) = LOWER(TRIM(user_email)) LIMIT 1;
  END IF;
  IF uid IS NULL THEN
    RAISE EXCEPTION 'No user found with email %', user_email;
  END IF;

  RETURN public.credit_wallet(amount, uid);
END;
$$;

GRANT EXECUTE ON FUNCTION public.credit_wallet(numeric, uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.credit_wallet(numeric, uuid) TO service_role;
GRANT EXECUTE ON FUNCTION public.credit_wallet(numeric, uuid) TO anon;
GRANT EXECUTE ON FUNCTION public.credit_by_email(numeric, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.credit_by_email(numeric, text) TO service_role;
GRANT EXECUTE ON FUNCTION public.credit_by_email(numeric, text) TO anon;

-- 8. Delete User & Data Function
-- Completely removes user from public.users, public.profiles, and auth.users
-- Allows user to re-register with the same email and generate permanent account afresh
CREATE OR REPLACE FUNCTION public.delete_user()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  uid uuid;
BEGIN
  uid := auth.uid();
  IF uid IS NOT NULL THEN
    DELETE FROM public.users WHERE id = uid;
    IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'profiles') THEN
      DELETE FROM public.profiles WHERE id = uid;
    END IF;
    DELETE FROM auth.users WHERE id = uid;
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.delete_user_account()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  PERFORM public.delete_user();
END;
$$;

GRANT EXECUTE ON FUNCTION public.delete_user() TO authenticated;
GRANT EXECUTE ON FUNCTION public.delete_user() TO service_role;
GRANT EXECUTE ON FUNCTION public.delete_user() TO anon;
GRANT EXECUTE ON FUNCTION public.delete_user_account() TO authenticated;
GRANT EXECUTE ON FUNCTION public.delete_user_account() TO service_role;
GRANT EXECUTE ON FUNCTION public.delete_user_account() TO anon;

