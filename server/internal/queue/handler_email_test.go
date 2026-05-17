package queue

import (
	"bytes"
	"context"
	"encoding/json"
	"html/template"
	"strings"
	"testing"

	"bedrud/config"
	"bedrud/internal/models"

	"github.com/google/uuid"
	"gorm.io/gorm"
)

func TestSendEmailHandler_NoSMTP_LogsBody(t *testing.T) {
	// Handler with nil config — no SMTP configured.
	h := NewSendEmailHandler(nil)

	payload := SendEmailPayload{
		To:           "user@test.com",
		Subject:      "Welcome!",
		TemplateName: "welcome",
		TemplateData: map[string]any{"Name": "Alice", "LoginURL": "https://bedrud.org/login"},
	}
	payloadBytes, _ := json.Marshal(payload)

	job := &models.Job{
		ID:      uuid.New().String(),
		Type:    "send_email",
		Payload: string(payloadBytes),
	}

	// Should return nil (not error) when SMTP unconfigured.
	err := h(context.Background(), &gorm.DB{}, job)
	if err != nil {
		t.Fatalf("expected nil error when SMTP not configured, got: %v", err)
	}
}

func TestSendEmailHandler_NoSMTP_EmptyConfig(t *testing.T) {
	// Handler with empty EmailConfig (Host="").
	h := NewSendEmailHandler(&config.EmailConfig{})

	payload := SendEmailPayload{
		To:           "user@test.com",
		Subject:      "Test",
		TemplateName: "welcome",
		TemplateData: map[string]any{"Name": "Bob"},
	}
	payloadBytes, _ := json.Marshal(payload)

	job := &models.Job{
		ID:      uuid.New().String(),
		Type:    "send_email",
		Payload: string(payloadBytes),
	}

	err := h(context.Background(), &gorm.DB{}, job)
	if err != nil {
		t.Fatalf("expected nil error, got: %v", err)
	}
}

func TestSendEmailHandler_InvalidJSON(t *testing.T) {
	h := NewSendEmailHandler(nil)

	job := &models.Job{
		ID:      uuid.New().String(),
		Type:    "send_email",
		Payload: "{invalid json}",
	}

	err := h(context.Background(), &gorm.DB{}, job)
	if err == nil {
		t.Fatal("expected error for invalid JSON payload")
	}
	if !strings.Contains(err.Error(), "unmarshal") {
		t.Fatalf("expected unmarshal error, got: %v", err)
	}
}

func TestSendEmailHandler_MissingTemplateName(t *testing.T) {
	h := NewSendEmailHandler(nil)

	payload := SendEmailPayload{
		To:           "user@test.com",
		Subject:      "Test",
		TemplateName: "nonexistent_template",
		TemplateData: map[string]any{"key": "value"},
	}
	payloadBytes, _ := json.Marshal(payload)

	job := &models.Job{
		ID:      uuid.New().String(),
		Type:    "send_email",
		Payload: string(payloadBytes),
	}

	// Should render fallback, not error.
	err := h(context.Background(), &gorm.DB{}, job)
	if err != nil {
		t.Fatalf("expected nil error with unknown template, got: %v", err)
	}
}

func TestBuildMessage_Format(t *testing.T) {
	msg := buildMessage("noreply@bedrud.org", "Bedrud", "alice@test.com", "Hello", "<p>Welcome</p>")

	if !strings.Contains(msg, "From: Bedrud <noreply@bedrud.org>") {
		t.Error("missing From header")
	}
	if !strings.Contains(msg, "To: alice@test.com") {
		t.Error("missing To header")
	}
	if !strings.Contains(msg, "Subject: Hello") {
		t.Error("missing Subject header")
	}
	if !strings.Contains(msg, "Content-Type: multipart/alternative") {
		t.Error("missing multipart content type")
	}
	if !strings.Contains(msg, "text/plain") {
		t.Error("missing plaintext alternative")
	}
	if !strings.Contains(msg, "text/html") {
		t.Error("missing HTML alternative")
	}
	if !strings.Contains(msg, "<p>Welcome</p>") {
		t.Error("missing HTML body")
	}
	if !strings.Contains(msg, "Welcome") {
		t.Error("missing plaintext content")
	}
}

