package handlers

import (
	"bedrud/internal/auth"
	"bedrud/internal/models"
	"bedrud/internal/repository"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"net/mail"
	"strings"
	"time"

	"github.com/gofiber/fiber/v2"
	"github.com/google/uuid"
	"github.com/rs/zerolog/log"
)

// validateSettings checks that settings values are within acceptable ranges.
func validateSettings(s *models.SystemSettings) error {
	if s.TokenDuration != 0 && (s.TokenDuration < 1 || s.TokenDuration > 8760) {
		return fmt.Errorf("tokenDuration must be between 1 and 8760 hours, or 0 for default")
	}
	validBackends := map[string]bool{"disk": true, "inline": true, "s3": true, "": true}
	if !validBackends[s.ChatUploadBackend] {
		return fmt.Errorf("chatUploadBackend must be disk, inline, or s3")
	}
	if s.ChatUploadMaxBytes < 0 {
		return fmt.Errorf("chatUploadMaxBytes cannot be negative")
	}
	validLevels := map[string]bool{"debug": true, "info": true, "warn": true, "error": true, "": true}
	if !validLevels[s.LogLevel] {
		return fmt.Errorf("invalid logLevel")
	}
	if s.MaxParticipantsLimit < 0 || s.MaxParticipantsLimit > 100000 {
		return fmt.Errorf("maxParticipantsLimit must be between 0 and 100000")
	}
	if s.MaxRoomsPerUser < 0 || s.MaxRoomsPerUser > 100000 {
		return fmt.Errorf("maxRoomsPerUser must be between 0 and 100000")
	}
	if s.MaxUploadBytesPerUser < 0 {
		return fmt.Errorf("maxUploadBytesPerUser cannot be negative")
	}
	if s.GlobalDiskThresholdBytes < 0 {
		return fmt.Errorf("globalDiskThresholdBytes cannot be negative")
	}
	if s.ChatMaxMessageCount < 0 {
		return fmt.Errorf("chatMaxMessageCount cannot be negative")
	}
	if s.ChatMessageTTLHours < 0 {
		return fmt.Errorf("chatMessageTTLHours cannot be negative")
	}
	if s.JWTSecret != "" && len(s.JWTSecret) < 32 {
		return fmt.Errorf("jwtSecret must be at least 32 characters")
	}
	if s.CORSAllowCredentials && (s.CORSAllowedOrigins == "*" || s.CORSAllowedOrigins == "") {
		return fmt.Errorf("corsAllowCredentials cannot be true when corsAllowedOrigins is '*' or empty")
	}
	return nil
}

const maskedSecret = "••••••••"

type AdminHandler struct {
	settingsRepo    *repository.SettingsRepository
	inviteTokenRepo *repository.InviteTokenRepository
}

func NewAdminHandler(sr *repository.SettingsRepository, itr *repository.InviteTokenRepository) *AdminHandler {
	return &AdminHandler{settingsRepo: sr, inviteTokenRepo: itr}
}

func (h *AdminHandler) GetSettings(c *fiber.Ctx) error {
	s, err := h.settingsRepo.GetEffectiveSettings()
	if err != nil {
		log.Error().Err(err).Msg("Failed to fetch settings")
		return c.Status(500).JSON(fiber.Map{"error": "Failed to fetch settings"})
	}
	return c.JSON(maskSettings(s))
}

// GetPublicSettings returns only the fields relevant to anonymous visitors (no auth required).
func (h *AdminHandler) GetPublicSettings(c *fiber.Ctx) error {
	s, err := h.settingsRepo.GetEffectiveSettings()
	if err != nil {
		log.Error().Err(err).Msg("Failed to fetch public settings")
		return c.Status(500).JSON(fiber.Map{"error": "Failed to fetch settings"})
	}
	return c.JSON(fiber.Map{
		"registrationEnabled":   s.RegistrationEnabled,
		"tokenRegistrationOnly": s.TokenRegistrationOnly,
		"passkeysEnabled":       s.PasskeysEnabled,
		"oauthProviders":        auth.ConfiguredProviders(),
		"chatMaxMessageCount":   s.ChatMaxMessageCount,
		"chatMessageTTLHours":   s.ChatMessageTTLHours,
	})
}

