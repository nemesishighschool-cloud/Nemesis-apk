# Passkey journeys

Use Credential Manager for the supported consolidated credential experience. Passkeys are a recommended primary method where the product and platform support them; this design skill does not authorize adding or replacing the authentication backend.

## Creation

Suitable moments include account creation, successful recovery, immediately after a password sign-in, and account management. Explain the practical benefit before opening the platform flow.

Use consistent language such as "Create a passkey" and explain saving it to the selected password manager. Show confirmation only after actual success. Preserve alternatives and a cancellation path; do not require passkey creation just to dismiss an optional upgrade prompt.

Avoid prompting for duplicate passkeys for the same account and provider without a reason.

## Sign-in

Let the system present available credentials. Do not draw a custom credential selector that pretends to authenticate. Design zero, one, and multiple-account outcomes, provider cancellation, no available credential, and fallback.

Platform UI varies by Android version and credential provider. The reviewed guide describes streamlined Android 15 flows and differences on earlier versions. Recheck current API behavior rather than promising an exact number of taps on every device.

Keep the account identity and destination clear. A successful biometric prompt alone is not application authentication; the full credential exchange and server verification remain implementation concerns.

## Management

Provide an accessible account/security location to inspect and delete passkeys. Display provider name/icon, creation time, and last use when available. Avoid implying that a synced credential is locked to the original device.

Explain what deletion removes. Product-side credential removal and password-manager removal are distinct operations; do not claim both occurred when only one did. If the last passkey is removed, ensure the remaining recovery/sign-in path is understandable.

## Writing and assets

Lead with user benefits and familiar screen-lock methods rather than cryptographic terminology. Keep the term "passkey" consistent. Use the recognized passkey symbol according to its source asset guidance; this bundle does not redistribute that asset.

Authored runtime checks: create success/cancel/failure; single/multiple/no credentials; unavailable provider; alternate sign-in; interrupted return; deletion and subsequent fallback. Synthetic mock UI can validate layout but cannot prove a provider flow.

Source: [User authentication with passkeys](https://developer.android.com/design/ui/mobile/guides/patterns/passkeys), reviewed 2026-10-04. Follow its Credential Manager implementation link for current API requirements.
