package queue

import (
	"context"
	"encoding/json"

	"bedrud/internal/models"

	"github.com/rs/zerolog/log"
	"gorm.io/gorm"
)

// HandleDispatchWebhook is a stub handler for dispatching webhook events.
// TODO: HTTP POST to payload.URL with HMAC-SHA256 signature header X-Bedrud-Signature.
func HandleDispatchWebhook(ctx context.Context, db *gorm.DB, job *models.Job) error {
	var payload WebhookPayload
	if err := json.Unmarshal([]byte(job.Payload), &payload); err != nil {
		return err
	}
	log.Info().Str("url", payload.URL).Str("event", payload.Event).
		Msg("queue: dispatch_webhook stub — not implemented")
	return nil
}

// NewProcessRecordingHandler creates a stub handler for processing LiveKit Egress recordings.
// TODO: Download, store, update room metadata, notify participants, dispatch webhook.
func NewProcessRecordingHandler() Handler {
	return func(ctx context.Context, db *gorm.DB, job *models.Job) error {
		var payload ProcessRecordingPayload
		if err := json.Unmarshal([]byte(job.Payload), &payload); err != nil {
			return err
		}
		log.Info().Str("roomID", payload.RoomID).Str("fileURL", payload.FileURL).
			Str("type", payload.RecordingType).
			Msg("queue: process_recording stub — not implemented")
		return nil
	}
}
