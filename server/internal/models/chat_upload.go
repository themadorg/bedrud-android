package models

import "time"

type ChatUpload struct {
	ID             string    `json:"id" gorm:"primaryKey;type:varchar(36)"`
	RoomID         string    `json:"roomId" gorm:"not null;type:varchar(36);index;constraint:OnDelete:CASCADE"`
	UploadedBy     string    `json:"uploadedBy" gorm:"type:varchar(36);index"`
	FileHash       string    `json:"fileHash" gorm:"not null;type:varchar(64)"`
	Extension      string    `json:"extension" gorm:"type:varchar(10)"`
	FileSize       int64     `json:"fileSize" gorm:"not null;default:0"`
	StorageBackend string    `json:"storageBackend" gorm:"type:varchar(10);not null;default:'disk'"`
	CreatedAt      time.Time `json:"createdAt" gorm:"autoCreateTime;not null"`
	Room           Room      `json:"-" gorm:"foreignKey:RoomID;references:ID"`
}

func (ChatUpload) TableName() string { return "chat_uploads" }
