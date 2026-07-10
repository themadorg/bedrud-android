/**
 * Excalidraw ↔ Yjs binding modeled on context/y-excalidraw (webxdc-correct path).
 *
 * Structure:
 *   ydoc.getArray('elements') → Y.Array<Y.Map<{ pos, el }>>
 *   ydoc.getMap('assets') / settings / locks
 *
 * Freehand: version bumps every point, but we **throttle** Yjs writes while the pen
 * is down. Writing the full stroke on every pointermove freezes the main thread
 * after ~1–2s (hundreds of points × Yjs encode × LiveKit publish) and the stroke
 * appears to stop even though the user is still dragging.
 */
import type { OrderedExcalidrawElement } from '@excalidraw/excalidraw/element/types'
import type { AppState, BinaryFileData, BinaryFiles, ExcalidrawImperativeAPI } from '@excalidraw/excalidraw/types'
import * as Y from 'yjs'
import { canEditElement, type ElementLockSnapshot, WHITEBOARD_LOCKS_ORIGIN } from './whiteboardElementLocks'
import {
  pickSyncableSettings,
  settingsSignature,
  WHITEBOARD_SYNC_SETTINGS_KEYS,
  type WhiteboardSyncSettings,
} from './whiteboardSyncSettings'
import {
  applyAssetOperations,
  applyElementOperations,
  getDeltaOperationsForAssets,
  getDeltaOperationsForElements,
  type LastKnownOrderedElement,
} from './yExcalidrawDiff'
import { areElementsSame, type YElementEntry, yElementById, yjsToExcalidraw } from './yExcalidrawHelpers'

export const EXCALIDRAW_YJS_ORIGIN = Symbol('excalidraw-yjs-binding')

/** Max freehand Yjs pushes per second while pen is down (remote stream still smooth). */
const FREEHAND_SYNC_MS = 100

export type BindExcalidrawToYDocOptions = {
  localIdentity: string
  getLocks: () => ElementLockSnapshot
}

export interface ExcalidrawYjsBinding {
  onExcalidrawChange: (elements: readonly OrderedExcalidrawElement[], appState: AppState, files: BinaryFiles) => void
  flush: () => void
  destroy: () => void
}

function readSettingsFromDoc(doc: Y.Doc): Partial<WhiteboardSyncSettings> | null {
  const ySettings = doc.getMap<WhiteboardSyncSettings[keyof WhiteboardSyncSettings]>('settings')
  const settings: Partial<WhiteboardSyncSettings> = {}
  let hasAny = false

  for (const key of WHITEBOARD_SYNC_SETTINGS_KEYS) {
    const value = ySettings.get(key)
    if (key === 'viewBackgroundColor') {
      if (typeof value !== 'string') continue
      settings.viewBackgroundColor = value
      hasAny = true
      continue
    }
    if (key === 'gridModeEnabled') {
      if (typeof value !== 'boolean') continue
      settings.gridModeEnabled = value
      hasAny = true
    }
  }

  return hasAny ? settings : null
}

function syncSettingsToDoc(doc: Y.Doc, settings: WhiteboardSyncSettings, origin: unknown) {
  const ySettings = doc.getMap<WhiteboardSyncSettings[keyof WhiteboardSyncSettings]>('settings')
  doc.transact(() => {
    for (const key of WHITEBOARD_SYNC_SETTINGS_KEYS) {
      const value = settings[key]
      if (value == null) {
        if (ySettings.has(key)) ySettings.delete(key)
        continue
      }
      if (ySettings.get(key) !== value) ySettings.set(key, value)
    }
  }, origin)
}

function lastKnownFromYArray(yElements: Y.Array<YElementEntry>): LastKnownOrderedElement[] {
  return yElements
    .toArray()
    .map((x) => {
      const el = x.get('el') as OrderedExcalidrawElement
      return { id: el.id, version: el.version, pos: x.get('pos') as string }
    })
    .sort((a, b) => (a.pos > b.pos ? 1 : a.pos < b.pos ? -1 : 0))
}

function filterLockedElements(
  elements: readonly OrderedExcalidrawElement[],
  locks: ElementLockSnapshot,
  localIdentity: string,
  yElements: Y.Array<YElementEntry>,
): OrderedExcalidrawElement[] {
  return elements.map((el) => {
    if (canEditElement(el.id, localIdentity, locks)) return el
    const remote = yElementById(yElements, el.id) as OrderedExcalidrawElement | undefined
    return remote ?? el
  }) as OrderedExcalidrawElement[]
}

/** True while local user has an in-progress freehand stroke. */
function isPenDown(api: ExcalidrawImperativeAPI): boolean {
  try {
    return api.getAppState().newElement?.type === 'freedraw'
  } catch {
    return false
  }
}

