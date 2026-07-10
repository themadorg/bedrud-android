import type { CollaboratorPointer, ExcalidrawImperativeAPI, Gesture } from '@excalidraw/excalidraw/types'
import { lazy, Suspense, useCallback, useEffect, useRef, useState } from 'react'
import type * as Y from 'yjs'
import type { bindExcalidrawToYDoc } from '@/components/meeting/whiteboard/excalidrawYjsBinding'
import { attachWhiteboardCursorSync } from '@/components/meeting/whiteboard/whiteboardCursorSync'
import type { ElementLockSnapshot } from '@/components/meeting/whiteboard/whiteboardElementLocks'
import { handleWhiteboardEscapeKey } from '@/components/meeting/whiteboard/whiteboardKeyboard'
import { attachWhiteboardRightDragPan } from '@/components/meeting/whiteboard/whiteboardRightDragPan'
import { releaseWhiteboardCursors } from '@/components/meeting/whiteboard/whiteboardTeardown'
import { applyWhiteboardToolCursor } from '@/components/meeting/whiteboard/whiteboardToolCursors'

/** Vendored Excalidraw surface (Excalidraw + MainMenu + CSS) — one React graph. */
const ExcalidrawCanvas = lazy(() => import('@/components/meeting/whiteboard/excalidrawLazy'))

interface MeetingSharedWhiteboardProps {
  ydoc: Y.Doc
  localIdentity: string
  getLocks: () => ElementLockSnapshot
  onApiReady: (api: ExcalidrawImperativeAPI) => void
  onClose?: () => void
  onSyncFlush?: () => void
  onPointerUpdate?: (payload: {
    pointer: CollaboratorPointer
    button: 'up' | 'down'
    pointersMap: Gesture['pointers']
  }) => void
  onPointerUp?: () => void
  onViewportChange?: () => void
}

export function MeetingSharedWhiteboard({
  ydoc,
  localIdentity,
  getLocks,
  onApiReady,
  onClose,
  onSyncFlush,
  onPointerUpdate,
  onPointerUp,
  onViewportChange,
}: MeetingSharedWhiteboardProps) {
  const apiRef = useRef<ExcalidrawImperativeAPI | null>(null)
  const shellRef = useRef<HTMLDivElement>(null)
  const bindingRef = useRef<ReturnType<typeof bindExcalidrawToYDoc> | null>(null)
  const [panSurface, setPanSurface] = useState<HTMLElement | null>(null)

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      const api = apiRef.current
      if (!api) return
      handleWhiteboardEscapeKey(event, api)
    }

    document.addEventListener('keydown', onKeyDown, true)
    return () => {
      document.removeEventListener('keydown', onKeyDown, true)
      bindingRef.current?.destroy()
      bindingRef.current = null
      releaseWhiteboardCursors(apiRef.current, shellRef.current)
      apiRef.current = null
    }
  }, [])

  useEffect(() => {
    const api = apiRef.current
    if (!api) return

    // Rebind when ydoc / identity changes (canvas already mounted).
    void import('@/components/meeting/whiteboard/excalidrawYjsBinding').then(({ bindExcalidrawToYDoc: bind }) => {
      bindingRef.current?.destroy()
      bindingRef.current = bind(api, ydoc, { localIdentity, getLocks })
    })
  }, [getLocks, localIdentity, ydoc])

  const handleViewportChange = useCallback(() => {
    onViewportChange?.()
    const api = apiRef.current
    if (!api) return
    api.refresh()
    window.dispatchEvent(new Event('resize'))
  }, [onViewportChange])

  useEffect(() => {
    if (!panSurface) return
    const detachPan = attachWhiteboardRightDragPan(panSurface, () => apiRef.current, handleViewportChange)
    const detachCursor = attachWhiteboardCursorSync(panSurface, () => apiRef.current)
    return () => {
      detachPan()
      detachCursor()
    }
  }, [handleViewportChange, panSurface])

  // biome-ignore lint/correctness/useExhaustiveDependencies: refs don't change, intentional exclusion
  useEffect(() => {
    const shell = shellRef.current
    const api = apiRef.current
    if (!shell || !api) return

    let timer: ReturnType<typeof setTimeout> | null = null
    const scheduleRefresh = () => {
      if (timer != null) return
      timer = setTimeout(() => {
        timer = null
        api.refresh()
        applyWhiteboardToolCursor(api)
      }, 150)
    }

    const observer = new ResizeObserver(scheduleRefresh)
    observer.observe(shell)

    return () => {
      if (timer != null) clearTimeout(timer)
      observer.disconnect()
    }
  }, [panSurface])

  return (
    <Suspense
      fallback={
        <div className="flex h-full w-full items-center justify-center text-sm text-white/50">Loading whiteboard…</div>
      }
    >
      <div
        ref={shellRef}
        className="bedrud-whiteboard-shell h-full w-full [&_.excalidraw]:h-full [&_.context-menu]:hidden [&_.excalidraw-contextMenuContainer]:hidden"
      >
        <ExcalidrawCanvas
          apiRef={apiRef}
          shellRef={shellRef}
          bindingRef={bindingRef}
          setPanSurface={setPanSurface}
          ydoc={ydoc}
          localIdentity={localIdentity}
          getLocks={getLocks}
          onApiReady={onApiReady}
          onClose={onClose}
          onSyncFlush={onSyncFlush}
          onPointerUpdate={onPointerUpdate}
          onPointerUp={onPointerUp}
        />
      </div>
    </Suspense>
  )
}
