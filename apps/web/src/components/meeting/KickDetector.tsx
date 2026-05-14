import { useRoomContext } from '@livekit/components-react'
import { DisconnectReason, RoomEvent } from 'livekit-client'
import { useEffect } from 'react'

interface KickDetectorProps {
  onKicked: () => void
  onRoomDeleted: () => void
}

export function KickDetector({ onKicked, onRoomDeleted }: KickDetectorProps) {
  const room = useRoomContext()

  useEffect(() => {
    const handler = (reason?: DisconnectReason) => {
      if (reason === DisconnectReason.PARTICIPANT_REMOVED) {
        onKicked()
      } else if (reason === DisconnectReason.ROOM_DELETED) {
        onRoomDeleted()
      }
    }
    room.on(RoomEvent.Disconnected, handler)
    return () => {
      room.off(RoomEvent.Disconnected, handler)
    }
  }, [room, onKicked, onRoomDeleted])

  return null
}