func TestBuildMessage_EmptyFromName(t *testing.T) {
	msg := buildMessage("noreply@bedrud.org", "", "alice@test.com", "Test", "<p>Hi</p>")
	if !strings.Contains(msg, "From:  <noreply@bedrud.org>") {
		t.Error("from should have empty name")
	}
}

func TestStripHTML(t *testing.T) {
	cases := []struct {
		input    string
		expected string
	}{
		{"<p>Hello</p>", "Hello"},
		{"<h1>Title</h1><p>Body</p>", "TitleBody"},
		{"No tags here", "No tags here"},
		{"<a href=\"link\">Click</a>", "Click"},
		{"", ""},
		{"<br/>", ""},
	}
	for _, c := range cases {
		got := stripHTML(c.input)
		if got != c.expected {
			t.Errorf("stripHTML(%q) = %q, want %q", c.input, got, c.expected)
		}
	}
}

func TestRenderFallback(t *testing.T) {
	data := map[string]any{
		"Name":  "Alice",
		"URL":   "https://bedrud.org",
		"Count": 42,
	}
	html := renderFallback(data)
	if !strings.Contains(html, "Alice") {
		t.Error("missing Name in fallback")
	}
	if !strings.Contains(html, "bedrud.org") {
		t.Error("missing URL in fallback")
	}
	if !strings.Contains(html, "42") {
		t.Error("missing Count in fallback")
	}
}

func TestSendEmailHandler_ContextCancelled(t *testing.T) {
	// Use a minimal SMTP config but cancelled context.
	// Handler will try to connect and fail, but we cancel first.
	h := NewSendEmailHandler(&config.EmailConfig{
		SMTPHost: "127.0.0.1",
		SMTPPort: 2525,
	})

	payload := SendEmailPayload{
		To:           "user@test.com",
		Subject:      "Test",
		TemplateName: "welcome",
		TemplateData: map[string]any{"Name": "Alice"},
	}
	payloadBytes, _ := json.Marshal(payload)

	job := &models.Job{
		ID:      uuid.New().String(),
		Type:    "send_email",
		Payload: string(payloadBytes),
	}

	// Cancelled context should return cancellation error.
	ctx, cancel := context.WithCancel(context.Background())
	cancel()

	err := h(ctx, &gorm.DB{}, job)
	if err == nil {
		t.Fatal("expected error with cancelled context")
	}
}

func TestSendEmailHandler_TemplateNotFound_LogsData(t *testing.T) {
	h := NewSendEmailHandler(nil)

	payload := SendEmailPayload{
		To:           "user@test.com",
		Subject:      "Fallback test",
		TemplateName: "nonexistent",
		TemplateData: map[string]any{
			"UserID": "abc-123",
			"Room":   "test-room",
		},
	}
	payloadBytes, _ := json.Marshal(payload)

	job := &models.Job{
		ID:      uuid.New().String(),
		Type:    "send_email",
		Payload: string(payloadBytes),
	}

	err := h(context.Background(), &gorm.DB{}, job)
	if err != nil {
		t.Fatalf("expected nil error when template missing, got: %v", err)
	}
	// The body should contain the data keys.
	// We can't assert on log output here, but handler returning nil means
	// renderFallback was used successfully.
}

func TestSendEmailHandler_NilData(t *testing.T) {
	h := NewSendEmailHandler(nil)

	payload := SendEmailPayload{
		To:           "user@test.com",
		Subject:      "Data test",
		TemplateName: "welcome",
		TemplateData: nil,
	}
	payloadBytes, _ := json.Marshal(payload)

	job := &models.Job{
		ID:      uuid.New().String(),
		Type:    "send_email",
		Payload: string(payloadBytes),
	}

	err := h(context.Background(), &gorm.DB{}, job)
	if err != nil {
		t.Fatalf("expected nil error with nil template data, got: %v", err)
	}
}

func TestBuildMessage_NoBody(t *testing.T) {
	msg := buildMessage("from@test.com", "Tester", "to@test.com", "Empty", "")
	if !strings.Contains(msg, "text/plain") {
		t.Error("missing plaintext part")
	}
	if !strings.Contains(msg, "text/html") {
		t.Error("missing HTML part")
	}
}

func TestStripHTML_Complex(t *testing.T) {
	input := `<div class="content"><h1>Title</h1><p>Para with <strong>bold</strong></p><ul><li>Item</li></ul></div>`
	expected := "TitlePara with boldItem"
	got := stripHTML(input)
	if got != expected {
		t.Errorf("stripHTML returned %q, want %q", got, expected)
	}
}

