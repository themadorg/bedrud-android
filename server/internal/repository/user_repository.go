package repository

import (
	"bedrud/internal/models"
	"crypto/sha256"
	"encoding/hex"
	"time"

	"github.com/google/uuid"
	"github.com/rs/zerolog/log"
	"gorm.io/gorm"
)

type UserRepository struct {
	db *gorm.DB
}

func NewUserRepository(db *gorm.DB) *UserRepository {
	return &UserRepository{db: db}
}

// hashToken returns the SHA-256 hex digest of a token string.
func hashToken(token string) string {
	h := sha256.Sum256([]byte(token))
	return hex.EncodeToString(h[:])
}

func (r *UserRepository) CreateOrUpdateUser(user *models.User) error {
	now := time.Now()
	user.UpdatedAt = now

	result := r.db.Where("email = ? AND provider = ?", user.Email, user.Provider).
		Assign(user).
		FirstOrCreate(user)

	if result.Error != nil {
		log.Error().Err(result.Error).Msg("Failed to create or update user")
		return result.Error
	}

	return nil
}

func (r *UserRepository) GetUserByEmailAndProvider(email, provider string) (*models.User, error) {
	var user models.User
	result := r.db.Where("email = ? AND provider = ?", email, provider).First(&user)

	if result.Error == gorm.ErrRecordNotFound {
		return nil, nil
	}

	if result.Error != nil {
		log.Error().Err(result.Error).Msg("Failed to get user")
		return nil, result.Error
	}

	return &user, nil
}

func (r *UserRepository) GetUserByEmail(email string) (*models.User, error) {
	var user models.User
	result := r.db.Where("email = ?", email).First(&user)

	if result.Error == gorm.ErrRecordNotFound {
		return nil, nil
	}

	if result.Error != nil {
		log.Error().Err(result.Error).Msg("Failed to get user by email")
		return nil, result.Error
	}

	return &user, nil
}

func (r *UserRepository) CreateUser(user *models.User) error {
	result := r.db.Create(user)
	if result.Error != nil {
		log.Error().Err(result.Error).Msg("Failed to create user")
		return result.Error
	}
	return nil
}

func (r *UserRepository) UpdateRefreshToken(userID, refreshToken string) error {
	result := r.db.Model(&models.User{}).
		Where("id = ?", userID).
		Update("refresh_token", hashToken(refreshToken))

	if result.Error != nil {
		log.Error().Err(result.Error).Msg("Failed to update refresh token")
		return result.Error
	}
	return nil
}

// MatchRefreshToken checks whether the provided raw token matches the stored hash for the user.
func (r *UserRepository) MatchRefreshToken(userID, rawToken string) bool {
	var stored string
	r.db.Model(&models.User{}).
		Where("id = ?", userID).
		Select("refresh_token").
		Row().
		Scan(&stored)
	return stored == hashToken(rawToken)
}

func (r *UserRepository) GetUserByID(id string) (*models.User, error) {
	var user models.User
	result := r.db.Where("id = ?", id).First(&user)

	if result.Error == gorm.ErrRecordNotFound {
		return nil, nil
	}

	if result.Error != nil {
		log.Error().Err(result.Error).Msg("Failed to get user by ID")
		return nil, result.Error
	}

	return &user, nil
}

func (r *UserRepository) BlockRefreshToken(userID, token string, expiresAt time.Time) error {
	blocked := &models.BlockedRefreshToken{
		ID:        uuid.New().String(),
		Token:     hashToken(token),
		UserID:    userID,
		ExpiresAt: expiresAt,
	}

	result := r.db.Create(blocked)
	return result.Error
}

func (r *UserRepository) IsRefreshTokenBlocked(token string) bool {
	var count int64
	r.db.Model(&models.BlockedRefreshToken{}).
		Where("token = ? AND expires_at > ?", hashToken(token), time.Now()).
		Count(&count)
	return count > 0
}

func (r *UserRepository) CleanupBlockedTokens() error {
	result := r.db.Where("expires_at < ?", time.Now()).
		Delete(&models.BlockedRefreshToken{})
	return result.Error
}

func (r *UserRepository) UpdateUserAccesses(userID string, accesses []string) error {
	result := r.db.Model(&models.User{}).
		Where("id = ?", userID).
		Update("accesses", models.StringArray(accesses))

	return result.Error
}