func (h *AdminHandler) UpdateSettings(c *fiber.Ctx) error {
	// Get existing settings first (to use as base for partial updates)
	existing, err := h.settingsRepo.GetSettings()
	if err != nil {
		log.Error().Err(err).Msg("Failed to fetch current settings")
		return c.Status(500).JSON(fiber.Map{"error": "Failed to fetch current settings"})
	}

	// Parse raw JSON body to detect which fields the client actually sent
	// (rather than zeroing unset fields via direct struct unmarshal)
	var raw map[string]json.RawMessage
	if err := c.BodyParser(&raw); err != nil {
		return c.Status(400).JSON(fiber.Map{"error": "Invalid input"})
	}

	// Apply only the fields present in the request onto the existing settings
	existing = applySettingsFields(existing, raw)

	// Validate merged settings
	if err := validateSettings(existing); err != nil {
		return c.Status(400).JSON(fiber.Map{"error": err.Error()})
	}

	existing.ID = 1
	if err := h.settingsRepo.SaveSettings(existing); err != nil {
		log.Error().Err(err).Msg("Failed to save settings")
		return c.Status(500).JSON(fiber.Map{"error": "Failed to save settings"})
	}

	// Reload runtime-configurable subsystems
	effective, err := h.settingsRepo.GetEffectiveSettings()
	if err != nil {
		log.Error().Err(err).Msg("Settings saved but failed to reload")
		return c.Status(500).JSON(fiber.Map{"error": "Settings saved but failed to reload"})
	}
	auth.ReloadProviders(effective)

	log.Info().Msg("Admin settings updated and providers reloaded")
	return c.JSON(maskSettings(effective))
}

