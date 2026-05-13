package models

import "time"

type ChatUpload struct {
	ID        string    `json:"id" gorm:"primaryKey;type:varchar(36)"`
	RoomID    string    `json:"roomId" gorm:"not null;type:varchar(36);index"`
	FileHash  string    `json:"fileHash" gorm:"not null;type:varchar(64)"`
	Extension string    `json:"extension" gorm:"type:varchar(10)"`
	CreatedAt time.Time `json:"createdAt" gorm:"autoCreateTime;not null"`
}

func (ChatUpload) TableName() string { return "chat_uploads" }
