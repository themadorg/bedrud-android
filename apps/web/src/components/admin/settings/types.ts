export interface SystemSettings {
  id: number
  registrationEnabled: boolean
  tokenRegistrationOnly: boolean
  passkeysEnabled: boolean
  googleClientId: string
  googleClientSecret: string
  googleRedirectUrl: string
  githubClientId: string
  githubClientSecret: string
  githubRedirectUrl: string
  twitterClientId: string
  twitterClientSecret: string
  twitterRedirectUrl: string
  jwtSecret: string
  tokenDuration: number
  sessionSecret: string
  frontendUrl: string
  serverPort: string
  serverHost: string
  serverDomain: string
  serverEnableTls: boolean
  serverCertFile: string
  serverKeyFile: string
  serverUseAcme: boolean
  serverEmail: string
  behindProxy: boolean
  livekitHost: string
  livekitApiKey: string
  livekitApiSecret: string
  livekitExternal: boolean
  corsAllowedOrigins: string
  corsAllowedHeaders: string
  corsAllowedMethods: string
  corsAllowCredentials: boolean
  corsMaxAge: number
  chatUploadBackend: string
  chatUploadMaxBytes: number
  chatUploadInlineMax: number
  chatUploadDiskDir: string
  chatUploadS3Endpoint: string
  chatUploadS3Bucket: string
  chatUploadS3Region: string
  chatUploadS3AccessKey: string
  chatUploadS3SecretKey: string
  chatUploadS3PublicUrl: string
  logLevel: string
  maxParticipantsLimit: number
  maxRoomsPerUser: number
  maxUploadBytesPerUser: number
  globalDiskThresholdBytes: number
  chatMaxMessageCount: number
  chatMessageTTLHours: number
  updatedAt: string
}

export interface InviteToken {
  id: string
  token: string
  email: string
  createdBy: string
  expiresAt: string
  usedAt: string | null
  usedBy: string
  createdAt: string
}

export type RegMode = 'open' | 'invite' | 'closed'
