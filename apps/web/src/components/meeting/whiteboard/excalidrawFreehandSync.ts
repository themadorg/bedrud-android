import type { OrderedExcalidrawElement } from '@excalidraw/excalidraw/element/types'
import type { AppState } from '@excalidraw/excalidraw/types'

type PointList = readonly (readonly [number, number])[]

export function pointCount(el: OrderedExcalidrawElement): number {
  if (!('points' in el) || !Array.isArray(el.points)) return 0
  return (el.points as PointList).length
}

export function isPointBasedElement(el: OrderedExcalidrawElement): boolean {
  return el.type === 'freedraw' || el.type === 'line' || el.type === 'arrow'
}

/** True while Excalidraw has an in-progress shape (freehand / multi-point / new element). */
export function isLocallyDrawing(appState: AppState): boolean {
  if (appState.newElement) return true
  if (appState.multiElement) return true
  if (appState.selectedLinearElement?.isEditing) return true
  if (appState.resizingElement) return true
  if (appState.editingTextElement) return true
  return false
}

/**
 * Freehand mutates `appState.newElement` in place; the scene array can lag or
 * hold a detached clone after a remote updateScene. Always prefer the live
 * drawing object when syncing so peers get the full stroke.
 */
export function ensureInProgressDrawingInScene(
  elements: readonly OrderedExcalidrawElement[],
  appState: AppState,
): OrderedExcalidrawElement[] {
  const drawing = (appState.newElement ?? appState.multiElement) as OrderedExcalidrawElement | null
  if (!drawing || drawing.isDeleted) {
    return elements as OrderedExcalidrawElement[]
  }

  let found = false
  const next = elements.map((el) => {
    if (el.id !== drawing.id) return el
    found = true
    // Prefer the live drawing object (may have more points than a detached scene copy).
    if (isPointBasedElement(drawing) || isPointBasedElement(el)) {
      return pointCount(drawing) >= pointCount(el) ? drawing : el
    }
    return drawing.version >= el.version ? drawing : el
  })

  if (!found) next.push(drawing)
  return next
}

/**
 * Prefer the more complete stroke for freehand/linear paths.
 * Version alone is not enough: mid-stroke remote echoes can carry a higher/equal
 * version with fewer points and would otherwise truncate the line.
 *
 * Pattern learned from Excalidraw collab reconcile + y-excalidraw version tracking.
 */
export function pickNewerElement(
  local: OrderedExcalidrawElement,
  remote: OrderedExcalidrawElement,
): OrderedExcalidrawElement {
  const localPoints = pointCount(local)
  const remotePoints = pointCount(remote)
  const pointBased = isPointBasedElement(local) || isPointBasedElement(remote)

  if (pointBased && localPoints !== remotePoints) {
    // Same id with more points is almost always a freehand extension of the shorter one.
    if (localPoints > remotePoints) return local
    if (remotePoints > localPoints) return remote
  }

  if (remote.version > local.version) return remote
  if (local.version > remote.version) return local

  // Same version: deterministic like official collab (lower versionNonce wins).
  if (local.versionNonce <= remote.versionNonce) return local
  return remote
}

/**
 * Official Excalidraw collab rule: never replace the element the user is
 * actively creating/editing (newElement freehand, text, resize, multi-point).
 */
export function shouldKeepLocalElement(
  appState: AppState,
  local: OrderedExcalidrawElement | undefined,
  remote: OrderedExcalidrawElement,
): boolean {
  if (!local) return false

  if (
    local.id === appState.editingTextElement?.id ||
    local.id === appState.resizingElement?.id ||
    local.id === appState.newElement?.id ||
    local.id === appState.multiElement?.id
  ) {
    return true
  }

  // Freehand growth: keep the longer path even when versions race.
  if (isPointBasedElement(local) || isPointBasedElement(remote)) {
    const lp = pointCount(local)
    const rp = pointCount(remote)
    if (lp > rp) return true
    if (rp > lp) return false
  }

  if (local.version > remote.version) return true
  if (local.version === remote.version && local.versionNonce <= remote.versionNonce) return true
  return false
}

export function pointsSignature(points: PointList | undefined): string {
  if (!points || points.length === 0) return '0'
  const first = points[0]
  const mid = points[Math.floor(points.length / 2)]
  const last = points[points.length - 1]
  return `${points.length}:${first?.[0]},${first?.[1]}:${mid?.[0]},${mid?.[1]}:${last?.[0]},${last?.[1]}`
}
