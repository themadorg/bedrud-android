import type { OrderedExcalidrawElement } from '@excalidraw/excalidraw/element/types'
import type { AppState } from '@excalidraw/excalidraw/types'
import { describe, expect, it } from 'vitest'
import {
  ensureInProgressDrawingInScene,
  isPointBasedElement,
  pickNewerElement,
  pointCount,
  shouldKeepLocalElement,
} from './excalidrawFreehandSync'

function freehand(id: string, points: [number, number][], version: number, versionNonce = 1): OrderedExcalidrawElement {
  return {
    id,
    type: 'freedraw',
    x: 0,
    y: 0,
    width: 10,
    height: 10,
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
    versionNonce,
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

function rect(id: string, version: number): OrderedExcalidrawElement {
  return {
    id,
    type: 'rectangle',
    x: 0,
    y: 0,
    width: 10,
    height: 10,
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
    versionNonce: 1,
    isDeleted: false,
    boundElements: null,
    updated: 1,
    link: null,
    locked: false,
  } as unknown as OrderedExcalidrawElement
}

describe('excalidrawFreehandSync', () => {
  it('detects point-based elements', () => {
    expect(isPointBasedElement(freehand('a', [[0, 0]], 1))).toBe(true)
    expect(isPointBasedElement(rect('b', 1))).toBe(false)
  })

  it('pickNewerElement prefers more freehand points over version', () => {
    const short = freehand(
      'stroke',
      [
        [0, 0],
        [1, 1],
      ],
      5,
    )
    const long = freehand(
      'stroke',
      [
        [0, 0],
        [1, 1],
        [2, 2],
        [3, 3],
      ],
      4,
    )

    expect(pointCount(pickNewerElement(long, short))).toBe(4)
    expect(pointCount(pickNewerElement(short, long))).toBe(4)
  })

  it('pickNewerElement uses version when point counts match', () => {
    const older = freehand(
      'stroke',
      [
        [0, 0],
        [1, 1],
      ],
      2,
    )
    const newer = freehand(
      'stroke',
      [
        [0, 0],
        [1, 1],
      ],
      5,
    )
    expect(pickNewerElement(older, newer).version).toBe(5)
    expect(pickNewerElement(newer, older).version).toBe(5)
  })

  it('shouldKeepLocalElement keeps newElement freehand', () => {
    const local = freehand(
      'stroke',
      [
        [0, 0],
        [1, 1],
        [2, 2],
      ],
      3,
    )
    const remote = freehand('stroke', [[0, 0]], 99)
    const appState = { newElement: local } as unknown as AppState

    expect(shouldKeepLocalElement(appState, local, remote)).toBe(true)
  })

  it('shouldKeepLocalElement keeps longer freehand path', () => {
    const local = freehand(
      'stroke',
      [
        [0, 0],
        [1, 1],
        [2, 2],
        [3, 3],
      ],
      2,
    )
    const remote = freehand(
      'stroke',
      [
        [0, 0],
        [1, 1],
      ],
      10,
    )
    const appState = {} as AppState

    expect(shouldKeepLocalElement(appState, local, remote)).toBe(true)
    expect(shouldKeepLocalElement(appState, remote, local)).toBe(false)
  })

  it('ensureInProgressDrawingInScene prefers live newElement points', () => {
    const live = freehand(
      'stroke',
      [
        [0, 0],
        [1, 1],
        [2, 2],
        [3, 3],
        [4, 4],
      ],
      5,
    )
    const stale = freehand('stroke', [[0, 0]], 1)
    const other = rect('box', 1)
    const appState = { newElement: live } as unknown as AppState

    const merged = ensureInProgressDrawingInScene([stale, other], appState)
    const stroke = merged.find((el) => el.id === 'stroke')
    expect(pointCount(stroke!)).toBe(5)
    expect(stroke).toBe(live)
  })

  it('ensureInProgressDrawingInScene inserts missing drawing element', () => {
    const live = freehand(
      'stroke',
      [
        [0, 0],
        [1, 1],
      ],
      1,
    )
    const appState = { newElement: live } as unknown as AppState
    const merged = ensureInProgressDrawingInScene([rect('box', 1)], appState)
    expect(merged.some((el) => el.id === 'stroke')).toBe(true)
  })

  it('pickNewerElement never shrinks a freehand stroke', () => {
    const short = freehand('s', [[0, 0]], 100)
    const long = freehand(
      's',
      [
        [0, 0],
        [1, 1],
        [2, 2],
        [3, 3],
        [4, 4],
      ],
      1,
    )
    // Higher version but fewer points must lose.
    expect(pointCount(pickNewerElement(long, short))).toBe(5)
  })
})
