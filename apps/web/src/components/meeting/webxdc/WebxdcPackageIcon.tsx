import { Package } from 'lucide-react'
import { useEffect, useState } from 'react'
import { API_URL } from '#/lib/api'
import { useAuthStore } from '#/lib/auth.store'
import { cn } from '#/lib/utils'

/** Package ids from the server are UUID hex only — never pass arbitrary strings into paths. */
const SAFE_PACKAGE_ID = /^[0-9a-fA-F-]{8,64}$/

const SAFE_IMAGE_TYPES = new Set(['image/png', 'image/jpeg', 'image/gif', 'image/webp'])

type Props = {
  /** Server package UUID (instance or room package). */
  packageId?: string
  /** Remote/semi-remote HTTPS icon — only used when packageId icon is not available. */
  remoteIconUrl?: string
  hasIcon?: boolean
  name: string
  className?: string
}

/**
 * Renders a package icon. Instance/room icons load via authenticated fetch → blob URL
 * (Bearer header; plain <img src="/api/..."> cannot send Authorization).
 * Remote catalog icons may use HTTPS iconUrl with referrerPolicy=no-referrer.
 */
export function WebxdcPackageIcon({ packageId, remoteIconUrl, hasIcon, name, className }: Props) {
  const [src, setSrc] = useState<string | null>(null)

  useEffect(() => {
    setSrc(null)
    const id = (packageId || '').trim()
    const remote = (remoteIconUrl || '').trim()
    let cancelled = false
    let objectUrl: string | null = null

    const applyBlob = (blob: Blob) => {
      if (cancelled) return
      objectUrl = URL.createObjectURL(blob)
      setSrc(objectUrl)
    }

    const load = async () => {
      // 1) Same-origin authenticated package icon (works under COEP as blob:).
      if (hasIcon && id && SAFE_PACKAGE_ID.test(id)) {
        try {
          const token = useAuthStore.getState().tokens?.accessToken
          const headers: Record<string, string> = {}
          if (token) headers.Authorization = `Bearer ${token}`
          const path = `/api/webxdc/packages/${encodeURIComponent(id)}/icon`
          const res = await fetch(`${API_URL}${path}`, { credentials: 'include', headers })
          if (res.ok && !cancelled) {
            const ct = (res.headers.get('Content-Type') || '').split(';')[0]?.trim().toLowerCase() ?? ''
            if (SAFE_IMAGE_TYPES.has(ct)) {
              applyBlob(await res.blob())
              return
            }
          }
        } catch {
          // fall through to remote
        }
      }
      // 2) Remote HTTPS icons: never use as raw <img src> under COEP require-corp
      // (external hosts lack CORP). Fetch as blob when CORS allows; otherwise placeholder.
      if (remote && /^https:\/\//i.test(remote)) {
        try {
          const res = await fetch(remote, { mode: 'cors', credentials: 'omit', referrerPolicy: 'no-referrer' })
          if (!res.ok || cancelled) return
          const ct = (res.headers.get('Content-Type') || '').split(';')[0]?.trim().toLowerCase() ?? ''
          if (!SAFE_IMAGE_TYPES.has(ct) && !ct.startsWith('image/')) return
          applyBlob(await res.blob())
        } catch {
          // Optional decoration — leave placeholder.
        }
      }
    }

    void load()
    return () => {
      cancelled = true
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [packageId, hasIcon, remoteIconUrl])

  const displaySrc = src

  if (!displaySrc) {
    return (
      <div
        className={cn(
          'meet-gallery-app-icon flex shrink-0 items-center justify-center bg-[var(--meet-btn-muted-bg)] text-[var(--meet-btn-muted-fg)]',
          className,
        )}
        aria-hidden
      >
        <Package className="h-6 w-6" />
      </div>
    )
  }

  return (
    <img
      src={displaySrc}
      alt=""
      title={name}
      className={cn('meet-gallery-app-icon shrink-0 object-cover', className)}
      referrerPolicy="no-referrer"
      draggable={false}
    />
  )
}