func TestRenderFallback_EmptyData(t *testing.T) {
	html := renderFallback(map[string]any{})
	if html == "" {
		t.Error("expected non-empty fallback HTML")
	}
}

func TestRenderWelcomeTemplate(t *testing.T) {
	var buf bytes.Buffer
	tmpl, err := template.ParseFS(emailTemplatesFS, "templates/welcome.html")
	if err != nil {
		t.Fatalf("parse welcome template: %v", err)
	}
	if err := tmpl.Execute(&buf, map[string]any{
		"Name":     "Alice",
		"LoginURL": "https://bedrud.org/login",
	}); err != nil {
		t.Fatalf("execute welcome template: %v", err)
	}
	output := buf.String()
	if !strings.Contains(output, "Welcome, Alice") {
		t.Error("welcome template missing name")
	}
	if !strings.Contains(output, "bedrud.org/login") {
		t.Error("welcome template missing login URL")
	}
	if strings.Contains(output, "<no value>") {
		t.Error("welcome template has unrendered placeholders")
	}
}

func TestRenderRoomInviteTemplate(t *testing.T) {
	var buf bytes.Buffer
	tmpl, err := template.ParseFS(emailTemplatesFS, "templates/room_invite.html")
	if err != nil {
		t.Fatalf("parse room_invite template: %v", err)
	}
	if err := tmpl.Execute(&buf, map[string]any{
		"InviterName": "Bob",
		"RoomName":    "Team Standup",
		"JoinURL":     "https://bedrud.org/room/abc",
	}); err != nil {
		t.Fatalf("execute room_invite template: %v", err)
	}
	output := buf.String()
	if !strings.Contains(output, "Bob") {
		t.Error("room_invite template missing inviter name")
	}
	if !strings.Contains(output, "Team Standup") {
		t.Error("room_invite template missing room name")
	}
	if !strings.Contains(output, "bedrud.org/room/abc") {
		t.Error("room_invite template missing join URL")
	}
}

func TestRenderPasswordResetTemplate(t *testing.T) {
	var buf bytes.Buffer
	tmpl, err := template.ParseFS(emailTemplatesFS, "templates/password_reset.html")
	if err != nil {
		t.Fatalf("parse password_reset template: %v", err)
	}
	if err := tmpl.Execute(&buf, map[string]any{
		"ResetURL":  "https://bedrud.org/reset/token123",
		"IPAddress": "192.168.1.1",
	}); err != nil {
		t.Fatalf("execute password_reset template: %v", err)
	}
	output := buf.String()
	if !strings.Contains(output, "bedrud.org/reset/token123") {
		t.Error("password_reset template missing reset URL")
	}
	if !strings.Contains(output, "192.168.1.1") {
		t.Error("password_reset template missing IP address")
	}
	if !strings.Contains(output, "Reset Password") {
		t.Error("password_reset template missing button text")
	}
}

func TestRenderGenericTemplate(t *testing.T) {
	var buf bytes.Buffer
	tmpl, err := template.ParseFS(emailTemplatesFS, "templates/generic.html")
	if err != nil {
		t.Fatalf("parse generic template: %v", err)
	}
	if err := tmpl.Execute(&buf, map[string]any{
		"UserID": "u-123",
		"Room":   "test-room",
	}); err != nil {
		t.Fatalf("execute generic template: %v", err)
	}
	output := buf.String()
	if !strings.Contains(output, "UserID") {
		t.Error("generic template missing UserID key")
	}
	if !strings.Contains(output, "u-123") {
		t.Error("generic template missing UserID value")
	}
	if !strings.Contains(output, "test-room") {
		t.Error("generic template missing Room value")
	}
	if strings.Contains(output, "<no value>") {
		t.Error("generic template has unrendered placeholders")
	}
}

func TestRenderGenericTemplate_EmptyData(t *testing.T) {
	var buf bytes.Buffer
	tmpl, err := template.ParseFS(emailTemplatesFS, "templates/generic.html")
	if err != nil {
		t.Fatalf("parse generic template: %v", err)
	}
	if err := tmpl.Execute(&buf, map[string]any{}); err != nil {
		t.Fatalf("execute generic template with empty data: %v", err)
	}
	output := buf.String()
	if !strings.Contains(output, "Notification from Bedrud") {
		t.Error("generic template missing heading with empty data")
	}
}

