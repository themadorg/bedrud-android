package middleware

import (
	"time"

	"bedrud/config"

	"github.com/gofiber/fiber/v2"
	"github.com/gofiber/fiber/v2/middleware/limiter"
)

func AuthRateLimiter(cfg config.RateLimitConfig) fiber.Handler {
	maxN := 10
	if cfg.AuthMaxRequests != nil {
		maxN = *cfg.AuthMaxRequests
	}
	if maxN == 0 {
		return func(c *fiber.Ctx) error { return c.Next() }
	}
	window := 60
	if cfg.AuthWindowSecs != nil {
		window = *cfg.AuthWindowSecs
	}
	return limiter.New(limiter.Config{
		Max:        maxN,
		Expiration: time.Duration(window) * time.Second,
		KeyGenerator: func(c *fiber.Ctx) string {
			return c.IP()
		},
		LimitReached: func(c *fiber.Ctx) error {
			return c.Status(fiber.StatusTooManyRequests).JSON(fiber.Map{
				"error": "too many requests, please try again later",
			})
		},
	})
}

// ResendRateLimiter provides a stricter rate limit for the verification-resend endpoint.
// Uses config fields AuthResendMaxRequests / AuthResendWindowSecs when set.
// Default: 3 requests per 60 seconds per IP.
func ResendRateLimiter(cfg config.RateLimitConfig) fiber.Handler {
	maxN := 3
	if cfg.AuthResendMaxRequests != nil {
		maxN = *cfg.AuthResendMaxRequests
	}
	if maxN == 0 {
		return func(c *fiber.Ctx) error { return c.Next() }
	}
	window := 60
	if cfg.AuthResendWindowSecs != nil {
		window = *cfg.AuthResendWindowSecs
	}
	return limiter.New(limiter.Config{
		Max:        maxN,
		Expiration: time.Duration(window) * time.Second,
		KeyGenerator: func(c *fiber.Ctx) string {
			return c.IP()
		},
		LimitReached: func(c *fiber.Ctx) error {
			return c.Status(fiber.StatusTooManyRequests).JSON(fiber.Map{
				"error": "too many requests, please try again later",
			})
		},
	})
}

func GuestRateLimiter(cfg config.RateLimitConfig) fiber.Handler {
	maxN := 5
	if cfg.GuestMaxRequests != nil {
		maxN = *cfg.GuestMaxRequests
	}
	if maxN == 0 {
		return func(c *fiber.Ctx) error { return c.Next() }
	}
	window := 60
	if cfg.GuestWindowSecs != nil {
		window = *cfg.GuestWindowSecs
	}
	return limiter.New(limiter.Config{
		Max:        maxN,
		Expiration: time.Duration(window) * time.Second,
		KeyGenerator: func(c *fiber.Ctx) string {
			return c.IP()
		},
		LimitReached: func(c *fiber.Ctx) error {
			return c.Status(fiber.StatusTooManyRequests).JSON(fiber.Map{
				"error": "too many guest join attempts",
			})
		},
	})
}

// APIRateLimiter provides rate limiting for room creation and chat uploads.
func APIRateLimiter(cfg config.RateLimitConfig) fiber.Handler {
	maxN := 30
	if cfg.APIMaxRequests != nil {
		maxN = *cfg.APIMaxRequests
	}
	if maxN == 0 {
		return func(c *fiber.Ctx) error { return c.Next() }
	}
	window := 60
	if cfg.APIWindowSecs != nil {
		window = *cfg.APIWindowSecs
	}
	return limiter.New(limiter.Config{
		Max:        maxN,
		Expiration: time.Duration(window) * time.Second,
		KeyGenerator: func(c *fiber.Ctx) string {
			return c.IP()
		},
		LimitReached: func(c *fiber.Ctx) error {
			return c.Status(fiber.StatusTooManyRequests).JSON(fiber.Map{
				"error": "too many requests",
			})
		},
	})
}
