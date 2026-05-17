package queue

import (
	"context"
	"encoding/json"
	"fmt"

	"bedrud/internal/models"
	"bedrud/internal/repository"
	"bedrud/internal/services"

	"gorm.io/gorm"
)

// NewRoomDeleteHandler creates a handler that cascades room deletion.
func NewRoomDeleteHandler(
	cleanupSvc *services.RoomCleanupService,
	roomRepo *repository.RoomRepository,
) Handler {
	return func(ctx context.Context, db *gorm.DB, job *models.Job) error {
		var payload RoomDeletePayload
		if err := json.Unmarshal([]byte(job.Payload), &payload); err != nil {
			return fmt.Errorf("unmarshal room_delete payload: %w", err)
		}

		room, err := roomRepo.GetRoom(payload.RoomID)
		if err != nil {
			return fmt.Errorf("fetch room %s: %w", payload.RoomID, err)
		}
		if room == nil {
			return fmt.Errorf("room not found: %s", payload.RoomID)
		}

		opts := services.CascadeDeleteOptions{
			SystemEvent:     payload.SystemEvent,
			SystemMessage:   payload.SystemMessage,
			DeletedIdentity: payload.DeletedIdentity,
		}
		return cleanupSvc.CascadeDeleteRoom(ctx, room, opts)
	}
}
