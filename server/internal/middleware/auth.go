package middleware

import (
	"bedrud/config"
	"bedrud/internal/auth"
	"strings"

	"bedrud/internal/models"

	"github.com/gofiber/fiber/v2"
)

const bearerPrefix = "bearer "

// Protected middleware validates JWT and checks if user is active (not banned).
func Protected() fiber.Handler {
	return func(c *fiber.Ctx) error {
		token := ""

		// Prefer Authorization header
		if authHeader := c.Get("Authorization"); authHeader != "" {
			if strings.HasPrefix(strings.ToLower(authHeader), bearerPrefix) {
				token = authHeader[7:]
			} else {
				token = authHeader
			}
		}

		// Fallback to HTTP-only cookie
		if token == "" {
			token = c.Cookies("access_token")
		}

		if token == "" {
			return c.Status(fiber.StatusUnauthorized).JSON(fiber.Map{
				"error": "Missing authorization",
			})
		}

		claims, err := auth.ValidateToken(token, config.Get())
		if err != nil {
			return c.Status(fiber.StatusUnauthorized).JSON(fiber.Map{
				"error": "Invalid token",
			})
		}

		// Check if user is banned/deactivated (in-memory cache, no DB per request)
		if auth.IsUserBanned(claims.UserID) {
			return c.Status(fiber.StatusForbidden).JSON(fiber.Map{
				"error": "Account is deactivated",
			})
		}

		// Add claims to context for use in protected routes
		c.Locals("user", claims)
		return c.Next()
	}
}

// accessLevelWeight maps access levels to numeric weights for hierarchical checks.
// Higher weight = more privileges. Superadmin passes admin/user checks; admin passes user checks.
var accessLevelWeight = map[models.AccessLevel]int{
	models.AccessSuperAdmin: 3,
	models.AccessAdmin:      2,
	models.AccessUser:       1,
}

// RequireAccess middleware checks for specific access level or higher.
// Hierarchical: superadmin passes admin and user checks; admin passes user checks.
func RequireAccess(requiredAccess models.AccessLevel) fiber.Handler {
	requiredWeight, ok := accessLevelWeight[requiredAccess]
	if !ok {
		requiredWeight = 0
	}

	return func(c *fiber.Ctx) error {
		raw := c.Locals("user")
		if raw == nil {
			return c.Status(fiber.StatusUnauthorized).JSON(fiber.Map{"error": "Unauthorized"})
		}
		claims, ok := raw.(*auth.Claims)
		if !ok || claims == nil {
			return c.Status(fiber.StatusUnauthorized).JSON(fiber.Map{"error": "Unauthorized"})
		}

		for _, access := range claims.Accesses {
			if w, exists := accessLevelWeight[models.AccessLevel(access)]; exists && w >= requiredWeight {
				return c.Next()
			}
		}

		return c.Status(fiber.StatusForbidden).JSON(fiber.Map{
			"error": "Insufficient access rights",
		})
	}
}

// RequireBearerForMutations rejects state-changing requests that rely on cookie auth
// (no Authorization header). Prevents CSRF where external forms include cookies.
func RequireBearerForMutations() fiber.Handler {
	return func(c *fiber.Ctx) error {
		if c.Method() == "POST" || c.Method() == "PUT" || c.Method() == "DELETE" || c.Method() == "PATCH" {
			authHeader := c.Get("Authorization")
			if authHeader == "" || !strings.HasPrefix(strings.ToLower(authHeader), bearerPrefix) {
				return c.Status(fiber.StatusUnauthorized).JSON(fiber.Map{
					"error": "Authorization header required for state-changing requests",
				})
			}
		}
		return c.Next()
	}
}

// RejectGuest returns a middleware that blocks requests from guest users.
// Use for profile/password/account endpoints that need a persistent identity.
func RejectGuest() fiber.Handler {
	return func(c *fiber.Ctx) error {
		raw := c.Locals("user")
		if raw == nil {
			return c.Status(fiber.StatusUnauthorized).JSON(fiber.Map{"error": "Unauthorized"})
		}
		claims, ok := raw.(*auth.Claims)
		if !ok || claims == nil {
			return c.Status(fiber.StatusUnauthorized).JSON(fiber.Map{"error": "Unauthorized"})
		}
		if claims.Provider == "guest" {
			return c.Status(fiber.StatusForbidden).JSON(fiber.Map{"error": "Not available for guest accounts"})
		}
		return c.Next()
	}
}

// Example usage:
// app.Get("/admin", middleware.Protected(), middleware.RequireAccess(models.AccessAdmin), adminHandler)
