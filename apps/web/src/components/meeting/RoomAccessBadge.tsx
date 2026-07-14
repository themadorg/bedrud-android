import { Globe, Lock } from 'lucide-react'
import { useMeetingRoomContext } from '@/components/meeting/MeetingContext'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

type Props = {
  onOpen: () => void
  variant?: 'default' | 'rail'
}

function meetChromeButtonClass(active: boolean, className?: string) {
  return cn(
    'cursor-pointer backdrop-blur-lg transition-all duration-150',
    active
      ? 'border border-[color-mix(in_oklab,var(--accent-600)_28%,transparent)] bg-[var(--meet-btn-muted-bg)] text-[var(--meet-btn-muted-fg)]'
      : 'border border-[var(--meet-border-subtle)] bg-[var(--meet-chrome)] text-[var(--meet-fg-muted)] hover:text-[var(--meet-fg-strong)]',
    className,
  )
}

export function RoomAccessBadge({ onOpen, variant = 'default' }: Props) {
  const { isPublic } = useMeetingRoomContext()
  const Icon = isPublic ? Globe : Lock
  const label = isPublic ? 'Public room — change access' : 'Private room — change access'

  if (variant === 'rail') {
    return (
      <Button
        type="button"
        variant="ghost"
        size="icon"
        className={cn(
          'h-9 w-9 shrink-0',
          isPublic &&
            'bg-[color-mix(in_oklab,var(--accent-600)_18%,transparent)] text-accent-400 hover:bg-[color-mix(in_oklab,var(--accent-600)_24%,transparent)] hover:text-accent-400',
        )}
        aria-label={label}
        onClick={onOpen}
      >
        <Icon className="h-4 w-4" />
      </Button>
    )
  }

  return (
    <button
      type="button"
      onClick={onOpen}
      className={cn(
        'flex h-8 w-8 items-center justify-center rounded-lg backdrop-blur-lg transition-all duration-150',
        isPublic
          ? 'border border-[color-mix(in_oklab,var(--accent-600)_35%,transparent)] bg-[color-mix(in_oklab,var(--accent-600)_18%,transparent)] text-accent-400'
          : meetChromeButtonClass(false),
      )}
      aria-label={label}
    >
      <Icon size={14} />
    </button>
  )
}
