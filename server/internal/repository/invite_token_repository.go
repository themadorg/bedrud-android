package repository

import (
	"bedrud/internal/models"
	"errors"
	"time"

	"gorm.io/gorm"
)

type InviteTokenRepository struct {
	db *gorm.DB
}

func NewInviteTokenRepository(db *gorm.DB) *InviteTokenRepository {
	return &InviteTokenRepository{db: db}
}

func (r *InviteTokenRepository) Create(t *models.InviteToken) error {
	return r.db.Create(t).Error
}

func (r *InviteTokenRepository) List(p PaginationParams) ([]models.InviteToken, int64, error) {
	var total int64
	if err := r.db.Model(&models.InviteToken{}).Count(&total).Error; err != nil {
		return nil, 0, err
	}
	if p.Limit <= 0 || p.Limit > 100 {
		p.Limit = 50
	}
	if p.Page <= 0 {
		p.Page = 1
	}
	offset := (p.Page - 1) * p.Limit
	if offset > 10000 {
		offset = 10000
	}
	var tokens []models.InviteToken
	err := r.db.Order("created_at desc").Limit(p.Limit).Offset(offset).Find(&tokens).Error
	return tokens, total, err
}

func (r *InviteTokenRepository) GetByToken(token string) (*models.InviteToken, error) {
	var t models.InviteToken
	err := r.db.Where("token = ?", token).First(&t).Error
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil
		}
		return nil, err
	}
	return &t, nil
}

func (r *InviteTokenRepository) MarkUsed(tokenID, userID string) error {
	now := time.Now()
	result := r.db.Model(&models.InviteToken{}).
		Where("id = ? AND used_at IS NULL AND expires_at > ?", tokenID, now).
		Updates(map[string]interface{}{"used_at": now, "used_by": userID})
	if result.Error != nil {
		return result.Error
	}
	if result.RowsAffected == 0 {
		return errors.New("invite token already used, expired, or not found")
	}
	return nil
}

func (r *InviteTokenRepository) Delete(tokenID string) error {
	return r.db.Delete(&models.InviteToken{}, "id = ?", tokenID).Error
}