/** Any in-progress local creation that must not be interrupted by updateScene. */
function isLocallyDrawing(api: ExcalidrawImperativeAPI): boolean {
  try {
    const s = api.getAppState()
    return !!(s.newElement || s.multiElement || s.selectedLinearElement?.isEditing || s.resizingElement)
  } catch {
    return false
  }
}

/**
 * Deep-clone freehand for Yjs so the live scene element is not shared with CRDT state.
 * Shallow `{...el}` reuses the points array — later mutateElement can race with Yjs encode.
 */
function cloneForYjs(el: OrderedExcalidrawElement): OrderedExcalidrawElement {
  if (el.type === 'freedraw' || el.type === 'line' || el.type === 'arrow') {
    return JSON.parse(JSON.stringify(el)) as OrderedExcalidrawElement
  }
  return { ...el } as OrderedExcalidrawElement
}

export function bindExcalidrawToYDoc(
  api: ExcalidrawImperativeAPI,
  doc: Y.Doc,
  options: BindExcalidrawToYDocOptions,
): ExcalidrawYjsBinding {
  const { localIdentity, getLocks } = options

  const yElements = doc.getArray<YElementEntry>('elements')
  const yAssets = doc.getMap<BinaryFileData>('assets')
  const yFilesLegacy = doc.getMap<BinaryFileData>('files')
  const ySettings = doc.getMap<WhiteboardSyncSettings[keyof WhiteboardSyncSettings]>('settings')

  const origin = Object.create(null) as object

  let lastKnownElements: LastKnownOrderedElement[] = lastKnownFromYArray(yElements)
  let lastKnownFileIds = new Set<string>([...yAssets.keys(), ...yFilesLegacy.keys()])
  let trackedSettingsSignature = ''
  const subscriptions: (() => void)[] = []

  let freehandTimer: number | null = null
  let freehandPushPending = false
  let destroyed = false

  const pushLocalFromApi = () => {
    if (destroyed) return

    const elements = filterLockedElements(
      api.getSceneElements() as OrderedExcalidrawElement[],
      getLocks(),
      localIdentity,
      yElements,
    ).map(cloneForYjs)

    const files = api.getFiles()

    if (!areElementsSame(lastKnownElements, elements)) {
      const res = getDeltaOperationsForElements(lastKnownElements, elements)
      lastKnownElements = res.lastKnownElements
      if (res.operations.length > 0) {
        applyElementOperations(yElements, res.operations, origin)
      }
    }

    // Skip asset/settings churn mid freehand — keep the pen path light.
    if (isPenDown(api)) return

    const assetRes = getDeltaOperationsForAssets(lastKnownFileIds, files)
    lastKnownFileIds = assetRes.lastKnownFileIds
    if (assetRes.operations.length > 0) {
      applyAssetOperations(yAssets, assetRes.operations, origin)
    }

    try {
      const settings = pickSyncableSettings(api.getAppState())
      const nextSig = settingsSignature(settings)
      if (nextSig !== trackedSettingsSignature) {
        syncSettingsToDoc(doc, settings, origin)
        trackedSettingsSignature = nextSig
      }
    } catch {
      /* unmount */
    }
  }

  /**
   * Freehand: schedule at most one Yjs write per FREEHAND_SYNC_MS.
   * Non-freehand: push immediately (rects etc.).
   */
  const schedulePush = (force = false) => {
    if (destroyed) return

    if (!force && isPenDown(api)) {
      freehandPushPending = true
      if (freehandTimer != null) return
      freehandTimer = window.setTimeout(() => {
        freehandTimer = null
        if (!freehandPushPending || destroyed) return
        freehandPushPending = false
        pushLocalFromApi()
        // Still pen-down? schedule next sample so path keeps streaming.
        if (isPenDown(api)) schedulePush()
      }, FREEHAND_SYNC_MS)
      return
    }

    if (freehandTimer != null) {
      window.clearTimeout(freehandTimer)
      freehandTimer = null
    }
    freehandPushPending = false
    pushLocalFromApi()
  }

  const unsubOnChange = api.onChange((_elements, state) => {
    // api.onChange is the only push path (do not also push from props onChange).
    const pen = state.newElement?.type === 'freedraw'
    schedulePush(!pen)
  })
  subscriptions.push(unsubOnChange)

  const safeUpdateScene = (payload: {
    elements?: readonly OrderedExcalidrawElement[]
    appState?: Partial<AppState>
    captureUpdate?: 'NEVER'
  }) => {
    // Never replace the scene mid freehand — detaches newElement and freezes the stroke.
    if (isLocallyDrawing(api)) return
    api.updateScene(payload as Parameters<ExcalidrawImperativeAPI['updateScene']>[0])
  }

  const onRemoteElements = (_events: Y.YEvent<Y.AbstractType<unknown>>[], txn: Y.Transaction) => {
    if (txn.origin === origin) return
    if (isLocallyDrawing(api)) return

    const remoteElements = yjsToExcalidraw(yElements) as OrderedExcalidrawElement[]
    const localScene = api.getSceneElements() as OrderedExcalidrawElement[]
    const localById = new Map(localScene.map((el) => [el.id, el]))

    // Prefer remote for ids that exist in Yjs; keep local-only ids.
    const remoteIds = new Set(remoteElements.map((el) => el.id))
    const elements = [
      ...remoteElements.map((el) =>
        localById.get(el.id) && el.version === localById.get(el.id)!.version ? localById.get(el.id)! : el,
      ),
      ...localScene.filter((el) => !remoteIds.has(el.id) && !el.isDeleted),
    ]

    lastKnownElements = lastKnownFromYArray(yElements)

    const remoteSettings = readSettingsFromDoc(doc)
    safeUpdateScene({
      elements,
      ...(remoteSettings
        ? { appState: remoteSettings as Pick<AppState, 'viewBackgroundColor' | 'gridModeEnabled'> }
        : {}),
      captureUpdate: 'NEVER',
    })
  }
  yElements.observeDeep(onRemoteElements)
  subscriptions.push(() => yElements.unobserveDeep(onRemoteElements))

  const onRemoteAssets = (event: Y.YMapEvent<BinaryFileData>, txn: Y.Transaction) => {
    if (txn.origin === origin) return
    if (isLocallyDrawing(api)) return
    const added = [...event.keysChanged].map((key) => yAssets.get(key)).filter((f): f is BinaryFileData => f != null)
    if (added.length > 0) api.addFiles(added)
    lastKnownFileIds = new Set([...yAssets.keys()])
  }
  yAssets.observe(onRemoteAssets)
  subscriptions.push(() => yAssets.unobserve(onRemoteAssets))

  const onRemoteSettings = (_event: unknown, txn: Y.Transaction) => {
    if (txn.origin === origin) return
    if (isLocallyDrawing(api)) return
    const remoteSettings = readSettingsFromDoc(doc)
    if (!remoteSettings) return
    trackedSettingsSignature = settingsSignature(remoteSettings)
    safeUpdateScene({
      appState: remoteSettings as Pick<AppState, 'viewBackgroundColor' | 'gridModeEnabled'>,
      captureUpdate: 'NEVER',
    })
  }
  ySettings.observe(onRemoteSettings)
  subscriptions.push(() => ySettings.unobserve(onRemoteSettings))

  const yLocks = doc.getMap('locks')
  const onRemoteLocks = (_event: unknown, txn: Y.Transaction) => {
    if (txn.origin === origin || txn.origin === WHITEBOARD_LOCKS_ORIGIN) return
    if (isLocallyDrawing(api)) return
    lastKnownElements = lastKnownFromYArray(yElements)
    safeUpdateScene({
      elements: yjsToExcalidraw(yElements) as OrderedExcalidrawElement[],
      captureUpdate: 'NEVER',
    })
  }
  yLocks.observe(onRemoteLocks)
  subscriptions.push(() => yLocks.unobserve(onRemoteLocks))

  if (yElements.length > 0 || ySettings.size > 0) {
    const initial = yjsToExcalidraw(yElements) as OrderedExcalidrawElement[]
    lastKnownElements = lastKnownFromYArray(yElements)
    const initialSettings = readSettingsFromDoc(doc)
    if (initialSettings) trackedSettingsSignature = settingsSignature(initialSettings)
    api.updateScene({
      elements: initial,
      ...(initialSettings
        ? { appState: initialSettings as Pick<AppState, 'viewBackgroundColor' | 'gridModeEnabled'> }
        : {}),
      captureUpdate: 'NEVER',
    })
    const assets = [...yAssets.keys()].map((k) => yAssets.get(k)).filter((f): f is BinaryFileData => f != null)
    if (assets.length > 0) api.addFiles(assets)
  }

  return {
    onExcalidrawChange: () => {
      // Props onChange is for UI only — push is owned by api.onChange to avoid double work.
    },
    flush: () => {
      // Pen-up: force immediate full freehand write.
      if (freehandTimer != null) {
        window.clearTimeout(freehandTimer)
        freehandTimer = null
      }
      freehandPushPending = false
      pushLocalFromApi()
    },
    destroy: () => {
      destroyed = true
      if (freehandTimer != null) {
        window.clearTimeout(freehandTimer)
        freehandTimer = null
      }
      pushLocalFromApi()
      for (const unsub of subscriptions) unsub()
    },
  }
}
