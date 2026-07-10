/**
 * Single lazy entry for vendored Excalidraw UI.
 *
 * Source: apps/web/src/vendor/excalidraw (Vite aliases @excalidraw/* → vendor).
 * Excalidraw + MainMenu must load from this one module so they share React and
 * Excalidraw context (prevents dual-React "useState of null" crashes).
 */
import { Excalidraw, MainMenu } from '@excalidraw/excalidraw'
import type { ExcalidrawImperativeAPI } from '@excalidraw/excalidraw/types'
import { X } from 'lucide-react'
import type { MutableRefObject, RefObject } from 'react'
import type * as Y from 'yjs'
import { bindExcalidrawToYDoc } from '@/components/meeting/whiteboard/excalidrawYjsBinding'
import { WhiteboardMainMenu } from '@/components/meeting/whiteboard/WhiteboardMainMenu'
import type { ElementLockSnapshot } from '@/components/meeting/whiteboard/whiteboardElementLocks'
import { alignRtlTextElements } from '@/components/meeting/whiteboard/whiteboardTextDirection'
import { applyWhiteboardToolCursor } from '@/components/meeting/whiteboard/whiteboardToolCursors'

import '@/vendor/excalidraw/packages/excalidraw/css/app.scss'
import '@/vendor/excalidraw/packages/excalidraw/css/styles.scss'
import '@/vendor/excalidraw/packages/excalidraw/fonts/fonts.css'

export { Excalidraw, MainMenu }

export type ExcalidrawCanvasProps = {
  apiRef: MutableRefObject<ExcalidrawImperativeAPI | null>
  shellRef: RefObject<HTMLDivElement | null>
  bindingRef: MutableRefObject<ReturnType<typeof bindExcalidrawToYDoc> | null>
  setPanSurface: (el: HTMLElement | null) => void
  ydoc: Y.Doc
  localIdentity: string
  getLocks: () => ElementLockSnapshot
  onApiReady: (api: ExcalidrawImperativeAPI) => void
  onClose?: () => void
  onSyncFlush?: () => void
  onPointerUpdate?: React.ComponentProps<typeof Excalidraw>['onPointerUpdate']
  onPointerUp?: () => void
}

/** Full canvas: Excalidraw + MainMenu from the same module graph. */
export default function ExcalidrawCanvas({
  apiRef,
  shellRef,
  bindingRef,
  setPanSurface,
  ydoc,
  localIdentity,
  getLocks,
  onApiReady,
  onClose,
  onSyncFlush,
  onPointerUpdate,
  onPointerUp,
}: ExcalidrawCanvasProps) {
  return (
    <Excalidraw
      autoFocus
      handleKeyboardGlobally
      isCollaborating
      onPointerUpdate={onPointerUpdate}
      onExcalidrawAPI={(api: ExcalidrawImperativeAPI | null) => {
        if (!api) {
          apiRef.current = null
          return
        }
        apiRef.current = api
        onApiReady(api)
        bindingRef.current?.destroy()
        bindingRef.current = bindExcalidrawToYDoc(api, ydoc, { localIdentity, getLocks })
        requestAnimationFrame(() => {
          const container = shellRef.current?.querySelector('.excalidraw')
          if (container instanceof HTMLElement) setPanSurface(container)
          api.refresh()
          applyWhiteboardToolCursor(api)
        })
      }}
      theme="dark"
      onChange={(elements, appState) => {
        // Yjs push is owned by binding api.onChange (throttled freehand).
        // Do not push again here — double writes froze the pen after ~2s.
        const drawing = !!appState.newElement || !!appState.multiElement || !!appState.selectedLinearElement?.isEditing
        if (!drawing) {
          const aligned = alignRtlTextElements(elements)
          if (aligned) {
            apiRef.current?.updateScene({ elements: aligned, captureUpdate: 'NEVER' })
          }
        }
        if (appState.newElement?.type !== 'freedraw') {
          applyWhiteboardToolCursor(apiRef.current, appState)
        }
      }}
      onPointerUp={() => {
        const flushAfterFinalize = () => {
          bindingRef.current?.flush()
          onSyncFlush?.()
        }
        requestAnimationFrame(() => {
          requestAnimationFrame(() => {
            flushAfterFinalize()
            window.setTimeout(flushAfterFinalize, 0)
          })
        })
        onPointerUp?.()
      }}
      renderTopRightUI={() =>
        onClose ? (
          <button
            type="button"
            onClick={onClose}
            aria-label="Close shared whiteboard"
            title="Close whiteboard"
            className="flex h-9 w-9 items-center justify-center rounded-lg border-none cursor-pointer"
            style={{
              background: 'var(--island-bg-color)',
              color: 'var(--icon-fill-color)',
            }}
          >
            <X size={18} />
          </button>
        ) : null
      }
      UIOptions={{
        canvasActions: {
          toggleTheme: false,
          export: false,
          loadScene: false,
          saveToActiveFile: false,
          saveAsImage: false,
          clearCanvas: false,
        },
      }}
    >
      <WhiteboardMainMenu apiRef={apiRef} MainMenu={MainMenu as never} />
    </Excalidraw>
  )
}
