package middleware

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"bedrud/config"

	"github.com/gofiber/fiber/v2"
)

func intPtr(i int) *int { return &i }

func setupRLApp(handler fiber.Handler) *fiber.App {
	app := fiber.New()
	app.Use(handler)
	app.Get("/test", func(c *fiber.Ctx) error {
		return c.SendString("ok")
	})
	return app
}

func TestAuthRateLimiter_DefaultLimits(t *testing.T) {
	app := setupRLApp(AuthRateLimiter(config.RateLimitConfig{}))

	for i := 0; i < 10; i++ {
		resp, err := app.Test(httptest.NewRequest(http.MethodGet, "/test", nil))
		if err != nil {
			t.Fatalf("request %d: %v", i, err)
		}
		resp.Body.Close()
		if resp.StatusCode != http.StatusOK {
			t.Fatalf("request %d: expected 200, got %d", i, resp.StatusCode)
		}
	}

	resp, err := app.Test(httptest.NewRequest(http.MethodGet, "/test", nil))
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusTooManyRequests {
		t.Fatalf("expected 429, got %d", resp.StatusCode)
	}
}

func TestAuthRateLimiter_CustomLimits(t *testing.T) {
	app := setupRLApp(AuthRateLimiter(config.RateLimitConfig{
		AuthMaxRequests: intPtr(3),
		AuthWindowSecs:  intPtr(60),
	}))

	for i := 0; i < 3; i++ {
		resp, err := app.Test(httptest.NewRequest(http.MethodGet, "/test", nil))
		if err != nil {
			t.Fatalf("request %d: %v", i, err)
		}
		resp.Body.Close()
		if resp.StatusCode != http.StatusOK {
			t.Fatalf("request %d: expected 200, got %d", i, resp.StatusCode)
		}
	}

	resp, err := app.Test(httptest.NewRequest(http.MethodGet, "/test", nil))
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusTooManyRequests {
		t.Fatalf("expected 429, got %d", resp.StatusCode)
	}
}

func TestAuthRateLimiter_Disabled(t *testing.T) {
	app := setupRLApp(AuthRateLimiter(config.RateLimitConfig{
		AuthMaxRequests: intPtr(0),
	}))

	for i := 0; i < 100; i++ {
		resp, err := app.Test(httptest.NewRequest(http.MethodGet, "/test", nil))
		if err != nil {
			t.Fatalf("request %d: %v", i, err)
		}
		resp.Body.Close()
		if resp.StatusCode != http.StatusOK {
			t.Fatalf("request %d: expected 200, got %d", i, resp.StatusCode)
		}
	}
}

func TestAuthRateLimiter_LimitReachedBody(t *testing.T) {
	app := setupRLApp(AuthRateLimiter(config.RateLimitConfig{
		AuthMaxRequests: intPtr(1),
	}))

	app.Test(httptest.NewRequest(http.MethodGet, "/test", nil))

	resp, err := app.Test(httptest.NewRequest(http.MethodGet, "/test", nil))
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()

	var body map[string]string
	if err := json.NewDecoder(resp.Body).Decode(&body); err != nil {
		t.Fatalf("failed to decode body: %v", err)
	}
	if body["error"] != "too many requests, please try again later" {
		t.Fatalf("unexpected error message: %q", body["error"])
	}
}

func TestAuthRateLimiter_WindowDefaultWhenNil(t *testing.T) {
	app := setupRLApp(AuthRateLimiter(config.RateLimitConfig{
		AuthMaxRequests: intPtr(5),
	}))

	for i := 0; i < 5; i++ {
		resp, err := app.Test(httptest.NewRequest(http.MethodGet, "/test", nil))
		if err != nil {
			t.Fatalf("request %d: %v", i, err)
		}
		resp.Body.Close()
		if resp.StatusCode != http.StatusOK {
			t.Fatalf("request %d: expected 200, got %d", i, resp.StatusCode)
		}
	}

	resp, err := app.Test(httptest.NewRequest(http.MethodGet, "/test", nil))
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusTooManyRequests {
		t.Fatalf("expected 429, got %d", resp.StatusCode)
	}
}

func TestGuestRateLimiter_DefaultLimits(t *testing.T) {
	app := setupRLApp(GuestRateLimiter(config.RateLimitConfig{}))

	for i := 0; i < 5; i++ {
		resp, err := app.Test(httptest.NewRequest(http.MethodGet, "/test", nil))
		if err != nil {
			t.Fatalf("request %d: %v", i, err)
		}
		resp.Body.Close()
		if resp.StatusCode != http.StatusOK {
			t.Fatalf("request %d: expected 200, got %d", i, resp.StatusCode)
		}
	}

	resp, err := app.Test(httptest.NewRequest(http.MethodGet, "/test", nil))
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusTooManyRequests {
		t.Fatalf("expected 429, got %d", resp.StatusCode)
	}
}