// applySettingsFields selectively applies fields from raw JSON onto existing settings.
// Only fields present in the JSON body are applied; others retain their current value.
func applySettingsFields(existing *models.SystemSettings, raw map[string]json.RawMessage) *models.SystemSettings {
	if v, ok := raw["registrationEnabled"]; ok {
		var b bool
		if json.Unmarshal(v, &b) == nil {
			existing.RegistrationEnabled = b
		}
	}
	if v, ok := raw["tokenRegistrationOnly"]; ok {
		var b bool
		if json.Unmarshal(v, &b) == nil {
			existing.TokenRegistrationOnly = b
		}
	}
	if v, ok := raw["passkeysEnabled"]; ok {
		var b bool
		if json.Unmarshal(v, &b) == nil {
			existing.PasskeysEnabled = b
		}
	}

	// Unmask secrets: if client sent masked placeholder, keep existing value
	type secretHelper struct {
		rawKey  string
		target  *string
		current string
	}
	secrets := []secretHelper{
		{"googleClientSecret", &existing.GoogleClientSecret, existing.GoogleClientSecret},
		{"githubClientSecret", &existing.GithubClientSecret, existing.GithubClientSecret},
		{"twitterClientSecret", &existing.TwitterClientSecret, existing.TwitterClientSecret},
		{"jwtSecret", &existing.JWTSecret, existing.JWTSecret},
		{"sessionSecret", &existing.SessionSecret, existing.SessionSecret},
		{"livekitApiSecret", &existing.LiveKitAPISecret, existing.LiveKitAPISecret},
		{"chatUploadS3AccessKey", &existing.ChatUploadS3AccessKey, existing.ChatUploadS3AccessKey},
		{"chatUploadS3SecretKey", &existing.ChatUploadS3SecretKey, existing.ChatUploadS3SecretKey},
	}
	for _, s := range secrets {
		rawVal, ok := raw[s.rawKey]
		if !ok {
			continue
		}
		var strVal string
		if err := json.Unmarshal(rawVal, &strVal); err != nil {
			continue
		}
		if strings.TrimSpace(strVal) == maskedSecret {
			*s.target = s.current
		} else {
			*s.target = strVal
		}
	}

	// String fields
	if v, ok := raw["googleClientId"]; ok {
		var s string
		if json.Unmarshal(v, &s) == nil {
			existing.GoogleClientID = s
		}
	}
	if v, ok := raw["googleRedirectUrl"]; ok {
		var s string
		if json.Unmarshal(v, &s) == nil {
			existing.GoogleRedirectURL = s
		}
	}
	if v, ok := raw["githubClientId"]; ok {
		var s string
		if json.Unmarshal(v, &s) == nil {
			existing.GithubClientID = s
		}
	}
	if v, ok := raw["githubRedirectUrl"]; ok {
		var s string
		if json.Unmarshal(v, &s) == nil {
			existing.GithubRedirectURL = s
		}
	}
	if v, ok := raw["twitterClientId"]; ok {
		var s string
		if json.Unmarshal(v, &s) == nil {
			existing.TwitterClientID = s
		}
	}
	if v, ok := raw["twitterRedirectUrl"]; ok {
		var s string
		if json.Unmarshal(v, &s) == nil {
			existing.TwitterRedirectURL = s
		}
	}
	if v, ok := raw["frontendUrl"]; ok {
		var s string
		if json.Unmarshal(v, &s) == nil {
			existing.FrontendURL = s
		}
	}
	if v, ok := raw["serverPort"]; ok {
		var s string
		if json.Unmarshal(v, &s) == nil {
			existing.ServerPort = s
		}
	}
	if v, ok := raw["serverHost"]; ok {
		var s string
		if json.Unmarshal(v, &s) == nil {
			existing.ServerHost = s
		}
	}
	if v, ok := raw["serverDomain"]; ok {
		var s string
		if json.Unmarshal(v, &s) == nil {
			existing.ServerDomain = s
		}
	}
	if v, ok := raw["serverCertFile"]; ok {
		var s string
		if json.Unmarshal(v, &s) == nil {
			existing.ServerCertFile = s
		}
	}
	if v, ok := raw["serverKeyFile"]; ok {
		var s string
		if json.Unmarshal(v, &s) == nil {
			existing.ServerKeyFile = s
		}
	}
	if v, ok := raw["serverEmail"]; ok {
		var s string
		if json.Unmarshal(v, &s) == nil {
			existing.ServerEmail = s
		}
	}
	if v, ok := raw["livekitHost"]; ok {
		var s string
		if json.Unmarshal(v, &s) == nil {
			existing.LiveKitHost = s
		}
	}
	if v, ok := raw["livekitApiKey"]; ok {
		var s string
		if json.Unmarshal(v, &s) == nil {
			existing.LiveKitAPIKey = s
		}
	}
	if v, ok := raw["corsAllowedOrigins"]; ok {
		var s string
		if json.Unmarshal(v, &s) == nil {
			existing.CORSAllowedOrigins = s
		}
	}
	if v, ok := raw["corsAllowedHeaders"]; ok {
		var s string
		if json.Unmarshal(v, &s) == nil {
			existing.CORSAllowedHeaders = s
		}
	}
	if v, ok := raw["corsAllowedMethods"]; ok {
		var s string
		if json.Unmarshal(v, &s) == nil {
			existing.CORSAllowedMethods = s
		}
	}
	if v, ok := raw["chatUploadBackend"]; ok {
		var s string
		if json.Unmarshal(v, &s) == nil {
			existing.ChatUploadBackend = s
		}
	}
	if v, ok := raw["chatUploadDiskDir"]; ok {
		var s string
		if json.Unmarshal(v, &s) == nil {
			existing.ChatUploadDiskDir = s
		}
	}
	if v, ok := raw["chatUploadS3Endpoint"]; ok {
		var s string
		if json.Unmarshal(v, &s) == nil {
			existing.ChatUploadS3Endpoint = s
		}
	}
	if v, ok := raw["chatUploadS3Bucket"]; ok {
		var s string
		if json.Unmarshal(v, &s) == nil {
			existing.ChatUploadS3Bucket = s
		}
	}
	if v, ok := raw["chatUploadS3Region"]; ok {
		var s string
		if json.Unmarshal(v, &s) == nil {
			existing.ChatUploadS3Region = s
		}
	}
	if v, ok := raw["chatUploadS3PublicUrl"]; ok {
		var s string
		if json.Unmarshal(v, &s) == nil {
			existing.ChatUploadS3PublicURL = s
		}
	}
	if v, ok := raw["logLevel"]; ok {
		var s string
		if json.Unmarshal(v, &s) == nil {
			existing.LogLevel = s
		}
	}

	// Bool fields
	if v, ok := raw["serverEnableTls"]; ok {
		var b bool
		if json.Unmarshal(v, &b) == nil {
			existing.ServerEnableTLS = b
		}
	}
	if v, ok := raw["serverUseAcme"]; ok {
		var b bool
		if json.Unmarshal(v, &b) == nil {
			existing.ServerUseACME = b
		}
	}
	if v, ok := raw["behindProxy"]; ok {
		var b bool
		if json.Unmarshal(v, &b) == nil {
			existing.BehindProxy = b
		}
	}
	if v, ok := raw["livekitExternal"]; ok {
		var b bool
		if json.Unmarshal(v, &b) == nil {
			existing.LiveKitExternal = b
		}
	}
	if v, ok := raw["corsAllowCredentials"]; ok {
		var b bool
		if json.Unmarshal(v, &b) == nil {
			existing.CORSAllowCredentials = b
		}
	}

	// Int fields
	if v, ok := raw["corsMaxAge"]; ok {
		var i int
		if json.Unmarshal(v, &i) == nil {
			existing.CORSMaxAge = i
		}
	}
	if v, ok := raw["tokenDuration"]; ok {
		var i int
		if json.Unmarshal(v, &i) == nil {
			existing.TokenDuration = i
		}
	}
	if v, ok := raw["maxParticipantsLimit"]; ok {
		var i int
		if json.Unmarshal(v, &i) == nil {
			existing.MaxParticipantsLimit = i
		}
	}
	if v, ok := raw["maxRoomsPerUser"]; ok {
		var i int
		if json.Unmarshal(v, &i) == nil {
			existing.MaxRoomsPerUser = i
		}
	}
	if v, ok := raw["chatMaxMessageCount"]; ok {
		var i int
		if json.Unmarshal(v, &i) == nil {
			existing.ChatMaxMessageCount = i
		}
	}
	if v, ok := raw["chatMessageTTLHours"]; ok {
		var i int
		if json.Unmarshal(v, &i) == nil {
			existing.ChatMessageTTLHours = i
		}
	}

	// Int64 fields
	if v, ok := raw["chatUploadMaxBytes"]; ok {
		var i int64
		if json.Unmarshal(v, &i) == nil {
			existing.ChatUploadMaxBytes = i
		}
	}
	if v, ok := raw["chatUploadInlineMax"]; ok {
		var i int64
		if json.Unmarshal(v, &i) == nil {
			existing.ChatUploadInlineMax = i
		}
	}
	if v, ok := raw["maxUploadBytesPerUser"]; ok {
		var i int64
		if json.Unmarshal(v, &i) == nil {
			existing.MaxUploadBytesPerUser = i
		}
	}
	if v, ok := raw["globalDiskThresholdBytes"]; ok {
		var i int64
		if json.Unmarshal(v, &i) == nil {
			existing.GlobalDiskThresholdBytes = i
		}
	}

	return existing
}

