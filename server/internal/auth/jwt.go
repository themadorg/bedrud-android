package auth

import (
	"bedrud/config"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"sync"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"
)

// ErrTokenRevoked is returned when a token has been explicitly revoked (e.g. on logout).
var ErrTokenRevoked = errors.New("token has been revoked")

// tokenHash returns the hex-encoded SHA-256 hash of a token string.
// Storing hashes instead of full JWTs reduces memory usage in the revocation set.
func tokenHash(token string) string {
	h := sha256.Sum256([]byte(token))
	return hex.EncodeToString(h[:])
}

// revokedSet holds revoked access tokens keyed by their SHA-256 hash, with their expiry time.
//
// TRADE-OFF: The revocation list is kept in-memory and is lost on server restart.
// After a restart, revoked tokens remain valid until their natural expiry (up to TokenDuration).
// This mainly affects tokens explicitly revoked on logout. To close the window:
//   - Reduce tokenDuration in config.yaml (default 24h)
//   - Or persist revoked hashes to a DB table (check BlockedRefreshToken model for pattern)
type revokedSet struct {
	mu sync.RWMutex
	m  map[string]time.Time
}

var revokedTokens = &revokedSet{m: make(map[string]time.Time)}

// RevokeAccessToken marks a JWT as invalid until its natural expiry.
func RevokeAccessToken(tokenStr string, cfg *config.Config) {
	claims, err := parseTokenUnchecked(tokenStr, cfg)
	if err != nil {
		return
	}
	exp := time.Unix(claims.ExpiresAt.Unix(), 0)
	h := tokenHash(tokenStr)
	revokedTokens.mu.Lock()
	revokedTokens.m[h] = exp
	revokedTokens.mu.Unlock()
}

// PruneRevokedTokens removes expired entries from the revocation set. Call periodically (e.g. hourly).
func PruneRevokedTokens() {
	now := time.Now()
	revokedTokens.mu.Lock()
	defer revokedTokens.mu.Unlock()
	for h, exp := range revokedTokens.m {
		if now.After(exp) {
			delete(revokedTokens.m, h)
		}
	}
}

func isRevoked(tokenStr string) bool {
	h := tokenHash(tokenStr)
	revokedTokens.mu.RLock()
	defer revokedTokens.mu.RUnlock()
	exp, exists := revokedTokens.m[h]
	return exists && time.Now().Before(exp)
}

// bannedUsers holds IDs of deactivated users for fast middleware checks.
// Populated when an admin bans a user; removed when unbanned.
var bannedUsers = &struct {
	mu sync.RWMutex
	m  map[string]struct{}
}{m: make(map[string]struct{})}

// BanUser adds a user ID to the in-memory banned set.
func BanUser(userID string) {
	bannedUsers.mu.Lock()
	bannedUsers.m[userID] = struct{}{}
	bannedUsers.mu.Unlock()
}

// UnbanUser removes a user ID from the in-memory banned set.
func UnbanUser(userID string) {
	bannedUsers.mu.Lock()
	delete(bannedUsers.m, userID)
	bannedUsers.mu.Unlock()
}

// LoadBannedUsersFromDB populates the in-memory banned set from a list of inactive user IDs.
// Call once on server startup after DB initialization.
func LoadBannedUsersFromDB(userIDs []string) {
	bannedUsers.mu.Lock()
	defer bannedUsers.mu.Unlock()
	for _, id := range userIDs {
		bannedUsers.m[id] = struct{}{}
	}
}

// IsUserBanned checks if a user ID is in the banned set.
func IsUserBanned(userID string) bool {
	bannedUsers.mu.RLock()
	defer bannedUsers.mu.RUnlock()
	_, exists := bannedUsers.m[userID]
	return exists
}

type Claims struct {
	UserID   string   `json:"userId"`
	Email    string   `json:"email"`
	Name     string   `json:"name"`
	Provider string   `json:"provider"`
	Accesses []string `json:"accesses"`
	jwt.RegisteredClaims
}

func GenerateToken(userID, email, name, provider string, accesses []string, cfg *config.Config) (string, error) {
	expirationTime := time.Now().Add(time.Duration(cfg.Auth.TokenDuration.Int()) * time.Hour)

	claims := &Claims{
		UserID:   userID,
		Email:    email,
		Name:     name,
		Provider: provider,
		Accesses: accesses,
		RegisteredClaims: jwt.RegisteredClaims{
			Issuer:    "bedrud",
			Subject:   userID,
			Audience:  []string{"bedrud"},
			ExpiresAt: jwt.NewNumericDate(expirationTime),
			IssuedAt:  jwt.NewNumericDate(time.Now()),
		},
	}

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	tokenString, err := token.SignedString([]byte(cfg.Auth.JWTSecret))
	if err != nil {
		return "", err
	}

	return tokenString, nil
}

// parseTokenUnchecked parses and verifies the JWT signature/expiry WITHOUT checking revocation.
// Used internally so RevokeAccessToken can read expiry without triggering an infinite loop.
func parseTokenUnchecked(tokenString string, cfg *config.Config) (*Claims, error) {
	claims := &Claims{}
	token, err := jwt.ParseWithClaims(tokenString, claims, func(token *jwt.Token) (interface{}, error) {
		if _, ok := token.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, fmt.Errorf("unexpected signing method: %v", token.Header["alg"])
		}
		return []byte(cfg.Auth.JWTSecret), nil
	},
		jwt.WithIssuer("bedrud"),
		jwt.WithAudience("bedrud"),
	)
	if err != nil {
		return nil, err
	}

	if !token.Valid {
		return nil, fmt.Errorf("invalid token")
	}

	return claims, nil
}

func ValidateToken(tokenString string, cfg *config.Config) (*Claims, error) {
	claims, err := parseTokenUnchecked(tokenString, cfg)
	if err != nil {
		return nil, err
	}

	if isRevoked(tokenString) {
		return nil, ErrTokenRevoked
	}

	return claims, nil
}

func GenerateTokenPair(userID, email, name string, accesses []string, cfg *config.Config) (accessToken, refreshToken string, err error) {
	// Generate access token
	accessToken, err = GenerateToken(userID, email, name, "local", accesses, cfg)
	if err != nil {
		return "", "", err
	}

	// Generate refresh token
	refreshClaims := &Claims{
		UserID:   userID,
		Email:    email,
		Name:     name,
		Provider: "local",
		Accesses: accesses,
		RegisteredClaims: jwt.RegisteredClaims{
			Issuer:    "bedrud",
			Subject:   userID,
			Audience:  []string{"bedrud"},
			ExpiresAt: jwt.NewNumericDate(time.Now().Add(time.Hour * 24 * 7)), // 7 days
			IssuedAt:  jwt.NewNumericDate(time.Now()),
			ID:        uuid.New().String(),
		},
	}

	rt := jwt.NewWithClaims(jwt.SigningMethodHS256, refreshClaims)
	refreshToken, err = rt.SignedString([]byte(cfg.Auth.JWTSecret))
	if err != nil {
		return "", "", err
	}

	return accessToken, refreshToken, nil
}