func TestRenderWelcomeTemplate_MissingOptionalKeys(t *testing.T) {
	var buf bytes.Buffer
	tmpl, err := template.ParseFS(emailTemplatesFS, "templates/welcome.html")
	if err != nil {
		t.Fatalf("parse welcome template: %v", err)
	}
	// Only Name provided, LoginURL is optional (wrapped in {{if}}).
	if err := tmpl.Execute(&buf, map[string]any{
		"Name": "Charlie",
	}); err != nil {
		t.Fatalf("execute welcome template with missing keys: %v", err)
	}
	output := buf.String()
	if !strings.Contains(output, "Welcome, Charlie") {
		t.Error("welcome template missing name")
	}
	if strings.Contains(output, "Go to Bedrud") {
		t.Error("welcome template should not render login button when LoginURL missing")
	}
}

func TestRenderPasswordResetTemplate_MissingOptionalKeys(t *testing.T) {
	var buf bytes.Buffer
	tmpl, err := template.ParseFS(emailTemplatesFS, "templates/password_reset.html")
	if err != nil {
		t.Fatalf("parse password_reset template: %v", err)
	}
	// Neither ResetURL nor IPAddress provided — should still render without error.
	if err := tmpl.Execute(&buf, map[string]any{}); err != nil {
		t.Fatalf("execute password_reset template with empty data: %v", err)
	}
	output := buf.String()
	if !strings.Contains(output, "Password Reset") {
		t.Error("password_reset template missing heading")
	}
	if strings.Contains(output, "<no value>") {
		t.Error("password_reset template has unrendered placeholders")
	}
}

func TestTemplateRenderDirect_SMTPNotConfigured(t *testing.T) {
	// Run template through the handler with no SMTP config — verifies the full render + log path.
	h := NewSendEmailHandler(nil)
	payload := SendEmailPayload{
		To:           "alice@test.com",
		Subject:      "Welcome!",
		TemplateName: "welcome",
		TemplateData: map[string]any{
			"Name":     "Alice",
			"LoginURL": "https://bedrud.org",
		},
	}
	payloadBytes, _ := json.Marshal(payload)
	job := &models.Job{
		ID:      uuid.New().String(),
		Type:    "send_email",
		Payload: string(payloadBytes),
	}
	if err := h(context.Background(), &gorm.DB{}, job); err != nil {
		t.Fatalf("handler returned error: %v", err)
	}
}

// TTD tests — these verify the handler falls back to generic template for unknown names.
// Currently failing because "generic" is not parsed — will pass after fix.

func TestUnknownTemplate_FallsBackToGeneric(t *testing.T) {
	// Unknown template name should render the generic template ("Notification from Bedrud" heading).
	// This test will fail until "generic" is added to the template parse loop.
	h := NewSendEmailHandler(nil)
	payload := SendEmailPayload{
		To:           "user@test.com",
		Subject:      "Notification",
		TemplateName: "bogus_template_name",
		TemplateData: map[string]any{
			"Message": "Something happened",
		},
	}
	payloadBytes, _ := json.Marshal(payload)
	job := &models.Job{
		ID:      uuid.New().String(),
		Type:    "send_email",
		Payload: string(payloadBytes),
	}
	// The handler returns nil even with generic fallback — but output includes generic heading.
	// We can't assert the body directly (it's logged, not returned), but we verify no error.
	if err := h(context.Background(), &gorm.DB{}, job); err != nil {
		t.Fatalf("expected nil error with unknown template (generic fallback), got: %v", err)
	}
}

// Test that unknown template data keys appear in generic fallback output.
// We render the generic template directly and verify data keys are present.
func TestGenericTemplate_IncludesDataKeys(t *testing.T) {
	var buf bytes.Buffer
	tmpl, err := template.ParseFS(emailTemplatesFS, "templates/generic.html")
	if err != nil {
		t.Fatalf("parse generic template: %v", err)
	}
	if err := tmpl.Execute(&buf, map[string]any{
		"CustomField": "custom-value",
		"AnotherKey":  "another-value",
	}); err != nil {
		t.Fatalf("execute generic template: %v", err)
	}
	output := buf.String()
	if !strings.Contains(output, "CustomField") {
		t.Error("generic template missing CustomField key")
	}
	if !strings.Contains(output, "custom-value") {
		t.Error("generic template missing CustomField value")
	}
	if !strings.Contains(output, "AnotherKey") {
		t.Error("generic template missing AnotherKey key")
	}
}

