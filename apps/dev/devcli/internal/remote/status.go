package remote

// PrintStatus runs all remote health probes and prints a summary.
func PrintStatus(cfg *Config) (bool, error) {
	report := CheckRemoteHealth(cfg)
	ok := PrintHealthReport("Remote debug status", report)
	return ok, nil
}