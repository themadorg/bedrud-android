import type { Room } from 'livekit-client'
import { ConnectionState } from 'livekit-client'
import {
  getLiveKitPublishDiagnostics,
  isRoomPublishReady,
  isRoomTransportDead,
  type LiveKitPublishDiagnostics,
} from '#/lib/livekit-publish'
import { getLiveKitTransportMode } from '#/lib/livekit-transport-type'

const MAX_EVENTS = 200

export type MeetingDebugEvent = {
  t: string
  ms: number
  tag: string
  detail?: Record<string, unknown>
}

const events: MeetingDebugEvent[] = []
let sessionMeta: Record<string, unknown> = {}

function nowIso() {
  return new Date().toISOString()
}

/** Append a structured meeting debug line (also printed to console). */
export function meetingDebugLog(tag: string, detail?: Record<string, unknown>): void {
  const entry: MeetingDebugEvent = {
    t: nowIso(),
    ms: Date.now(),
    tag,
    ...(detail ? { detail } : {}),
  }
  events.push(entry)
  if (events.length > MAX_EVENTS) events.shift()

  // Always print — user needs copyable console output while debugging remote meetings.
  if (detail) {
    console.log(`[bedrud-meet] ${tag}`, detail)
  } else {
    console.log(`[bedrud-meet] ${tag}`)
  }
}

export function meetingDebugSetMeta(meta: Record<string, unknown>): void {
  sessionMeta = { ...sessionMeta, ...meta }
  meetingDebugLog('session.meta', meta)
}

export function meetingDebugClear(): void {
  events.length = 0
  sessionMeta = {}
}

export function getMeetingDebugEvents(): MeetingDebugEvent[] {
  return [...events]
}

type EngineProbe = {
  hasPcManager: boolean
  pcMode?: string
  reliableDc?: string
  reliableDcSub?: string
  lossyDc?: string
  verifyTransport?: boolean
  wsReadyState?: number
  participantCount?: number
  localIdentity?: string
  remoteIdentities?: string[]
}

function probeEngine(room: Room): EngineProbe {
  try {
    const engine = room.engine as unknown as {
      pcManager?: { mode?: string } | null
      reliableDC?: { readyState?: string }
      reliableDCSub?: { readyState?: string }
      lossyDC?: { readyState?: string }
      dataSubscriberReadyState?: string
      client?: { ws?: { readyState?: number } }
      verifyTransport?: () => boolean
    }
    const remotes = Array.from(room.remoteParticipants.values()).map((p) => p.identity)
    return {
      hasPcManager: engine.pcManager != null,
      pcMode: engine.pcManager?.mode,
      reliableDc: engine.reliableDC?.readyState,
      reliableDcSub: engine.reliableDCSub?.readyState ?? engine.dataSubscriberReadyState,
      lossyDc: engine.lossyDC?.readyState,
      verifyTransport: typeof engine.verifyTransport === 'function' ? engine.verifyTransport() : undefined,
      wsReadyState: engine.client?.ws?.readyState,
      participantCount: 1 + remotes.length,
      localIdentity: room.localParticipant?.identity,
      remoteIdentities: remotes,
    }
  } catch (err) {
    return {
      hasPcManager: false,
      // surface probe errors
      pcMode: `probe-error: ${err instanceof Error ? err.message : String(err)}`,
    }
  }
}

export type MeetingDebugSnapshot = {
  generatedAt: string
  userAgent: string
  href: string
  session: Record<string, unknown>
  room: {
    name?: string
    state: ConnectionState
    publishReady: boolean
    transportDead: boolean
  }
  diagnostics: LiveKitPublishDiagnostics
  engine: EngineProbe
  iceMode?: string
  serverAddress?: string | null
  recentEvents: MeetingDebugEvent[]
}

/** Full snapshot for copy/paste bug reports. */
export async function buildMeetingDebugSnapshot(room: Room): Promise<MeetingDebugSnapshot> {
  const diagnostics = getLiveKitPublishDiagnostics(room)
  const engine = probeEngine(room)
  let iceMode = 'unknown'
  let serverAddress: string | null | undefined
  try {
    iceMode = await getLiveKitTransportMode(room)
  } catch {
    iceMode = 'error'
  }
  try {
    serverAddress = (await room.engine.getConnectedServerAddress?.()) ?? null
  } catch {
    serverAddress = null
  }

  return {
    generatedAt: nowIso(),
    userAgent: typeof navigator !== 'undefined' ? navigator.userAgent : '',
    href: typeof location !== 'undefined' ? location.href : '',
    session: { ...sessionMeta },
    room: {
      name: room.name,
      state: room.state,
      publishReady: isRoomPublishReady(room),
      transportDead: isRoomTransportDead(room),
    },
    diagnostics,
    engine,
    iceMode,
    serverAddress,
    recentEvents: getMeetingDebugEvents(),
  }
}

export function formatMeetingDebugSnapshot(snapshot: MeetingDebugSnapshot): string {
  return [
    '======== BEDRUD MEETING DEBUG LOG ========',
    `generatedAt: ${snapshot.generatedAt}`,
    `href: ${snapshot.href}`,
    `ua: ${snapshot.userAgent}`,
    '',
    '--- session ---',
    JSON.stringify(snapshot.session, null, 2),
    '',
    '--- room ---',
    JSON.stringify(snapshot.room, null, 2),
    '',
    '--- diagnostics ---',
    JSON.stringify(snapshot.diagnostics, null, 2),
    '',
    '--- engine ---',
    JSON.stringify(snapshot.engine, null, 2),
    `iceMode: ${snapshot.iceMode}`,
    `serverAddress: ${snapshot.serverAddress ?? '(none)'}`,
    '',
    '--- recent events (oldest → newest) ---',
    ...snapshot.recentEvents.map((e) => {
      const d = e.detail ? ` ${JSON.stringify(e.detail)}` : ''
      return `${e.t} [${e.tag}]${d}`
    }),
    '======== END BEDRUD MEETING DEBUG LOG ========',
  ].join('\n')
}

export async function copyMeetingDebugLog(room: Room): Promise<string> {
  const snapshot = await buildMeetingDebugSnapshot(room)
  const text = formatMeetingDebugSnapshot(snapshot)
  meetingDebugLog('debug.copied', {
    eventCount: snapshot.recentEvents.length,
    publishReady: snapshot.room.publishReady,
    transportDead: snapshot.room.transportDead,
    iceMode: snapshot.iceMode,
  })
  console.log(text)
  try {
    await navigator.clipboard.writeText(text)
  } catch {
    // Clipboard may be blocked; text is still in console.
  }
  return text
}

/** Attach helpers for DevTools: window.__bedrudMeetingDebug */
export function installMeetingDebugGlobals(getRoom: () => Room | undefined): void {
  if (typeof window === 'undefined') return
  const api = {
    log: meetingDebugLog,
    events: getMeetingDebugEvents,
    clear: meetingDebugClear,
    setMeta: meetingDebugSetMeta,
    async dump() {
      const room = getRoom()
      if (!room) {
        const msg = 'No LiveKit room — join a meeting first'
        console.warn('[bedrud-meet]', msg)
        return msg
      }
      return copyMeetingDebugLog(room)
    },
    async snapshot() {
      const room = getRoom()
      if (!room) return null
      return buildMeetingDebugSnapshot(room)
    },
  }
  ;(window as unknown as { __bedrudMeetingDebug: typeof api }).__bedrudMeetingDebug = api
  meetingDebugLog('debug.globals', { hint: 'await window.__bedrudMeetingDebug.dump()' })
}