// maskSettings returns a copy with secret fields replaced by a placeholder.
func maskSettings(s *models.SystemSettings) *models.SystemSettings {
	cp := *s
	if cp.GoogleClientSecret != "" {
		cp.GoogleClientSecret = maskedSecret
	}
	if cp.GithubClientSecret != "" {
		cp.GithubClientSecret = maskedSecret
	}
	if cp.TwitterClientSecret != "" {
		cp.TwitterClientSecret = maskedSecret
	}
	if cp.JWTSecret != "" {
		cp.JWTSecret = maskedSecret
	}
	if cp.SessionSecret != "" {
		cp.SessionSecret = maskedSecret
	}
	if cp.LiveKitAPISecret != "" {
		cp.LiveKitAPISecret = maskedSecret
	}
	if cp.ChatUploadS3AccessKey != "" {
		cp.ChatUploadS3AccessKey = maskedSecret
	}
	if cp.ChatUploadS3SecretKey != "" {
		cp.ChatUploadS3SecretKey = maskedSecret
	}
	return &cp
}

func (h *AdminHandler) ListInviteTokens(c *fiber.Ctx) error {
	page := c.QueryInt("page", 1)
	limit := c.QueryInt("limit", 50)
	tokens, total, err := h.inviteTokenRepo.List(repository.PaginationParams{Page: page, Limit: limit})
	if err != nil {
		log.Error().Err(err).Msg("Failed to fetch invite tokens")
		return c.Status(500).JSON(fiber.Map{"error": "Failed to fetch tokens"})
	}
	if tokens == nil {
		tokens = []models.InviteToken{}
	}
	type tokenResponse struct {
		models.InviteToken
		Used bool `json:"used"`
	}
	out := make([]tokenResponse, len(tokens))
	for i := range tokens {
		out[i] = tokenResponse{InviteToken: tokens[i], Used: tokens[i].UsedAt != nil}
	}
	return c.JSON(fiber.Map{"tokens": out, "total": total})
}

