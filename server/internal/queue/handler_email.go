package queue

import (
	"bytes"
	"context"
	"crypto/tls"
	"embed"
	"encoding/json"
	"fmt"
	"html/template"
	"net"
	"net/smtp"
	"time"

	"bedrud/config"
	"bedrud/internal/models"

	"github.com/rs/zerolog/log"
	"gorm.io/gorm"
)

//go:embed templates/*.html
var emailTemplatesFS embed.FS

// emailHandler holds SMTP config and parsed templates for sending transactional emails.
type emailHandler struct {
	cfg    *config.EmailConfig
	tmpls  map[string]*template.Template
}

// NewSendEmailHandler creates a handler that sends transactional emails via SMTP.
// When SMTP is not configured, the handler logs and skips (no-op, no error).
func NewSendEmailHandler(cfg *config.EmailConfig) Handler {
	h := &emailHandler{cfg: cfg}

	// Parse all embedded HTML templates on init.
	h.tmpls = make(map[string]*template.Template)
	for _, name := range []string{"welcome", "room_invite", "password_reset", "password_changed", "generic"} {
		t, err := template.ParseFS(emailTemplatesFS, "templates/"+name+".html")
		if err != nil {
			log.Warn().Err(err).Str("template", name).Msg("email: failed to parse template, will fall back to plain text")
			continue
		}
		h.tmpls[name] = t
	}

	h.validateConfig()

	return func(ctx context.Context, db *gorm.DB, job *models.Job) error {
		var payload SendEmailPayload
		if err := json.Unmarshal([]byte(job.Payload), &payload); err != nil {
			return fmt.Errorf("unmarshal send_email payload: %w", err)
		}

		// Render HTML body from template (needed for both SMTP path and no-SMTP logging).
		bodyHTML, err := renderEmailBody(h.tmpls, payload)
		if err != nil {
			return err
		}

		// Skip if SMTP not configured — log rendered body and return nil so job is marked done.
		if h.cfg == nil || h.cfg.SMTPHost == "" || h.cfg.SMTPPort == 0 {
			log.Warn().Str("to", payload.To).Str("subject", payload.Subject).
				Str("template", payload.TemplateName).
				Str("body", bodyHTML).
				Interface("data", payload.TemplateData).
				Msg("email: SMTP not configured, skipping send — body logged")
			return nil
		}

		from := h.cfg.FromAddress
		if from == "" {
			from = "noreply@bedrud"
		}
		fromName := h.cfg.FromName
		if fromName == "" {
			fromName = "Bedrud"
		}

		// Build RFC 2822 message.
		msg := buildMessage(from, fromName, payload.To, payload.Subject, bodyHTML)

		addr := net.JoinHostPort(h.cfg.SMTPHost, fmt.Sprint(h.cfg.SMTPPort))

		// Auth
		var auth smtp.Auth
		if h.cfg.Username != "" {
			auth = smtp.PlainAuth("", h.cfg.Username, h.cfg.Password, h.cfg.SMTPHost)
		}

		// Send via SMTP with a deadline.
		// StartTLS is preferred; fall back to plain SMTP if server doesn't support it.
		done := make(chan error, 1)
		go func() {
			defer func() {
				if r := recover(); r != nil {
					done <- fmt.Errorf("panic in sendSMTP: %v", r)
				}
			}()
			done <- sendSMTP(addr, auth, from, []string{payload.To}, []byte(msg),
				h.cfg.SMTPHost, h.cfg.TLSSkipVerify, h.cfg.SMTPSMode)
		}()

		timer := time.NewTimer(30 * time.Second)
		defer timer.Stop()
		select {
		case err := <-done:
			if err != nil {
				return fmt.Errorf("smtp send: %w", err)
			}
		case <-ctx.Done():
			return ctx.Err()
		case <-timer.C:
			return fmt.Errorf("smtp send timed out after 30s")
		}

		log.Info().Str("to", payload.To).Str("subject", payload.Subject).
			Str("template", payload.TemplateName).
			Msg("email: sent successfully")
		return nil
	}
}

// renderEmailBody resolves the template by name and renders it with the payload data.
// Falls back to "generic" template if named template not found, then to renderFallback.
func renderEmailBody(tmpls map[string]*template.Template, payload SendEmailPayload) (string, error) {
	var buf bytes.Buffer
	tmpl, ok := tmpls[payload.TemplateName]
	if ok {
		if err := tmpl.Execute(&buf, payload.TemplateData); err != nil {
			return "", fmt.Errorf("render template %s: %w", payload.TemplateName, err)
		}
		return buf.String(), nil
	}
	// Unknown template name — try generic fallback.
	if genericTmpl, ok := tmpls["generic"]; ok {
		if err := genericTmpl.Execute(&buf, payload.TemplateData); err != nil {
			buf.WriteString(renderFallback(payload.TemplateData))
		}
		return buf.String(), nil
	}
	// Last resort: plain text data dump.
	buf.WriteString(renderFallback(payload.TemplateData))
	return buf.String(), nil
}

// validateConfig logs warnings for misconfigured SMTP settings.
func (h *emailHandler) validateConfig() {
	if h.cfg == nil || h.cfg.SMTPHost == "" {
		return
	}
	if h.cfg.FromAddress == "" {
		log.Warn().Msg("email: smtpHost set but fromAddress empty — using 'noreply@bedrud' (may be rejected)")
	}
	if h.cfg.SMTPPort == 0 {
		log.Warn().Msg("email: smtpPort is 0 — SMTP connection will fail")
	}
	if h.cfg.Username != "" && h.cfg.Password == "" {
		log.Warn().Msg("email: username set but password empty — auth may fail")
	}
}

