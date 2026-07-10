package handlers

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/gofiber/fiber/v2"
)

func TestGuestIDCookie_RoundTrip(t *testing.T) {
	secret := "test-session-secret-for-guest-id"
	h := &RoomHandler{guestIDSecret: secret, secureCookies: false}

	app := fiber.New()
	var firstID string
	app.Get("/mint", func(c *fiber.Ctx) error {
		firstID = h.resolveGuestIdentity(c)
		return c.SendString(firstID)
	})
	app.Get("/reuse", func(c *fiber.Ctx) error {
		return c.SendString(h.resolveGuestIdentity(c))
	})

	req := httptest.NewRequest(http.MethodGet, "/mint", nil)
	resp, err := app.Test(req)
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	if !strings.HasPrefix(firstID, "guest-") {
		t.Fatalf("expected guest- prefix, got %q", firstID)
	}
	var cookieVal string
	for _, c := range resp.Cookies() {
		if c.Name == guestIDCookie {
			cookieVal = c.Value
		}
	}
	if cookieVal == "" {
		t.Fatal("expected guest cookie set")
	}

	req2 := httptest.NewRequest(http.MethodGet, "/reuse", nil)
	req2.AddCookie(&http.Cookie{Name: guestIDCookie, Value: cookieVal})
	resp2, err := app.Test(req2)
	if err != nil {
		t.Fatal(err)
	}
	defer resp2.Body.Close()
	// body is second identity
	buf := make([]byte, 64)
	n, _ := resp2.Body.Read(buf)
	second := string(buf[:n])
	if second != firstID {
		t.Fatalf("stable identity: first=%q second=%q", firstID, second)
	}
}

func TestParseGuestIDCookie_RejectsTamper(t *testing.T) {
	secret := "s"
	id := "guest-abc123"
	good := id + "." + guestIDMAC(secret, id)
	if got, ok := parseGuestIDCookie(secret, good); !ok || got != id {
		t.Fatalf("good cookie failed: %q %v", got, ok)
	}
	if _, ok := parseGuestIDCookie(secret, id+".deadbeef"); ok {
		t.Fatal("tampered mac accepted")
	}
	if _, ok := parseGuestIDCookie(secret, "notguest-x.mac"); ok {
		t.Fatal("bad prefix accepted")
	}
}
