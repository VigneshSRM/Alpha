-- ===========================================================================
-- Alpha Group of India — Supabase setup
-- Run in the Supabase SQL editor (Project → SQL → New query) or via
--   supabase db push
--
-- 1. Hardens the Auth password policy so it matches app/password_policy.py.
-- 2. Creates a `profiles` table (one row per auth user) with RLS.
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- 1. Server-side password policy (defence in depth)
--    Supabase Auth supports: min length, character classes, and (Pro plans)
--    a "leaked password protection" toggle in the dashboard.
--    The stricter combinations (2 digits / 2 symbols) are enforced by Flask.
-- ---------------------------------------------------------------------------
UPDATE auth.config
   SET minimum_password_length = 12,
       password_required_characters = 'abcdefghijklmnopqrstuvwxyz:ABCDEFGHIJKLMNOPQRSTUVWXYZ:0123456789:!@#$%^&*()_+-=[]{};''":,.?/~`|\\'
 WHERE true;

-- If the column above is not available on your project, set the equivalent
-- values in Dashboard → Authentication → Providers → Email:
--   * Minimum password length: 12
--   * Password requirements: lower, upper, numbers, symbols

-- ---------------------------------------------------------------------------
-- 2. Profiles
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.profiles (
  id           uuid PRIMARY KEY REFERENCES auth.users (id) ON DELETE CASCADE,
  full_name    text,
  email        text,
  created_at   timestamptz NOT NULL DEFAULT now(),
  updated_at   timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "profiles_select_own" ON public.profiles;
CREATE POLICY "profiles_select_own"
  ON public.profiles FOR SELECT
  TO authenticated
  USING (auth.uid() = id);

DROP POLICY IF EXISTS "profiles_update_own" ON public.profiles;
CREATE POLICY "profiles_update_own"
  ON public.profiles FOR UPDATE
  TO authenticated
  USING (auth.uid() = id)
  WITH CHECK (auth.uid() = id);

-- ---------------------------------------------------------------------------
-- 3. Create the profile row automatically after signup
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  INSERT INTO public.profiles (id, full_name, email)
  VALUES (
    NEW.id,
    COALESCE(NEW.raw_user_meta_data ->> 'full_name', NEW.raw_user_meta_data ->> 'name', ''),
    NEW.email
  )
  ON CONFLICT (id) DO NOTHING;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();
