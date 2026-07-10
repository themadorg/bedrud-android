import type { OrderedExcalidrawElement } from '@excalidraw/excalidraw/element/types'
import { describe, expect, it } from 'vitest'
import * as Y from 'yjs'
import { applyElementOperations, getDeltaOperationsForElements } from './yExcalidrawDiff'
import { type YElementEntry, yjsToExcalidraw } from './yExcalidrawHelpers'

function freehand(id: string, points: [number, number][], version: number): OrderedExcalidrawElement {
  return {
    id,
    type: 'freedraw',
    x: 0,
    y: 0,
    width: points.length,
    height: points.length,
    angle: 0,
    strokeColor: '#000',
    backgroundColor: 'transparent',
    fillStyle: 'solid',
    strokeWidth: 1,
    strokeStyle: 'solid',
    roughness: 0,
    opacity: 100,
    groupIds: [],
    frameId: null,
    roundness: null,
    seed: 1,
    version,
    versionNonce: version * 7,
    isDeleted: false,
    boundElements: null,
    updated: 1,
    link: null,
    locked: false,
    points,
    pressures: [],
    simulatePressure: true,
  } as unknown as OrderedExcalidrawElement
}

describe('y-excalidraw freehand delta (webxdc-correct path)', () => {
  it('streams freehand growth via version updates into Y.Array', () => {
    const doc = new Y.Doc()
    const yElements = doc.getArray<YElementEntry>('elements')
    let lastKnown: { id: string; version: number; pos: string }[] = []
    const origin = {}

    // Start stroke — 2 points
    let stroke = freehand(
      'pen1',
      [
        [0, 0],
        [1, 1],
      ],
      1,
    )
    let res = getDeltaOperationsForElements(lastKnown, [stroke])
    lastKnown = res.lastKnownElements
    applyElementOperations(yElements, res.operations, origin)

    expect((yjsToExcalidraw(yElements)[0] as unknown as { points: unknown[] }).points).toHaveLength(2)

    // Grow stroke — many points, version bumps each time (like Excalidraw freehand)
    for (let v = 2; v <= 30; v++) {
      const points = Array.from({ length: v + 1 }, (_, i) => [i, i * 0.5] as [number, number])
      stroke = freehand('pen1', points, v)
      res = getDeltaOperationsForElements(lastKnown, [stroke])
      lastKnown = res.lastKnownElements
      applyElementOperations(yElements, res.operations, origin)
    }

    const remote = yjsToExcalidraw(yElements)
    expect(remote).toHaveLength(1)
    expect(remote[0]!.type).toBe('freedraw')
    expect((remote[0] as unknown as { points: unknown[] }).points).toHaveLength(31)
    expect(remote[0]!.version).toBe(30)
  })

  it('does not re-emit when lastKnown versions match (prevents peer echo)', () => {
    const stroke = freehand(
      'pen1',
      [
        [0, 0],
        [1, 1],
        [2, 2],
      ],
      5,
    )
    const lastKnown = [{ id: 'pen1', version: 5, pos: 'a0' }]
    const res = getDeltaOperationsForElements(lastKnown, [stroke])
    expect(res.operations).toHaveLength(0)
  })

  it('second client applying updates sees full freehand path', () => {
    const docA = new Y.Doc()
    const docB = new Y.Doc()
    docA.on('update', (u) => Y.applyUpdate(docB, u))
    docB.on('update', (u) => Y.applyUpdate(docA, u))

    const yA = docA.getArray<YElementEntry>('elements')
    const yB = docB.getArray<YElementEntry>('elements')
    let lastKnown: { id: string; version: number; pos: string }[] = []
    const origin = {}

    for (let v = 1; v <= 20; v++) {
      const points = Array.from({ length: v * 3 }, (_, i) => [i, i] as [number, number])
      const stroke = freehand('pen1', points, v)
      const res = getDeltaOperationsForElements(lastKnown, [stroke])
      lastKnown = res.lastKnownElements
      applyElementOperations(yA, res.operations, origin)
    }

    const onB = yjsToExcalidraw(yB)
    expect((onB[0] as unknown as { points: unknown[] }).points).toHaveLength(60)
  })
})
