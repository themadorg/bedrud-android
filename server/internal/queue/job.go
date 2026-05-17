package queue

// UserDeletePayload carries data for hard-deleting a user and their rooms.
type UserDeletePayload struct {
	UserID  string   `json:"user_id"`
	Email   string   `json:"email"`
	RoomIDs []string `json:"room_ids,omitempty"`
}

// RoomDeletePayload carries data for cascading room deletion.
type RoomDeletePayload struct {
	RoomID          string `json:"room_id"`
	SystemEvent     string `json:"system_event"`
	SystemMessage   string `json:"system_message"`
	DeletedIdentity string `json:"deleted_identity,omitempty"`
}

// RoomSuspendPayload carries data for suspending a room.
type RoomSuspendPayload struct {
	RoomID string `json:"room_id"`
}

// ChatUploadS3Payload carries data for async S3 chat image upload.
// Data is base64-encoded image bytes.
type ChatUploadS3Payload struct {
	Data     string `json:"data"`
	RoomID   string `json:"room_id"`
	MimeType string `json:"mime_type"`
	UserID   string `json:"user_id"`
}

// SendEmailPayload carries data for sending transactional emails.
type SendEmailPayload struct {
	To           string         `json:"to"`
	Subject      string         `json:"subject"`
	TemplateName string         `json:"template_name"` // "welcome", "room_invite", "password_reset"
	TemplateData map[string]any `json:"template_data,omitempty"`
}

// WebhookPayload carries data for dispatching a webhook event.
type WebhookPayload struct {
	URL        string         `json:"url"`
	Event      string         `json:"event"` // "room.created", "room.ended", "participant.joined"
	Body       map[string]any `json:"body"`
	Secret     string         `json:"secret,omitempty"`
	MaxRetries int            `json:"max_retries,omitempty"`
}

// ProcessRecordingPayload carries data for processing a recording after Egress.
type ProcessRecordingPayload struct {
	RoomID        string `json:"room_id"`
	RoomName      string `json:"room_name"`
	EgressID      string `json:"egress_id"`
	FileURL       string `json:"file_url"`
	FileSize      int64  `json:"file_size"`
	RecordingType string `json:"recording_type"` // "audio", "video", "screen", "composite"
	DurationMs    int    `json:"duration_ms"`
	StartedAt     string `json:"started_at,omitempty"` // RFC3339
}
