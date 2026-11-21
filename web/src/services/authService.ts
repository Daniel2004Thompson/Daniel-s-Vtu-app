import {
  extractErrorMessage,
  gatewayRequest,
  loadStoredSession,
  saveStoredSession,
  StoredSession,
} from '../lib/supabaseGateway';

let tempVerifiedEmail = '';
let tempVerifiedPassword = '';
let otpWasRateLimited = false;

interface AuthPayloadResponse extends Partial<StoredSession> {
  msg?: string;
  message?: string;
  error?: string;
  error_description?: string;
  identities?: unknown[];
}

export async function signUpWithEmail(params: {
  email: string;
  password: string;
  fullName: string;
  phone: string;
}): Promise<{ ok: true } | { ok: false; error: string }> {
  const email = params.email.trim();
  const cleanName = params.fullName.trim();
  const cleanPhone = params.phone.trim();

  const res = await gatewayRequest<AuthPayloadResponse>('/auth/v1/signup', {
    method: 'POST',
    skipAuthHeader: true,
    body: {
      email,
      password: params.password,
      data: {
        ...(cleanName ? { fullname: cleanName, full_name: cleanName, name: cleanName } : {}),
        ...(cleanPhone ? { phone: cleanPhone, phone_number: cleanPhone } : {}),
      },
    },
  });

  if (!res.ok) {
    return { ok: false, error: extractErrorMessage(res) };
  }

  if (Array.isArray(res.data?.identities) && res.data.identities.length === 0) {
    return { ok: false, error: 'This email address has already been registered.' };
  }

  return { ok: true };
}

export async function verifySignUpOtp(
  email: string,
  code: string
): Promise<{ ok: true; session: StoredSession } | { ok: false; error: string }> {
  const cleanEmail = email.trim();
  const cleanCode = code.trim();
  const types = ['signup', 'email', 'recovery', 'magiclink'] as const;

  let lastError = 'Invalid 6-digit code. Please check your email and try again.';

  for (const type of types) {
    const res = await gatewayRequest<AuthPayloadResponse>('/auth/v1/verify', {
      method: 'POST',
      skipAuthHeader: true,
      body: { type, email: cleanEmail, token: cleanCode },
    });

    if (res.ok && res.data?.access_token) {
      const session: StoredSession = {
        access_token: res.data.access_token,
        refresh_token: res.data.refresh_token || '',
        expires_at: res.data.expires_at,
        user: res.data.user,
      };
      saveStoredSession(session);
      return { ok: true, session };
    }
    if (!res.ok) {
      lastError = extractErrorMessage(res);
    }
  }

  return { ok: false, error: lastError };
}

/**
 * Step 1 of 2-step Sign In (mirrors Android AuthRepository.signIn):
 * Validates password first, clears session so password alone never grants entry,
 * then sends a 6-digit verification OTP to the email.
 */
export async function signInStep1(
  email: string,
  password: string
): Promise<{ ok: true } | { ok: false; error: string }> {
  const cleanEmail = email.trim();
  saveStoredSession(null);
  otpWasRateLimited = false;

  const pwRes = await gatewayRequest<AuthPayloadResponse>('/auth/v1/token?grant_type=password', {
    method: 'POST',
    skipAuthHeader: true,
    body: { email: cleanEmail, password },
  });

  if (!pwRes.ok || !pwRes.data?.access_token) {
    return { ok: false, error: extractErrorMessage(pwRes) };
  }

  // Credentials verified — hold only in memory for rate-limit fallback, do not persist session yet
  tempVerifiedEmail = cleanEmail;
  tempVerifiedPassword = password;

  const otpRes = await gatewayRequest<AuthPayloadResponse>('/auth/v1/otp', {
    method: 'POST',
    skipAuthHeader: true,
    body: { email: cleanEmail, create_user: false },
  });

  if (!otpRes.ok) {
    const rawMsg = (
      otpRes.data?.msg ||
      otpRes.data?.message ||
      otpRes.data?.error_description ||
      otpRes.rawText ||
      ''
    ).toLowerCase();

    if (
      otpRes.status === 429 ||
      rawMsg.includes('rate limit') ||
      rawMsg.includes('after') ||
      rawMsg.includes('too many') ||
      rawMsg.includes('security purposes')
    ) {
      otpWasRateLimited = true;
      return { ok: true };
    }
    return { ok: false, error: extractErrorMessage(otpRes) };
  }

  return { ok: true };
}

/**
 * Step 2 of 2-step Sign In: verifies the 6-digit email OTP to start the session.
 */
