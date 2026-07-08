package ports

// Local dev port map (predictable 7070+ range). Keep in sync with scripts/dev-ports.sh.
const (
	Web        = 7070
	API        = 7071
	LiveKit    = 7072
	LiveKitRTC = 7073
	DevTools   = 7074
	Site       = 7075
	TurnUDP    = 7076
	TurnTLS    = 7077
	RTCStart   = 7080
	RTCEnd     = 7180
)

// DevTCPPorts are swept on stop (keep in sync with scripts/dev-ports.sh).
var DevTCPPorts = []int{Web, API, LiveKit, LiveKitRTC, DevTools, Site, TurnUDP, TurnTLS}