func TestGuestRateLimiter_Disabled(t *testing.T) {
	app := setupRLApp(GuestRateLimiter(config.RateLimitConfig{
		GuestMaxRequests: intPtr(0),
	}))

	for i := 0; i < 100; i++ {
		resp, err := app.Test(httptest.NewRequest(http.MethodGet, "/test", nil))
		if err != nil {
			t.Fatalf("request %d: %v", i, err)
		}
		resp.Body.Close()
		if resp.StatusCode != http.StatusOK {
			t.Fatalf("request %d: expected 200, got %d", i, resp.StatusCode)
		}
	}
}

func TestGuestRateLimiter_ErrorBody(t *testing.T) {
	app := setupRLApp(GuestRateLimiter(config.RateLimitConfig{
		GuestMaxRequests: intPtr(1),
	}))

	app.Test(httptest.NewRequest(http.MethodGet, "/test", nil))

	resp, err := app.Test(httptest.NewRequest(http.MethodGet, "/test", nil))
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()

	var body map[string]string
	if err := json.NewDecoder(resp.Body).Decode(&body); err != nil {
		t.Fatalf("failed to decode body: %v", err)
	}
	if body["error"] != "too many guest join attempts" {
		t.Fatalf("unexpected error message: %q", body["error"])
	}
}

func TestBothLimiters_Independent(t *testing.T) {
	app := fiber.New()

	authGroup := app.Group("/auth")
	authGroup.Use(AuthRateLimiter(config.RateLimitConfig{
		AuthMaxRequests: intPtr(2),
	}))
	authGroup.Get("/login", func(c *fiber.Ctx) error {
		return c.SendString("auth ok")
	})

	guestGroup := app.Group("/guest")
	guestGroup.Use(GuestRateLimiter(config.RateLimitConfig{
		GuestMaxRequests: intPtr(2),
	}))
	guestGroup.Get("/join", func(c *fiber.Ctx) error {
		return c.SendString("guest ok")
	})

	for i := 0; i < 2; i++ {
		resp, err := app.Test(httptest.NewRequest(http.MethodGet, "/auth/login", nil))
		if err != nil {
			t.Fatalf("auth request %d: %v", i, err)
		}
		resp.Body.Close()
		if resp.StatusCode != http.StatusOK {
			t.Fatalf("auth request %d: expected 200, got %d", i, resp.StatusCode)
		}

		resp, err = app.Test(httptest.NewRequest(http.MethodGet, "/guest/join", nil))
		if err != nil {
			t.Fatalf("guest request %d: %v", i, err)
		}
		resp.Body.Close()
		if resp.StatusCode != http.StatusOK {
			t.Fatalf("guest request %d: expected 200, got %d", i, resp.StatusCode)
		}
	}

	resp, err := app.Test(httptest.NewRequest(http.MethodGet, "/auth/login", nil))
	if err != nil {
		t.Fatal(err)
	}
	resp.Body.Close()
	if resp.StatusCode != http.StatusTooManyRequests {
		t.Fatalf("auth blocked: expected 429, got %d", resp.StatusCode)
	}

	resp, err = app.Test(httptest.NewRequest(http.MethodGet, "/guest/join", nil))
	if err != nil {
		t.Fatal(err)
	}
	resp.Body.Close()
	if resp.StatusCode != http.StatusTooManyRequests {
		t.Fatalf("guest blocked: expected 429, got %d", resp.StatusCode)
	}
}

func TestAuthRateLimiter_ExplicitWindow(t *testing.T) {
	app := setupRLApp(AuthRateLimiter(config.RateLimitConfig{
		AuthMaxRequests: intPtr(2),
		AuthWindowSecs:  intPtr(30),
	}))

	for i := 0; i < 2; i++ {
		resp, err := app.Test(httptest.NewRequest(http.MethodGet, "/test", nil))
		if err != nil {
			t.Fatalf("request %d: %v", i, err)
		}
		resp.Body.Close()
		if resp.StatusCode != http.StatusOK {
			t.Fatalf("request %d: expected 200, got %d", i, resp.StatusCode)
		}
	}

	resp, err := app.Test(httptest.NewRequest(http.MethodGet, "/test", nil))
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusTooManyRequests {
		t.Fatalf("expected 429, got %d", resp.StatusCode)
	}
}

func TestAuthRateLimiter_ExactLimitNotBlocked(t *testing.T) {
	app := setupRLApp(AuthRateLimiter(config.RateLimitConfig{
		AuthMaxRequests: intPtr(3),
	}))

	for i := 0; i < 3; i++ {
		resp, err := app.Test(httptest.NewRequest(http.MethodGet, "/test", nil))
		if err != nil {
			t.Fatalf("request %d: %v", i, err)
		}
		resp.Body.Close()
		if resp.StatusCode != http.StatusOK {
			t.Fatalf("request %d: expected 200, got %d", i, resp.StatusCode)
		}
	}
}
