import { useParticipants } from '@livekit/components-react'
import { Video } from 'lucide-react'

import { cn } from '#/lib/utils'
import { ParticipantTile } from './ParticipantTile'

interface ParticipantGridProps {
  pinnedIdentities: Set<string>
  onTogglePin: (identity: string) => void
}

function gridCols(count: number): string {
  if (count === 1) return 'grid-cols-1'
  if (count <= 4) return 'grid-cols-1 sm:grid-cols-2'
  if (count <= 9) return 'grid-cols-2 sm:grid-cols-3'
  return 'grid-cols-2 sm:grid-cols-3 lg:grid-cols-4'
}

export function ParticipantGrid({ pinnedIdentities, onTogglePin }: ParticipantGridProps) {
  const participants = useParticipants()

  if (participants.length === 0) {
    return (
      <div className="absolute inset-0 z-0 pt-[calc(56px+env(safe-area-inset-top))] pb-[calc(88px+env(safe-area-inset-bottom))] flex flex-col items-center justify-center gap-5">
        <div
          className="w-20 h-20 rounded-full flex items-center justify-center"
          style={{
            background: 'color-mix(in oklab, var(--primary) 10%, transparent)',
            border: '1px solid color-mix(in oklab, var(--primary) 20%, transparent)',
          }}
        >
          <Video size={32} className="text-[color-mix(in_oklab,var(--primary)_55%,transparent)]" />
        </div>
        <p className="text-white/30 text-sm">Waiting for others to join…</p>
      </div>
    )
  }

  return (
    <div className="absolute inset-0 z-0 pt-[calc(56px+env(safe-area-inset-top))] pb-[calc(88px+env(safe-area-inset-bottom))]">
      <div
        className={cn(
          'grid h-full w-full grid-auto-rows-[1fr]',
          gridCols(participants.length),
          participants.length === 1 ? 'gap-0 p-0' : 'gap-[3px] p-[3px]',
        )}
      >
        {participants.map((p, i) => (
          <ParticipantTile
            key={p.identity}
            participant={p}
            totalCount={participants.length}
            index={i}
            isPinned={pinnedIdentities.has(p.identity)}
            onTogglePin={() => onTogglePin(p.identity)}
          />
        ))}
      </div>
    </div>
  )
}