// sendSMTP sends email via SMTP. Supports three modes:
//   - SMTPS (direct TLS, port 465): tls.Dial → smtp.NewClient → Auth → send
//   - STARTTLS (port 587/25): plain TCP → STARTTLS → Auth → send
//   - Plain (no TLS): send on existing connection without TLS
//
// Never falls back to smtp.SendMail — each failure returns the actual error.
func sendSMTP(addr string, auth smtp.Auth, from string, to []string, msg []byte, host string, tlsSkipVerify, smtpsMode bool) error {
	tlsCfg := &tls.Config{ServerName: host, InsecureSkipVerify: tlsSkipVerify}

	if smtpsMode {
		// SMTPS — direct TLS connection (common on port 465).
		conn, err := tls.DialWithDialer(&net.Dialer{Timeout: 10 * time.Second}, "tcp", addr, tlsCfg)
		if err != nil {
			return fmt.Errorf("smtps dial: %w", err)
		}
		client, err := smtp.NewClient(conn, host)
		if err != nil {
			conn.Close()
			return fmt.Errorf("smtps new client: %w", err)
		}
		defer client.Close()
		if auth != nil {
			if err := client.Auth(auth); err != nil {
				return fmt.Errorf("smtps auth: %w", err)
			}
		}
		return sendMailClient(client, from, to, msg)
	}

	// STARTTLS path — dial plain TCP, upgrade to TLS if available.
	conn, err := net.DialTimeout("tcp", addr, 10*time.Second)
	if err != nil {
		return fmt.Errorf("smtp dial: %w", err)
	}

	client, err := smtp.NewClient(conn, host)
	if err != nil {
		conn.Close()
		return fmt.Errorf("smtp new client: %w", err)
	}
	defer client.Close()

	if ok, _ := client.Extension("STARTTLS"); ok {
		if err := client.StartTLS(tlsCfg); err != nil {
			return fmt.Errorf("smtp STARTTLS: %w", err)
		}
		if auth != nil {
			if err := client.Auth(auth); err != nil {
				return fmt.Errorf("smtp auth after STARTTLS: %w", err)
			}
		}
		return sendMailClient(client, from, to, msg)
	}

	// STARTTLS not available — send plain on the existing connection.
	// PlainAuth checks if conn has TLS state and will refuse if auth is set
	// but connection is not encrypted. This is by design for security.
	if auth != nil {
		if err := client.Auth(auth); err != nil {
			return fmt.Errorf("smtp auth without TLS: %w (SMTP server does not support STARTTLS)", err)
		}
	}
	return sendMailClient(client, from, to, msg)
}

// sendMailClient performs MAIL FROM, RCPT TO, and DATA on an already-connected SMTP client.
func sendMailClient(client *smtp.Client, from string, to []string, msg []byte) error {
	if err := client.Mail(from); err != nil {
		return fmt.Errorf("mail from: %w", err)
	}
	for _, addr := range to {
		if err := client.Rcpt(addr); err != nil {
			return fmt.Errorf("rcpt %s: %w", addr, err)
		}
	}
	w, err := client.Data()
	if err != nil {
		return fmt.Errorf("data: %w", err)
	}
	if _, err := w.Write(msg); err != nil {
		return fmt.Errorf("write: %w", err)
	}
	return w.Close()
}

// buildMessage constructs RFC 2822 formatted email with MIME multipart.
func buildMessage(from, fromName, to, subject, bodyHTML string) string {
	boundary := fmt.Sprintf("bedrud-boundary-%d", time.Now().UnixNano())
	msg := fmt.Sprintf("From: %s <%s>\r\n", fromName, from)
	msg += fmt.Sprintf("To: %s\r\n", to)
	msg += fmt.Sprintf("Subject: %s\r\n", subject)
	msg += "MIME-Version: 1.0\r\n"
	msg += fmt.Sprintf("Content-Type: multipart/alternative; boundary=\"%s\"\r\n", boundary)
	msg += "\r\n"
	msg += fmt.Sprintf("--%s\r\n", boundary)
	msg += "Content-Type: text/plain; charset=\"utf-8\"\r\n"
	msg += "\r\n"
	msg += stripHTML(bodyHTML) + "\r\n"
	msg += fmt.Sprintf("\r\n--%s\r\n", boundary)
	msg += "Content-Type: text/html; charset=\"utf-8\"\r\n"
	msg += "Content-Transfer-Encoding: quoted-printable\r\n"
	msg += "\r\n"
	msg += bodyHTML + "\r\n"
	msg += fmt.Sprintf("\r\n--%s--\r\n", boundary)
	return msg
}

// stripHTML removes HTML tags for plaintext fallback.
func stripHTML(html string) string {
	var buf bytes.Buffer
	inTag := false
	for _, r := range html {
		switch {
		case r == '<':
			inTag = true
		case r == '>':
			inTag = false
		case !inTag:
			buf.WriteRune(r)
		}
	}
	return buf.String()
}

// renderFallback builds a plain text summary from TemplateData when template parsing fails.
func renderFallback(data map[string]any) string {
	var buf bytes.Buffer
	buf.WriteString("<html><body><pre>")
	for k, v := range data {
		buf.WriteString(fmt.Sprintf("%s: %v\n", k, v))
	}
	buf.WriteString("</pre></body></html>")
	return buf.String()
}