func (h *AdminHandler) CreateInviteToken(c *fiber.Ctx) error {
	claims := c.Locals("user").(*auth.Claims)
	var input struct {
		Email     string `json:"email"`
		ExpiresIn int    `json:"expiresInHours"`
	}
	if err := c.BodyParser(&input); err != nil {
		return c.Status(400).JSON(fiber.Map{"error": "Invalid request body"})
	}
	if input.ExpiresIn <= 0 {
		input.ExpiresIn = 72
	}
	if input.ExpiresIn > 720 {
		return c.Status(400).JSON(fiber.Map{"error": "expiresInHours cannot exceed 720 (30 days)"})
	}

	if input.Email != "" {
		if _, err := mail.ParseAddress(input.Email); err != nil {
			return c.Status(400).JSON(fiber.Map{"error": "Invalid email format"})
		}
	}

	b := make([]byte, 16)
	if _, err := rand.Read(b); err != nil {
		return c.Status(500).JSON(fiber.Map{"error": "Failed to generate secure token"})
	}
	token := &models.InviteToken{
		ID:        uuid.NewString(),
		Token:     hex.EncodeToString(b),
		Email:     input.Email,
		CreatedBy: claims.UserID,
		ExpiresAt: time.Now().Add(time.Duration(input.ExpiresIn) * time.Hour),
	}
	if err := h.inviteTokenRepo.Create(token); err != nil {
		log.Error().Err(err).Msg("Failed to create invite token")
		return c.Status(500).JSON(fiber.Map{"error": "Failed to create token"})
	}
	return c.Status(201).JSON(token)
}

func (h *AdminHandler) DeleteInviteToken(c *fiber.Ctx) error {
	tokenID := c.Params("id")
	if err := h.inviteTokenRepo.Delete(tokenID); err != nil {
		log.Error().Err(err).Str("tokenID", tokenID).Msg("Failed to delete invite token")
		return c.Status(500).JSON(fiber.Map{"error": "Failed to delete token"})
	}
	return c.JSON(fiber.Map{"status": "success"})
}
