package database

import (
	"fmt"
	"os"

	"bedrud/internal/models"

	"github.com/rs/zerolog/log"
)

// RunMigrations performs all database migrations
func RunMigrations() error {
	if os.Getenv("BEDRUD_SKIP_MIGRATE") == "1" {
		log.Info().Msg("Skipping database migrations (BEDRUD_SKIP_MIGRATE=1)")
		return nil
	}

	db := GetDB()
	if db == nil {
		return fmt.Errorf("database not initialized; call database.Initialize first")
	}

	if err := db.AutoMigrate(&models.User{}); err != nil {
		return err
	}
	if err := db.AutoMigrate(&models.BlockedRefreshToken{}); err != nil {
		return err
	}
	if err := db.AutoMigrate(&models.Room{}); err != nil {
		return err
	}
	if err := db.AutoMigrate(&models.RoomParticipant{}); err != nil {
		return err
	}
	if err := db.AutoMigrate(&models.RoomPermissions{}); err != nil {
		return err
	}
	if err := db.AutoMigrate(&models.Passkey{}); err != nil {
		return err
	}
	if err := db.AutoMigrate(&models.SystemSettings{}); err != nil {
		return err
	}
	if err := db.AutoMigrate(&models.InviteToken{}); err != nil {
		return err
	}
	if err := db.AutoMigrate(&models.UserPreferences{}); err != nil {
		return err
	}
	if err := db.AutoMigrate(&models.ChatUpload{}); err != nil {
		return err
	}
	if err := db.AutoMigrate(&models.Job{}); err != nil {
		return err
	}

	// Add foreign key constraints manually (idempotent, Postgres only)
	// SQLite does not support ALTER TABLE ADD CONSTRAINT for composite FKs.
	if db.Dialector.Name() == "postgres" {
		if !db.Migrator().HasConstraint(&models.RoomPermissions{}, "fk_room_permissions_participant") {
			if err := db.Exec(`
				ALTER TABLE room_permissions
				ADD CONSTRAINT fk_room_permissions_participant
				FOREIGN KEY (room_id, user_id)
				REFERENCES room_participants(room_id, user_id)
				ON DELETE CASCADE
			`).Error; err != nil {
				log.Warn().Err(err).Msg("Failed to add foreign key constraint")
			}
		}
		if !db.Migrator().HasConstraint(&models.ChatUpload{}, "fk_chat_uploads_room") {
			if err := db.Exec(`
				ALTER TABLE chat_uploads
				ADD CONSTRAINT fk_chat_uploads_room
				FOREIGN KEY (room_id)
				REFERENCES rooms(id)
				ON DELETE CASCADE
			`).Error; err != nil {
				log.Warn().Err(err).Msg("Failed to add chat_uploads FK constraint")
			}
		}

		// Additional FK constraints for data integrity
		type fkDef struct{ name, table, column, refTable, refCol, onDelete string }
		fks := []fkDef{
			{"fk_room_participants_room", "room_participants", "room_id", "rooms", "id", "CASCADE"},
			{"fk_room_participants_user", "room_participants", "user_id", "users", "id", "CASCADE"},
			{"fk_rooms_created_by", "rooms", "created_by", "users", "id", "SET NULL"},
			{"fk_rooms_admin_id", "rooms", "admin_id", "users", "id", "SET NULL"},
			{"fk_passkeys_user", "passkeys", "user_id", "users", "id", "CASCADE"},
			{"fk_blocked_tokens_user", "blocked_refresh_tokens", "user_id", "users", "id", "CASCADE"},
			{"fk_invite_tokens_created_by", "invite_tokens", "created_by", "users", "id", "SET NULL"},
		}
		for _, fk := range fks {
			if !db.Migrator().HasConstraint(fk.table, fk.name) {
				sql := fmt.Sprintf(
					"ALTER TABLE %s ADD CONSTRAINT %s FOREIGN KEY (%s) REFERENCES %s(%s) ON DELETE %s",
					fk.table, fk.name, fk.column, fk.refTable, fk.refCol, fk.onDelete,
				)
				if err := db.Exec(sql).Error; err != nil {
					log.Warn().Err(err).Str("constraint", fk.name).Msg("Failed to add FK constraint")
				}
			}
		}
	}

	log.Info().Msg("Database migrations completed successfully")
	return nil
}
