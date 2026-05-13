import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export interface VideoPreferences {
  mirrorWebcam: boolean
}

interface VideoPreferencesState extends VideoPreferences {
  setMirrorWebcam: (enabled: boolean) => void
  merge: (partial: Partial<VideoPreferences>) => void
}

export const useVideoPreferencesStore = create<VideoPreferencesState>()(
  persist(
    (set) => ({
      mirrorWebcam: true,
      setMirrorWebcam: (enabled) => set({ mirrorWebcam: enabled }),
      merge: (partial) => set(partial),
    }),
    { name: 'video-preferences' },
  ),
)
