import { useCallback, useMemo } from 'react'
import type { ChatMessage } from '../MeetingContext'

export function useChatPersistence(
  roomId: string,
  maxMessageCount: number,
  messageTTLHours: number,
): [ChatMessage[], (msgs: ChatMessage[]) => void] {
  const key = `chat:${roomId}`

  const initialMessages = useMemo<ChatMessage[]>(() => {
    try {
      const raw = sessionStorage.getItem(key)
      const msgs = raw ? (JSON.parse(raw) as ChatMessage[]) : []
      if (messageTTLHours > 0) {
        const cutoff = Date.now() - messageTTLHours * 60 * 60 * 1000
        return msgs.filter((m) => m.timestamp >= cutoff)
      }
      return msgs
    } catch {
      return []
    }
  }, [key, messageTTLHours])

  const persist = useCallback(
    (msgs: ChatMessage[]) => {
      try {
        let toStore = msgs
        if (maxMessageCount > 0 && toStore.length > maxMessageCount) {
          toStore = toStore.slice(toStore.length - maxMessageCount)
        }
        sessionStorage.setItem(key, JSON.stringify(toStore))
      } catch {
        // sessionStorage unavailable (private browsing quota exceeded)
      }
    },
    [key, maxMessageCount],
  )

  return [initialMessages, persist]
}
