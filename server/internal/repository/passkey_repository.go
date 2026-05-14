package repository

import (
	"bedrud/internal/models"
	"fmt"

	"gorm.io/gorm"
)

type PasskeyRepository struct {
	db *gorm.DB
}

func NewPasskeyRepository(db *gorm.DB) *PasskeyRepository {
	return &PasskeyRepository{db: db}
}

func (r *PasskeyRepository) CreatePasskey(passkey *models.Passkey) error {
	return r.db.Create(passkey).Error
}

func (r *PasskeyRepository) GetPasskeyByCredentialID(credentialID []byte) (*models.Passkey, error) {
	var passkey models.Passkey
	err := r.db.Where("credential_id = ?", credentialID).First(&passkey).Error
	if err == gorm.ErrRecordNotFound {
		return nil, nil
	}
	return &passkey, err
}

func (r *PasskeyRepository) GetPasskeysByUserID(userID string) ([]models.Passkey, error) {
	var passkeys []models.Passkey
	err := r.db.Where("user_id = ?", userID).Find(&passkeys).Error
	return passkeys, err
}

func (r *PasskeyRepository) UpdatePasskeyCounter(credentialID []byte, counter uint32) error {
	result := r.db.Model(&models.Passkey{}).
		Where("credential_id = ? AND counter < ?", credentialID, counter).
		Update("counter", counter)
	if result.Error != nil {
		return result.Error
	}
	if result.RowsAffected == 0 {
		return fmt.Errorf("passkey counter not updated: credential not found or possible cloned authenticator (counter not advancing)")
	}
	return nil
}

func (r *PasskeyRepository) DeletePasskey(passkeyID, userID string) error {
	return r.db.Where("id = ? AND user_id = ?", passkeyID, userID).Delete(&models.Passkey{}).Error
}

func (r *PasskeyRepository) DeleteByUserID(userID string) error {
	return r.db.Delete(&models.Passkey{}, "user_id = ?", userID).Error
}
