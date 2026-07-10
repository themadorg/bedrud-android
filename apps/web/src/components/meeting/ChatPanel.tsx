import { Pin, X } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import {
  type ChatAttachment,
  type ChatMessage,
  type ChatPoll,
  normalizeChatAttachment,
  type SystemMessage,
} from '@/components/meeting/MeetingContext'
import { api } from '@/lib/api'
import { cn } from '@/lib/utils'
import { ChatInput, type ChatInputHandle } from './chat/ChatInput'
import { ChatMessageList } from './chat/ChatMessageList'
import { useFocusTrap } from './useFocusTrap'

/** Matches Tailwind `sm` and ControlsBar mobile breakpoint (640px). */
const MOBILE_MAX_WIDTH_MQ = '(max-width: 639px)'

interface Props {
  onClose: () => void
  roomId: string
  currentIdentity: string
  chatMessages: ChatMessage[]
  systemMessages: SystemMessage[]
  sendChat: (text: string, attachments?: ChatAttachment[], poll?: ChatPoll) => void
  markRead: () => void
  votePoll: (messageId: string, optionId: string) => void
  reactToMessage: (messageId: string, emoji: string) => void
  stuck?: boolean
  onStuckChange?: (stuck: boolean) => void
}

const headerBtnClass = (active = false) =>
  cn(
    'flex h-7 w-7 shrink-0 items-center justify-center rounded-[7px] border-none bg-transparent cursor-pointer transition-[background,color] duration-150',
    active ? 'text-accent-400' : 'text-white/50 hover:text-white/70',
  )

function useIsMobileChat() {
  const [isMobile, setIsMobile] = useState(() =>
    typeof window !== 'undefined' ? window.matchMedia(MOBILE_MAX_WIDTH_MQ).matches : false,
  )
  useEffect(() => {
    const mq = window.matchMedia(MOBILE_MAX_WIDTH_MQ)
    const onChange = () => setIsMobile(mq.matches)
    onChange()
    mq.addEventListener('change', onChange)
    return () => mq.removeEventListener('change', onChange)
  }, [])
  return isMobile
}

export function ChatPanel({
  onClose,
  roomId,
  currentIdentity,
  chatMessages,
  systemMessages,
  sendChat,
  markRead,
  votePoll,
  reactToMessage,
  stuck = false,
  onStuckChange,
}: Props) {
  const inputRef = useRef<ChatInputHandle>(null)
  const noop = useCallback(() => {}, [])
  const isMobile = useIsMobileChat()
  // Mobile is always a full-screen modal — never dock/stick.
  const isDocked = stuck && !isMobile

  useEffect(() => {
    markRead()
    const t = setTimeout(() => inputRef.current?.focus(), 80)
    return () => clearTimeout(t)
  }, [markRead])

  // Clear pin/dock when entering mobile so desktop stick state does not leak.
  useEffect(() => {
    if (isMobile && stuck) onStuckChange?.(false)
  }, [isMobile, stuck, onStuckChange])

  const uploadAndSend = useCallback(
    async (file: File): Promise<ChatAttachment> => {
      const form = new FormData()
      form.append('file', file)
      const raw = await api.post<ChatAttachment>(`/api/room/${roomId}/chat/upload`, form)
      const attachment = normalizeChatAttachment(raw)
      if (!attachment) throw new Error('Invalid upload response')
      return attachment
    },
    [roomId],
  )

  // Modal on mobile / undocked panel: trap focus. Docked desktop sidebar: leave focus free.
  const trapRef = useFocusTrap({ enabled: !isDocked, onClose })

  return (
    <aside
      ref={trapRef}
      role="dialog"
      aria-modal={!isDocked}
      aria-label="Chat"
      className={cn(
        'z-40 flex flex-col bg-[var(--meet-sidebar)] backdrop-blur-2xl pt-[env(safe-area-inset-top)] pb-[env(safe-area-inset-bottom)] transition-[left,right,width] duration-200',
        // Mobile: full-screen modal above floating chrome (controls, toggles).
        'fixed inset-0 w-full',
        // Desktop: right sidebar (320px), absolute unless pinned/docked.
        'sm:inset-y-0 sm:left-auto sm:right-0 sm:w-[min(320px,100vw)] sm:border-l sm:border-[var(--meet-border-subtle)]',
        isDocked ? 'sm:fixed' : 'sm:absolute',
      )}
    >
      <div className="flex h-[52px] shrink-0 items-center justify-between border-b border-white/[0.06] px-4">
        <span className="text-base font-semibold text-white/85">Chat</span>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => onStuckChange?.(!stuck)}
            className={cn(headerBtnClass(stuck), 'max-sm:hidden')}
            aria-label={stuck ? 'Unstick chat' : 'Stick chat open'}
            aria-pressed={stuck}
          >
            <Pin size={15} className={stuck ? 'fill-current' : ''} />
          </button>
          <button type="button" onClick={onClose} className={headerBtnClass()} aria-label="Close chat">
            <X size={15} />
          </button>
        </div>
      </div>

      {/* Message list */}
      <ChatMessageList
        chatMessages={chatMessages}
        systemMessages={systemMessages}
        currentIdentity={currentIdentity}
        onVotePoll={votePoll}
        onReactToMessage={reactToMessage}
        onScrollUnreadChange={noop}
        onDrop={(file) => {
          inputRef.current?.attachFile(file)
        }}
      />

      {/* Input */}
      <ChatInput ref={inputRef} onSend={sendChat} onUpload={uploadAndSend} />
    </aside>
  )
}
