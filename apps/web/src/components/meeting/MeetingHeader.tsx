import { useConnectionState } from '@livekit/components-react'
import { ConnectionState } from 'livekit-client'
import { Radio } from 'lucide-react'
import { useEffect, useState } from 'react'

import { cn } from '#/lib/utils'

interface MeetingHeaderProps {
  meetId: string
}

/** Top-of-screen header showing live indicator, room name, clock, and connection status. */
export function MeetingHeader({ meetId }: MeetingHeaderProps) {
  const state = useConnectionState()
  const [time, setTime] = useState(() => new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }))

  useEffect(() => {
    const id = setInterval(() => {
      setTime(new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }))
    }, 10_000)
    return () => clearInterval(id)
  }, [])

  const isConnected = state === ConnectionState.Connected

  return (
    <header className="absolute left-0 right-0 top-0 z-20 flex items-center justify-center px-4 pointer-events-none h-[calc(56px+env(safe-area-inset-top))] pt-[env(safe-area-inset-top)]">
      <div className="flex items-center gap-2.5 pointer-events-auto">
        <div
          className="flex items-center gap-[5px] rounded-[7px] px-[9px] py-[3px]"
          style={{
            background: 'color-mix(in oklab, var(--accent-400) 20%, transparent)',
            border: '1px solid color-mix(in oklab, var(--accent-400) 40%, transparent)',
          }}
        >
          <Radio size={11} className="text-[var(--accent-400)]" />
          <span className="text-[var(--accent-300)] text-[11px] font-bold tracking-widest">LIVE</span>
        </div>
        <span className="text-white/25 text-[13px]">·</span>
        <span className="text-white/55 text-xs font-mono">{meetId}</span>
        <span className="text-white/25 text-[13px]">·</span>
        <span className="text-white/25 text-[11px] font-mono">{time}</span>
        <span className="text-white/25 text-[13px]">·</span>
        <div
          className="flex items-center gap-[5px] rounded-[7px] px-[9px] py-[3px]"
          style={{
            background: isConnected ? 'rgba(34,197,94,0.12)' : 'rgba(234,179,8,0.12)',
            border: `1px solid ${isConnected ? 'rgba(34,197,94,0.25)' : 'rgba(234,179,8,0.25)'}`,
          }}
        >
          {isConnected ? (
            <span className="inline-block w-1.5 h-1.5 rounded-full bg-green-500" />
          ) : (
            <svg
              className="meet-connecting"
              width="10"
              height="10"
              viewBox="0 0 10 10"
              role="img"
              aria-label="Connecting"
            >
              <circle cx="5" cy="5" r="4" fill="none" stroke="#eab308" strokeWidth="1.5" strokeDasharray="6 4" />
            </svg>
          )}
          <span className={cn('text-[11px] font-medium', isConnected ? 'text-green-300' : 'text-yellow-300')}>
            {isConnected ? 'Connected' : state}
          </span>
        </div>
      </div>
    </header>
  )
}