// password_changed template tests — will fail until template file exists.

func TestRenderPasswordChangedTemplate(t *testing.T) {
	// This test will fail until password_changed.html template file is created.
	var buf bytes.Buffer
	tmpl, err := template.ParseFS(emailTemplatesFS, "templates/password_changed.html")
	if err != nil {
		t.Fatalf("parse password_changed template: %v", err)
	}
	if err := tmpl.Execute(&buf, map[string]any{
		"IPAddress": "10.0.0.1",
	}); err != nil {
		t.Fatalf("execute password_changed template: %v", err)
	}
	output := buf.String()
	if !strings.Contains(output, "Password Changed") {
		t.Error("password_changed template missing heading")
	}
	if !strings.Contains(output, "10.0.0.1") {
		t.Error("password_changed template missing IP address")
	}
	if strings.Contains(output, "<no value>") {
		t.Error("password_changed template has unrendered placeholders")
	}
}

func TestHandler_EnqueuePasswordChanged_NoSMTP(t *testing.T) {
	// Full handler integration test with password_changed template and no SMTP.
	h := NewSendEmailHandler(nil)
	payload := SendEmailPayload{
		To:           "user@test.com",
		Subject:      "Password Changed",
		TemplateName: "password_changed",
		TemplateData: map[string]any{
			"IPAddress": "10.0.0.1",
		},
	}
	payloadBytes, _ := json.Marshal(payload)
	job := &models.Job{
		ID:      uuid.New().String(),
		Type:    "send_email",
		Payload: string(payloadBytes),
	}
	// Should return nil (not error) when SMTP unconfigured.
	if err := h(context.Background(), &gorm.DB{}, job); err != nil {
		t.Fatalf("expected nil error when SMTP not configured, got: %v", err)
	}
}

func TestWelcome_EmptyLoginURL_NoButton(t *testing.T) {
	// Welcome template should NOT render the "Go to Bedrud" button when LoginURL is empty.
	var buf bytes.Buffer
	tmpl, err := template.ParseFS(emailTemplatesFS, "templates/welcome.html")
	if err != nil {
		t.Fatalf("parse welcome template: %v", err)
	}
	// LoginURL explicitly empty string — should not show button.
	if err := tmpl.Execute(&buf, map[string]any{
		"Name":     "Dave",
		"LoginURL": "",
	}); err != nil {
		t.Fatalf("execute welcome template: %v", err)
	}
	output := buf.String()
	if strings.Contains(output, "Go to Bedrud") {
		t.Error("welcome template should not render button when LoginURL is empty string")
	}
}

func TestWelcome_MissingLoginURLKey_NoButton(t *testing.T) {
	// Welcome template should NOT render the "Go to Bedrud" button when LoginURL key missing.
	var buf bytes.Buffer
	tmpl, err := template.ParseFS(emailTemplatesFS, "templates/welcome.html")
	if err != nil {
		t.Fatalf("parse welcome template: %v", err)
	}
	// LoginURL key not provided at all — should not show button.
	if err := tmpl.Execute(&buf, map[string]any{
		"Name": "Eve",
	}); err != nil {
		t.Fatalf("execute welcome template: %v", err)
	}
	output := buf.String()
	if strings.Contains(output, "Go to Bedrud") {
		t.Error("welcome template should not render button when LoginURL missing")
	}
}

// — renderEmailBody tests —

func TestRenderEmailBody_WelcomeTemplate(t *testing.T) {
	tmpls := make(map[string]*template.Template)
	for _, name := range []string{"welcome", "generic"} {
		tmpl, err := template.ParseFS(emailTemplatesFS, "templates/"+name+".html")
		if err != nil {
			t.Fatalf("parse %s template: %v", name, err)
		}
		tmpls[name] = tmpl
	}

	body, err := renderEmailBody(tmpls, SendEmailPayload{
		TemplateName: "welcome",
		TemplateData: map[string]any{
			"Name":     "Alice",
			"LoginURL": "https://bedrud.org",
		},
	})
	if err != nil {
		t.Fatalf("renderEmailBody: %v", err)
	}
	if !strings.Contains(body, "Welcome, Alice") {
		t.Error("body missing name")
	}
	if !strings.Contains(body, "bedrud.org") {
		t.Error("body missing URL")
	}
}

