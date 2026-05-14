package handlers

import (
	"github.com/gofiber/fiber/v2"
	"github.com/rs/zerolog/log"
)

// internalError returns a generic internal error response after logging the real error.
func internalError(err error) fiber.Map {
	log.Error().Err(err).Msg("Internal server error")
	return fiber.Map{"error": "An internal error occurred"}
}
