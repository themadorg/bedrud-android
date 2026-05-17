import { Field, Section, TextInput, Toggle } from './shared'
import type { SystemSettings } from './types'

function OAuthProviderCard({
  name,
  clientId,
  clientSecret,
  redirectUrl,
  onChange,
  idPrefix,
  errors,
  clearFieldError,
}: {
  name: string
  clientId: string
  clientSecret: string
  redirectUrl: string
  onChange: (v: Record<string, string>) => void
  idPrefix: string
  errors?: Record<string, string>
  clearFieldError?: (field: string) => void
}) {
  const configured = clientId !== '' && clientSecret !== '' && clientSecret !== '••••••••'

  const redirectField = `${idPrefix}RedirectUrl` as keyof SystemSettings

  return (
    <Section title={name} description={configured ? 'Configured' : 'Not configured'}>
      <div className="space-y-3">
        <Field
          label="Client ID"
          hint="OAuth app credential from the provider's developer console. Leave empty to use config.yaml value."
        >
          <TextInput
            value={clientId}
            onChange={(v) => onChange({ [`${idPrefix}ClientId`]: v })}
            placeholder={`Enter ${name} Client ID`}
            mono
          />
        </Field>
        <Field
          label="Client Secret"
          hint={
            clientSecret === '••••••••'
              ? 'Hidden — enter new value to change'
              : "OAuth app secret from the provider's developer console. Leave empty to use config.yaml value."
          }
        >
          <TextInput
            value={clientSecret}
            onChange={(v) => onChange({ [`${idPrefix}ClientSecret`]: v })}
            placeholder={configured ? '••••••••' : `Enter ${name} Client Secret`}
            mono
          />
        </Field>
        <Field
          label="Redirect URL"
          hint="Must exactly match the URI registered with the OAuth provider. Leave empty to use config.yaml value."
        >
          <TextInput
            type="url"
            value={redirectUrl}
            onChange={(v) => {
              const fieldName = `${idPrefix}RedirectUrl`
              clearFieldError?.(fieldName)
              onChange({ [fieldName]: v })
            }}
            placeholder={`https://your-domain.com/auth/${idPrefix}/callback`}
            mono
            error={errors?.[redirectField]}
          />
        </Field>
      </div>
    </Section>
  )
}

export function AuthTab({
  settings,
  setSettings,
  errors,
  clearFieldError,
}: {
  settings: SystemSettings
  setSettings: (s: SystemSettings) => void
  errors?: Record<string, string>
  clearFieldError?: (field: string) => void
}) {
  return (
    <div className="space-y-4">
      <Section title="Passkeys" description="Biometric and hardware key authentication">
        <Toggle
          checked={settings.passkeysEnabled}
          onChange={(v) => setSettings({ ...settings, passkeysEnabled: v })}
          label="Enable passkey login and registration"
        />
      </Section>

      <OAuthProviderCard
        name="Google"
        clientId={settings.googleClientId}
        clientSecret={settings.googleClientSecret}
        redirectUrl={settings.googleRedirectUrl}
        onChange={(v) => setSettings({ ...settings, ...v })}
        idPrefix="google"
        errors={errors}
        clearFieldError={clearFieldError}
      />

      <OAuthProviderCard
        name="GitHub"
        clientId={settings.githubClientId}
        clientSecret={settings.githubClientSecret}
        redirectUrl={settings.githubRedirectUrl}
        onChange={(v) => setSettings({ ...settings, ...v })}
        idPrefix="github"
        errors={errors}
        clearFieldError={clearFieldError}
      />

      <OAuthProviderCard
        name="Twitter / X"
        clientId={settings.twitterClientId}
        clientSecret={settings.twitterClientSecret}
        redirectUrl={settings.twitterRedirectUrl}
        onChange={(v) => setSettings({ ...settings, ...v })}
        idPrefix="twitter"
        errors={errors}
        clearFieldError={clearFieldError}
      />
    </div>
  )
}
