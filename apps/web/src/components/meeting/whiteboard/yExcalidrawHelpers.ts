/**
 * Ported from context/y-excalidraw (webxdc / y-excalidraw binding helpers).
 * Y.Array of Y.Map<{ pos, el }> is the correct CRDT shape for Excalidraw elements.
 */
import type { ExcalidrawElement } from '@excalidraw/excalidraw/element/types'
import type * as Y from 'yjs'

export type YElementEntry = Y.Map<unknown>

export function moveArrayItem<T>(arr: T[], from: number, to: number, inPlace = true): T[] {
  if (!inPlace) arr = [...arr]
  arr.splice(to, 0, arr.splice(from, 1)[0]!)
  return arr
}

export function areElementsSame(
  els1: readonly { id: string; version: number }[],
  els2: readonly { id: string; version: number }[],
): boolean {
  if (els1.length !== els2.length) return false
  for (let i = 0; i < els1.length; i++) {
    if (els1[i]!.id !== els2[i]!.id || els1[i]!.version !== els2[i]!.version) return false
  }
  return true
}

/** Sort by fractional `pos` and unwrap `el` — same as y-excalidraw `yjsToExcalidraw`. */
export function yjsToExcalidraw(yArray: Y.Array<YElementEntry>): ExcalidrawElement[] {
  return yArray
    .toArray()
    .sort((a, b) => {
      const key1 = a.get('pos') as string
      const key2 = b.get('pos') as string
      return key1 > key2 ? 1 : key1 < key2 ? -1 : 0
    })
    .map((entry) => entry.get('el') as ExcalidrawElement)
}

export function yElementById(yArray: Y.Array<YElementEntry>, id: string): ExcalidrawElement | undefined {
  for (let i = 0; i < yArray.length; i++) {
    const el = yArray.get(i).get('el') as ExcalidrawElement | undefined
    if (el?.id === id) return el
  }
  return undefined
}