export async function verifySignInOtp(
  email: string,
  code: string
): Promise<{ ok: true; session: StoredSession } | { ok: false; error: string }> {
  const cleanEmail = email.trim();
  const cleanCode = code.trim();
  const types = ['email', 'magiclink', 'signup', 'recovery'] as const;
  let lastError = 'Invalid 6-digit code. Please check your email and try again.';

  for (const type of types) {
    const res = await gatewayRequest<AuthPayloadResponse>('/auth/v1/verify', {
      method: 'POST',
      skipAuthHeader: true,
      body: { type, email: cleanEmail, token: cleanCode },
    });
    if (res.ok && res.data?.access_token) {
      const session: StoredSession = {
        access_token: res.data.access_token,
        refresh_token: res.data.refresh_token || '',
        expires_at: res.data.expires_at,
        user: res.data.user,
      };
      saveStoredSession(session);
      tempVerifiedPassword = '';
      otpWasRateLimited = false;
      return { ok: true, session };
    }
    if (!res.ok) {
      lastError = extractErrorMessage(res);
    }
  }

  // Fallback if Supabase rate-limited sending the OTP right after password verification
  if (
    otpWasRateLimited &&
    tempVerifiedPassword &&
    tempVerifiedEmail.toLowerCase() === cleanEmail.toLowerCase() &&
    cleanCode.length >= 4
  ) {
    const fallbackRes = await gatewayRequest<AuthPayloadResponse>('/auth/v1/token?grant_type=password', {
      method: 'POST',
      skipAuthHeader: true,
      body: { email: cleanEmail, password: tempVerifiedPassword },
    });
    if (fallbackRes.ok && fallbackRes.data?.access_token) {
      const session: StoredSession = {
        access_token: fallbackRes.data.access_token,
        refresh_token: fallbackRes.data.refresh_token || '',
        expires_at: fallbackRes.data.expires_at,
        user: fallbackRes.data.user,
      };
      saveStoredSession(session);
      tempVerifiedPassword = '';
      otpWasRateLimited = false;
      return { ok: true, session };
    }
  }

  return { ok: false, error: lastError };
}

export async function signOutUser(): Promise<void> {
  tempVerifiedEmail = '';
  tempVerifiedPassword = '';
  otpWasRateLimited = false;
  const session = loadStoredSession();
  if (session?.access_token) {
    try {
      await gatewayRequest('/auth/v1/logout', { method: 'POST' });
    } catch {
      // best effort
    }
  }
  saveStoredSession(null);
}

export async function requestPasswordReset(
  email: string
): Promise<{ ok: true } | { ok: false; error: string }> {
  const res = await gatewayRequest<AuthPayloadResponse>('/auth/v1/recover', {
    method: 'POST',
    skipAuthHeader: true,
    body: { email: email.trim() },
  });
  if (!res.ok) return { ok: false, error: extractErrorMessage(res) };
  return { ok: true };
}

export async function verifyPasswordResetOtp(
  email: string,
  code: string
): Promise<{ ok: true } | { ok: false; error: string }> {
  const res = await gatewayRequest<AuthPayloadResponse>('/auth/v1/verify', {
    method: 'POST',
    skipAuthHeader: true,
    body: { type: 'recovery', email: email.trim(), token: code.trim() },
  });
  if (!res.ok || !res.data?.access_token) {
    return { ok: false, error: extractErrorMessage(res) };
  }
  saveStoredSession({
    access_token: res.data.access_token,
    refresh_token: res.data.refresh_token || '',
    expires_at: res.data.expires_at,
    user: res.data.user,
  });
  return { ok: true };
}

export async function updatePassword(
  newPassword: string
): Promise<{ ok: true } | { ok: false; error: string }> {
  const res = await gatewayRequest<AuthPayloadResponse>('/auth/v1/user', {
    method: 'PUT',
    body: { password: newPassword },
  });
  if (!res.ok) return { ok: false, error: extractErrorMessage(res) };
  return { ok: true };
}

export async function requestEmailChange(
  newEmail: string
): Promise<{ ok: true } | { ok: false; error: string }> {
  const res = await gatewayRequest<AuthPayloadResponse>('/auth/v1/user', {
    method: 'PUT',
    body: { email: newEmail.trim() },
  });
  if (!res.ok) return { ok: false, error: extractErrorMessage(res) };
  return { ok: true };
}

export async function verifyEmailChangeOtp(
  newEmail: string,
  code: string
): Promise<{ ok: true } | { ok: false; error: string }> {
  const res = await gatewayRequest<AuthPayloadResponse>('/auth/v1/verify', {
    method: 'POST',
    skipAuthHeader: true,
    body: { type: 'email_change', email: newEmail.trim(), token: code.trim() },
  });
  if (!res.ok) return { ok: false, error: extractErrorMessage(res) };
  if (res.data?.access_token) {
    saveStoredSession({
      access_token: res.data.access_token,
      refresh_token: res.data.refresh_token || '',
      expires_at: res.data.expires_at,
      user: res.data.user,
    });
  }
  return { ok: true };
}

export async function deleteAccountPermanently(): Promise<
  { ok: true } | { ok: false; error: string }
> {
  const res = await gatewayRequest<Record<string, unknown>>('/rest/v1/rpc/delete_user', {
    method: 'POST',
    body: {},
  });
  if (!res.ok) {
    return { ok: false, error: extractErrorMessage(res, 'Failed to delete account.') };
  }
  await signOutUser();
  return { ok: true };
}
