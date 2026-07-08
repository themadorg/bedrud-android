package remote

import "testing"

func TestBuildRemoteCommandBashC(t *testing.T) {
	cmd := buildRemoteCommand("bash", "-c", "mkdir -p /etc/a && echo ok")
	want := "bash -c 'mkdir -p /etc/a && echo ok'"
	if cmd != want {
		t.Fatalf("got %q want %q", cmd, want)
	}
}

func TestBuildRemoteCommandSingle(t *testing.T) {
	if got := buildRemoteCommand("echo ok"); got != "echo ok" {
		t.Fatalf("got %q", got)
	}
}