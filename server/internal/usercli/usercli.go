package usercli

import (
	"bedrud/config"
	"bedrud/internal/auth"
	"bedrud/internal/database"
	"bedrud/internal/lkutil"
	"bedrud/internal/models"
	"bedrud/internal/repository"
	"bedrud/internal/services"
	"bedrud/internal/storage"
	"context"
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/rs/zerolog/log"
)

func PromoteUser(configPath, email string) error {
	return withUser(configPath, email, func(repo *repository.UserRepository, user *models.User) error {
		for _, a := range user.Accesses {
			if a == string(models.AccessSuperAdmin) {
				fmt.Printf("User %q already has superadmin access.\n", email)
				return nil
			}
		}
		user.Accesses = append(user.Accesses, string(models.AccessSuperAdmin))
		if err := repo.UpdateUserAccesses(user.ID, []string(user.Accesses)); err != nil {
			return fmt.Errorf("failed to update accesses: %w", err)
		}
		fmt.Printf("✓ User %q is now a superadmin.\n", email)
		return nil
	})
}

func DemoteUser(configPath, email string) error {
	return withUser(configPath, email, func(repo *repository.UserRepository, user *models.User) error {
		filtered := user.Accesses[:0]
		for _, a := range user.Accesses {
			if a != string(models.AccessSuperAdmin) {
				filtered = append(filtered, a)
			}
		}
		if len(filtered) == len(user.Accesses) {
			fmt.Printf("User %q does not have superadmin access.\n", email)
			return nil
		}
		if err := repo.UpdateUserAccesses(user.ID, []string(filtered)); err != nil {
			return fmt.Errorf("failed to update accesses: %w", err)
		}
		fmt.Printf("✓ Removed superadmin from %q.\n", email)
		return nil
	})
}

func CreateUser(configPath, email, password, name string) error {
	cfg, err := config.Load(configPath)
	if err != nil {
		return fmt.Errorf("failed to load config: %w", err)
	}
	if err := database.Initialize(&cfg.Database); err != nil {
		return fmt.Errorf("failed to open database: %w", err)
	}
	defer database.Close()

	if err := database.RunMigrations(); err != nil {
		return fmt.Errorf("failed to run migrations: %w", err)
	}

	hashedPassword, err := auth.HashPassword(password)
	if err != nil {
		return fmt.Errorf("failed to hash password: %w", err)
	}

	repo := repository.NewUserRepository(database.GetDB())
	existing, checkErr := repo.GetUserByEmail(email)
	if checkErr != nil {
		return fmt.Errorf("failed to check existing user: %w", checkErr)
	}
	if existing != nil {
		fmt.Printf("⚠ User %q already exists — skipping creation.\n", email)
		return nil
	}

	user := &models.User{
		ID:        uuid.New().String(),
		Email:     email,
		Password:  string(hashedPassword),
		Name:      name,
		Provider:  "local",
		Accesses:  models.StringArray{"user"},
		CreatedAt: time.Now(),
		UpdatedAt: time.Now(),
	}

	if err := repo.CreateUser(user); err != nil {
		return fmt.Errorf("failed to create user: %w", err)
	}

	fmt.Printf("✓ Created user: %s\n", user.Email)
	return nil
}

func DeleteUser(configPath, email string) error {
	cfg, err := config.Load(configPath)
	if err != nil {
		return fmt.Errorf("failed to load config: %w", err)
	}
	if err := database.Initialize(&cfg.Database); err != nil {
		return fmt.Errorf("failed to open database: %w", err)
	}
	defer database.Close()

	if err := database.RunMigrations(); err != nil {
		return fmt.Errorf("failed to run migrations: %w", err)
	}

	userRepo := repository.NewUserRepository(database.GetDB())
	user, err := userRepo.GetUserByEmail(email)
	if err != nil {
		return fmt.Errorf("database error: %w", err)
	}
	if user == nil {
		return fmt.Errorf("user not found: %s", email)
	}

	roomRepo := repository.NewRoomRepository(database.GetDB())
	rooms, err := roomRepo.GetRoomsCreatedByUser(user.ID)
	if err != nil {
		return fmt.Errorf("failed to fetch user rooms: %w", err)
	}

	if len(rooms) > 0 {
		client := lkutil.NewClient(&cfg.LiveKit)
		uploadDir := cfg.Chat.Uploads.DiskDir
		if uploadDir == "" {
			uploadDir = "./data/uploads/chat"
		}
		var s3Deleter storage.ObjectDeleter
		if cfg.Chat.Uploads.Backend == "s3" &&
			cfg.Chat.Uploads.S3.Endpoint != "" &&
			cfg.Chat.Uploads.S3.Bucket != "" &&
			cfg.Chat.Uploads.S3.AccessKey != "" {
			s3Deleter = storage.NewS3Deleter(cfg.Chat.Uploads.S3)
		}
		uploadTracker := storage.NewChatUploadTracker(database.GetDB(), uploadDir, s3Deleter)
		cleanupSvc := services.NewRoomCleanupService(roomRepo, client, cfg.LiveKit.APIKey, cfg.LiveKit.APISecret, uploadTracker)

		if err := cleanupSvc.DeleteUserRooms(context.Background(), rooms, user.ID); err != nil {
			fmt.Printf("⚠ Room cleanup had errors (proceeding with user deletion): %v\n", err)
			log.Warn().Err(err).Msg("room cleanup had errors during CLI user deletion")
		}
	}

	passkeyRepo := repository.NewPasskeyRepository(database.GetDB())
	prefsRepo := repository.NewUserPreferencesRepository(database.GetDB())

	if err := passkeyRepo.DeleteByUserID(user.ID); err != nil {
		return fmt.Errorf("failed to delete passkeys: %w", err)
	}
	if err := prefsRepo.DeleteByUserID(user.ID); err != nil {
		return fmt.Errorf("failed to delete preferences: %w", err)
	}
	if err := userRepo.DeleteUser(user.ID); err != nil {
		return fmt.Errorf("failed to delete user: %w", err)
	}

	fmt.Printf("✓ Deleted user: %s (%d room(s) cleaned up)\n", user.Email, len(rooms))
	return nil
}

func withUser(configPath, email string, fn func(*repository.UserRepository, *models.User) error) error {
	cfg, err := config.Load(configPath)
	if err != nil {
		return fmt.Errorf("failed to load config: %w", err)
	}
	if err := database.Initialize(&cfg.Database); err != nil {
		return fmt.Errorf("failed to open database: %w", err)
	}
	defer database.Close()

	if err := database.RunMigrations(); err != nil {
		return fmt.Errorf("failed to run migrations: %w", err)
	}

	repo := repository.NewUserRepository(database.GetDB())
	user, err := repo.GetUserByEmail(email)
	if err != nil {
		return fmt.Errorf("database error: %w", err)
	}
	if user == nil {
		return fmt.Errorf("no user found with email %q", email)
	}
	return fn(repo, user)
}
