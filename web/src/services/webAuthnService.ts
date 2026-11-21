const WEBAUTHN_STORAGE_PREFIX = 'daniel_vtu_webauthn_cred_v1_';

export interface StoredWebAuthnCredential {
  credentialId: string;
  createdAt: string;
  rpId: string;
}

function bufferToBase64Url(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
}

function base64UrlToUint8Array(base64Url: string): Uint8Array<ArrayBuffer> {
  const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
  const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
  const binary = atob(padded);
  const bytes = new Uint8Array(new ArrayBuffer(binary.length));
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

function randomChallenge(length = 32): Uint8Array<ArrayBuffer> {
  const bytes = new Uint8Array(new ArrayBuffer(length));
  crypto.getRandomValues(bytes);
  return bytes;
}

export function isWebAuthnSupported(): boolean {
  return (
    typeof window !== 'undefined' &&
    window.isSecureContext &&
    typeof window.PublicKeyCredential !== 'undefined' &&
    typeof navigator.credentials?.create === 'function' &&
    typeof navigator.credentials?.get === 'function'
  );
}

export async function isPlatformAuthenticatorAvailable(): Promise<boolean> {
  if (!isWebAuthnSupported()) return false;
  try {
    if (typeof PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable === 'function') {
      return await PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable();
    }
    return true;
  } catch {
    return false;
  }
}

export function getStoredBiometricCredential(userId?: string): StoredWebAuthnCredential | null {
  if (!userId) return null;
  try {
    const raw = localStorage.getItem(`${WEBAUTHN_STORAGE_PREFIX}${userId}`);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as StoredWebAuthnCredential;
    return parsed?.credentialId ? parsed : null;
  } catch {
    return null;
  }
}

export function hasBiometricEnabled(userId?: string): boolean {
  return Boolean(getStoredBiometricCredential(userId));
}

export function disableBiometricCredential(userId?: string): void {
  if (!userId) return;
  try {
    localStorage.removeItem(`${WEBAUTHN_STORAGE_PREFIX}${userId}`);
  } catch {
    // ignore storage errors
  }
}

function mapWebAuthnError(err: unknown): string {
  if (err instanceof DOMException) {
    switch (err.name) {
      case 'NotAllowedError':
        return 'Biometric verification was cancelled or timed out.';
      case 'NotSupportedError':
        return 'Your device or browser does not support built-in biometric credentials.';
      case 'SecurityError':
        return 'WebAuthn requires HTTPS or localhost to access device biometric sensors.';
      case 'InvalidStateError':
        return 'A biometric credential is already registered for this device.';
      case 'AbortError':
        return 'Biometric request was aborted.';
      default:
        return err.message || 'Biometric authentication failed.';
    }
  }
  if (err instanceof Error) return err.message;
  return 'Biometric authentication failed.';
}

export async function registerBiometricCredential(params: {
  userId: string;
  email: string;
  fullName: string;
}): Promise<{ ok: true; credential: StoredWebAuthnCredential } | { ok: false; error: string }> {
  if (!isWebAuthnSupported()) {
    return {
      ok: false,
      error:
        'Biometric authentication requires a modern browser over HTTPS (or localhost) with WebAuthn support.',
    };
  }

  const platformAvailable = await isPlatformAuthenticatorAvailable();
  if (!platformAvailable) {
    return {
      ok: false,
      error:
        'No built-in fingerprint, Face ID, or Windows Hello sensor was detected on this device.',
    };
  }

  try {
    const userBytesRaw = new TextEncoder().encode(params.userId.slice(0, 64));
    const userIdBuffer = new Uint8Array(new ArrayBuffer(userBytesRaw.length));
    userIdBuffer.set(userBytesRaw);
    const rpId = window.location.hostname;

    const credential = (await navigator.credentials.create({
      publicKey: {
        challenge: randomChallenge(),
        rp: {
          name: 'Daniel VTU',
          id: rpId,
        },
        user: {
          id: userIdBuffer,
          name: params.email || params.userId,
          displayName: params.fullName || params.email || 'Daniel VTU User',
        },
        pubKeyCredParams: [
          { alg: -7, type: 'public-key' },   // ES256
          { alg: -257, type: 'public-key' }, // RS256
        ],
        authenticatorSelection: {
          authenticatorAttachment: 'platform',
          residentKey: 'preferred',
          userVerification: 'required',
        },
        timeout: 60000,
        attestation: 'none',
      },
    })) as PublicKeyCredential | null;

    if (!credential || !credential.rawId) {
      return { ok: false, error: 'Failed to create biometric credential.' };
    }

    const stored: StoredWebAuthnCredential = {
      credentialId: bufferToBase64Url(credential.rawId),
      createdAt: new Date().toISOString(),
      rpId,
    };

    localStorage.setItem(`${WEBAUTHN_STORAGE_PREFIX}${params.userId}`, JSON.stringify(stored));
    return { ok: true, credential: stored };
  } catch (err) {
    return { ok: false, error: mapWebAuthnError(err) };
  }
}

export async function authenticateWithBiometric(
  userId: string
): Promise<{ ok: true } | { ok: false; error: string }> {
  if (!isWebAuthnSupported()) {
    return {
      ok: false,
      error: 'WebAuthn biometric authentication is unavailable in this browser context.',
    };
  }

  const stored = getStoredBiometricCredential(userId);
  if (!stored) {
    return { ok: false, error: 'Biometric authentication is not set up on this device yet.' };
  }

  try {
    const assertion = (await navigator.credentials.get({
      publicKey: {
        challenge: randomChallenge(),
        rpId: stored.rpId || window.location.hostname,
        allowCredentials: [
          {
            id: base64UrlToUint8Array(stored.credentialId),
            type: 'public-key',
            transports: ['internal'],
          },
        ],
        userVerification: 'required',
        timeout: 60000,
      },
    })) as PublicKeyCredential | null;

    if (!assertion) {
      return { ok: false, error: 'Biometric verification returned empty assertion.' };
    }

    const response = assertion.response as AuthenticatorAssertionResponse;
    if (!response?.authenticatorData || response.authenticatorData.byteLength === 0) {
      return { ok: false, error: 'Invalid authenticator response.' };
    }

    return { ok: true };
  } catch (err) {
    return { ok: false, error: mapWebAuthnError(err) };
  }
}