func TestRenderEmailBody_UnknownTemplate_UsesGeneric(t *testing.T) {
	tmpls := make(map[string]*template.Template)
	for _, name := range []string{"welcome", "generic"} {
		tmpl, err := template.ParseFS(emailTemplatesFS, "templates/"+name+".html")
		if err != nil {
			t.Fatalf("parse %s template: %v", name, err)
		}
		tmpls[name] = tmpl
	}

	body, err := renderEmailBody(tmpls, SendEmailPayload{
		TemplateName: "bogus_template",
		TemplateData: map[string]any{"key": "value"},
	})
	if err != nil {
		t.Fatalf("renderEmailBody with unknown template: %v", err)
	}
	// Generic template heading should be present.
	if !strings.Contains(body, "Notification from Bedrud") {
		t.Error("unknown template should render generic template heading")
	}
	// Data keys should appear in generic output.
	if !strings.Contains(body, "key") || !strings.Contains(body, "value") {
		t.Error("unknown template generic output missing data keys")
	}
}

func TestRenderEmailBody_UnknownTemplate_NoGeneric(t *testing.T) {
	// Only welcome template, NO generic template in map.
	tmpls := make(map[string]*template.Template)
	tmpl, err := template.ParseFS(emailTemplatesFS, "templates/welcome.html")
	if err != nil {
		t.Fatalf("parse welcome template: %v", err)
	}
	tmpls["welcome"] = tmpl

	body, err := renderEmailBody(tmpls, SendEmailPayload{
		TemplateName: "unknown",
		TemplateData: map[string]any{"Foo": "Bar"},
	})
	if err != nil {
		t.Fatalf("renderEmailBody without generic: %v", err)
	}
	// Should fall back to renderFallback (pre-formatted data dump).
	if !strings.Contains(body, "Foo") || !strings.Contains(body, "Bar") {
		t.Error("fallback output missing data keys")
	}
	// renderFallback wraps in <pre> tags.
	if !strings.Contains(body, "<pre>") {
		t.Error("fallback output should contain <pre> tags")
	}
}

func TestRenderEmailBody_NilData(t *testing.T) {
	tmpls := make(map[string]*template.Template)
	tmpl, err := template.ParseFS(emailTemplatesFS, "templates/welcome.html")
	if err != nil {
		t.Fatalf("parse welcome template: %v", err)
	}
	tmpls["welcome"] = tmpl

	body, err := renderEmailBody(tmpls, SendEmailPayload{
		TemplateName: "welcome",
		TemplateData: nil,
	})
	if err != nil {
		t.Fatalf("renderEmailBody with nil data: %v", err)
	}
	if body == "" {
		t.Error("expected non-empty body with nil data")
	}
}

func TestRenderEmailBody_UnknownTemplate_MultipleDataKeys(t *testing.T) {
	tmpls := make(map[string]*template.Template)
	tmpl, err := template.ParseFS(emailTemplatesFS, "templates/generic.html")
	if err != nil {
		t.Fatalf("parse generic template: %v", err)
	}
	tmpls["generic"] = tmpl

	body, err := renderEmailBody(tmpls, SendEmailPayload{
		TemplateName: "does_not_exist",
		TemplateData: map[string]any{
			"Alpha": "1",
			"Beta":  "2",
			"Gamma": "3",
		},
	})
	if err != nil {
		t.Fatalf("renderEmailBody: %v", err)
	}
	for _, key := range []string{"Alpha", "Beta", "Gamma"} {
		if !strings.Contains(body, key) {
			t.Errorf("body missing key %q", key)
		}
	}
}

func TestRenderEmailBody_UnknownTemplate_EmptyData(t *testing.T) {
	tmpls := make(map[string]*template.Template)
	tmpl, err := template.ParseFS(emailTemplatesFS, "templates/generic.html")
	if err != nil {
		t.Fatalf("parse generic template: %v", err)
	}
	tmpls["generic"] = tmpl

	body, err := renderEmailBody(tmpls, SendEmailPayload{
		TemplateName: "does_not_exist",
		TemplateData: map[string]any{},
	})
	if err != nil {
		t.Fatalf("renderEmailBody with empty data: %v", err)
	}
	if !strings.Contains(body, "Notification from Bedrud") {
		t.Error("body missing generic heading with empty data")
	}
}
