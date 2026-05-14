import { useMutation, useQuery } from '@tanstack/react-query'
import { createFileRoute } from '@tanstack/react-router'
import { Check, Loader2, Monitor } from 'lucide-react'
import { useEffect, useRef } from 'react'
import { api } from '#/lib/api'
import { useVideoPreferencesStore } from '#/lib/video-preferences.store'
import { Switch } from '@/components/ui/switch'

export const Route = createFileRoute('/dashboard/settings/video')({
  component: VideoPage,
})

function VideoPage() {
  const mirrorWebcam = useVideoPreferencesStore((s) => s.mirrorWebcam)
  const setMirrorWebcam = useVideoPreferencesStore((s) => s.setMirrorWebcam)
  const merge = useVideoPreferencesStore((s) => s.merge)

  const { data: remotePrefs } = useQuery({
    queryKey: ['preferences'],
    queryFn: () => api.get<{ preferencesJson: string }>('/api/auth/preferences'),
  })

  useEffect(() => {
    if (!remotePrefs?.preferencesJson) return
    try {
      const parsed = JSON.parse(remotePrefs.preferencesJson)
      if (parsed?.video) merge(parsed.video)
    } catch {
      /* ignore malformed data */
    }
  }, [remotePrefs, merge])

  const syncMutation = useMutation({
    mutationFn: (prefsJson: string) => api.put('/api/auth/preferences', { preferencesJson: prefsJson }),
  })
  const mutateRef = useRef(syncMutation.mutate)
  mutateRef.current = syncMutation.mutate

  useEffect(() => {
    const prefs = { video: { mirrorWebcam } }
    const timer = setTimeout(() => mutateRef.current(JSON.stringify(prefs)), 1000)
    return () => clearTimeout(timer)
  }, [mirrorWebcam])

  const syncStatus = syncMutation.isPending
    ? 'saving'
    : syncMutation.isError
      ? 'error'
      : syncMutation.isSuccess
        ? 'saved'
        : 'idle'

  return (
    <div className="border bg-card/50">
      <div className="flex items-center justify-between px-5 py-4">
        <div className="flex items-center gap-3">
          <Monitor className="h-4 w-4 text-muted-foreground" />
          <div>
            <p className="text-sm font-medium">Mirror my video</p>
            <p className="text-xs text-muted-foreground">
              Show your video mirrored (like a mirror) so you see yourself as others see you
            </p>
          </div>
        </div>
        <Switch checked={mirrorWebcam} onCheckedChange={setMirrorWebcam} />
      </div>

      {syncStatus !== 'idle' && (
        <div className="flex items-center justify-end gap-1.5 border-t px-5 py-2.5">
          {syncStatus === 'saving' && <Loader2 className="h-3 w-3 animate-spin text-muted-foreground/50" />}
          {syncStatus === 'saved' && <Check className="h-3 w-3 text-emerald-500" />}
          <span className="text-[11px] text-muted-foreground/50">
            {syncStatus === 'saving' && 'Saving...'}
            {syncStatus === 'saved' && 'Saved'}
            {syncStatus === 'error' && 'Sync failed'}
          </span>
        </div>
      )}
    </div>
  )
}