// UpdateAccessesAndClearToken atomically updates accesses and clears the refresh token.
// Used when changing user roles to force re-login with correct accesses in the JWT.
func (r *UserRepository) UpdateAccessesAndClearToken(userID string, accesses []string) error {
	result := r.db.Model(&models.User{}).Where("id = ?", userID).Updates(map[string]any{
		"accesses":      models.StringArray(accesses),
		"refresh_token": "",
		"updated_at":    time.Now(),
	})
	if result.Error != nil {
		log.Error().Err(result.Error).Str("userID", userID).Msg("Failed to update accesses and clear token")
		return result.Error
	}
	if result.RowsAffected == 0 {
		return gorm.ErrRecordNotFound
	}
	return nil
}

// ClearRefreshToken removes the stored refresh token for a user, invalidating all sessions.
func (r *UserRepository) ClearRefreshToken(userID string) error {
	result := r.db.Model(&models.User{}).
		Where("id = ?", userID).
		Update("refresh_token", "")
	if result.Error != nil {
		log.Error().Err(result.Error).Str("userID", userID).Msg("Failed to clear refresh token")
		return result.Error
	}
	if result.RowsAffected == 0 {
		return gorm.ErrRecordNotFound
	}
	return nil
}

// UpdateUserStatusAndClearToken atomically updates is_active and clears the refresh token.
// Used when banning a user to immediately invalidate all sessions.
func (r *UserRepository) UpdateUserStatusAndClearToken(userID string, active bool) error {
	result := r.db.Model(&models.User{}).Where("id = ?", userID).Updates(map[string]any{
		"is_active":     active,
		"refresh_token": "",
		"updated_at":    time.Now(),
	})
	if result.Error != nil {
		log.Error().Err(result.Error).Str("userID", userID).Msg("Failed to update status and clear token")
		return result.Error
	}
	if result.RowsAffected == 0 {
		return gorm.ErrRecordNotFound
	}
	return nil
}

func (r *UserRepository) UpdatePassword(userID, hashedPassword string) error {
	result := r.db.Model(&models.User{}).Where("id = ?", userID).Updates(map[string]any{
		"password":      hashedPassword,
		"refresh_token": "",
		"updated_at":    time.Now(),
	})
	if result.Error != nil {
		log.Error().Err(result.Error).Str("userID", userID).Msg("Failed to update password")
		return result.Error
	}
	if result.RowsAffected == 0 {
		return gorm.ErrRecordNotFound
	}
	return nil
}

func (r *UserRepository) GetUsersByAccess(access models.AccessLevel) ([]models.User, error) {
	var users []models.User
	err := r.db.Where("? = ANY(accesses)", string(access)).Find(&users).Error
	return users, err
}

// UpdateUser updates an existing user
func (r *UserRepository) UpdateUser(user *models.User) error {
	user.UpdatedAt = time.Now()
	result := r.db.Save(user)
	if result.Error != nil {
		log.Error().Err(result.Error).Msg("Failed to update user")
		return result.Error
	}
	return nil
}

// DeleteUser deletes a user by ID
func (r *UserRepository) DeleteUser(userID string) error {
	return r.db.Transaction(func(tx *gorm.DB) error {
		if err := tx.Delete(&models.Passkey{}, "user_id = ?", userID).Error; err != nil {
			return err
		}
		if err := tx.Delete(&models.UserPreferences{}, "user_id = ?", userID).Error; err != nil {
			return err
		}
		if err := tx.Delete(&models.RoomParticipant{}, "user_id = ?", userID).Error; err != nil {
			return err
		}
		if err := tx.Delete(&models.RoomPermissions{}, "user_id = ?", userID).Error; err != nil {
			return err
		}
		if err := tx.Delete(&models.BlockedRefreshToken{}, "user_id = ?", userID).Error; err != nil {
			return err
		}
		return tx.Delete(&models.User{}, "id = ?", userID).Error
	})
}

// PaginationParams holds page and limit for paginated queries.
type PaginationParams struct {
	Page  int
	Limit int
}

// GetAllUsers returns a paginated list of users and the total count.
func (r *UserRepository) GetAllUsers(p PaginationParams) ([]models.User, int64, error) {
	if p.Limit <= 0 || p.Limit > 100 {
		p.Limit = 50
	}
	if p.Page <= 0 {
		p.Page = 1
	}
	offset := (p.Page - 1) * p.Limit
	var total int64
	var users []models.User
	if err := r.db.Model(&models.User{}).Count(&total).Error; err != nil {
		return nil, 0, err
	}
	err := r.db.Limit(p.Limit).Offset(offset).Find(&users).Error
	return users, total, err
}
