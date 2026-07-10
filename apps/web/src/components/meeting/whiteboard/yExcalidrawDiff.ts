/**
 * Ported from context/y-excalidraw/src/diff.ts
 * Version-based element deltas against a Y.Array of Y.Map<{ pos, el }>.
 */
import type { ExcalidrawElement, OrderedExcalidrawElement } from '@excalidraw/excalidraw/element/types'
import type { BinaryFileData, BinaryFiles } from '@excalidraw/excalidraw/types'
// Vendored with Excalidraw — not the npm `fractional-indexing` package.
import { generateKeyBetween, generateNKeysBetween } from '@excalidraw/fractional-indexing'
import * as Y from 'yjs'
import { moveArrayItem, type YElementEntry } from './yExcalidrawHelpers'

export type UpdateOperation = { type: 'update'; id: string; index: number; element: ExcalidrawElement }
export type AppendOperation = { type: 'append'; id: string; pos: string; element: ExcalidrawElement }
export type DeleteOperation = { type: 'delete'; id: string; index: number }
export type MoveOperation = { type: 'move'; id: string; fromIndex: number; toIndex: number; pos: string }
export type BulkAppendOperation = {
  type: 'bulkAppend'
  data: { id: string; pos: string; element: ExcalidrawElement }[]
}
export type BulkDeleteOperation = {
  type: 'bulkDelete'
  id: string
  index: number
  data: { id: string; index: number }[]
}

export type Operation =
  | UpdateOperation
  | AppendOperation
  | DeleteOperation
  | MoveOperation
  | BulkAppendOperation
  | BulkDeleteOperation

type OperationTracker = {
  elementIds: string[]
  idMap: { [id: string]: { id: string; version: number; pos: string; index: number } }
}

export type LastKnownOrderedElement = { id: string; version: number; pos: string }

export function getDeltaOperationsForElements(
  lastKnownElements: LastKnownOrderedElement[],
  newElements: readonly OrderedExcalidrawElement[],
  bulkify = true,
): { operations: Operation[]; lastKnownElements: LastKnownOrderedElement[] } {
  const updateOperations: UpdateOperation[] = []
  const appendOperations: AppendOperation[] = []
  const deleteOperations: DeleteOperation[] = []
  const moveOperations: MoveOperation[] = []

  const opsTracker: OperationTracker = {
    elementIds: lastKnownElements.map((x) => x.id),
    idMap: lastKnownElements.reduce((map: OperationTracker['idMap'], data, index) => {
      map[data.id] = { id: data.id, version: data.version, pos: data.pos, index }
      return map
    }, {}),
  }

  const updateIdIndexLookup = () => {
    opsTracker.idMap = opsTracker.elementIds.reduce((map: OperationTracker['idMap'], id, index) => {
      map[id] = { ...opsTracker.idMap[id]!, index }
      return map
    }, {})
  }

  for (const newElement of newElements) {
    let oldIndex: number | null = null
    let oldElement: LastKnownOrderedElement | null = null
    if (opsTracker.idMap[newElement.id]) {
      const { index, ...rest } = opsTracker.idMap[newElement.id]!
      oldIndex = index
      oldElement = rest
    }
    if (!oldElement) {
      const op = {
        id: newElement.id,
        version: newElement.version,
        pos: !bulkify
          ? generateKeyBetween(opsTracker.idMap[opsTracker.elementIds[opsTracker.elementIds.length - 1]!]?.pos, null)
          : '',
        index: opsTracker.elementIds.length,
      }
      opsTracker.elementIds.push(op.id)
      opsTracker.idMap[op.id] = op
      appendOperations.push({ type: 'append', id: newElement.id, pos: op.pos, element: newElement })
    } else if (newElement.version !== oldElement.version) {
      // Freehand bumps version on every point — this is what streams the full path.
      const op = {
        id: newElement.id,
        version: newElement.version,
        pos: oldElement.pos,
        index: oldIndex!,
      }
      opsTracker.idMap[newElement.id] = op
      updateOperations.push({ type: 'update', id: op.id, index: op.index, element: newElement })
    }
  }

  const newElementIds = new Set(newElements.map((x) => x.id))
  const newOpsTrackerElementIds: string[] = []
  let runningIndex = 0
  for (let i = 0; i < opsTracker.elementIds.length; i++) {
    const id = opsTracker.elementIds[i]!
    if (!newElementIds.has(id)) {
      deleteOperations.push({ type: 'delete', index: runningIndex, id })
    } else {
      newOpsTrackerElementIds.push(id)
      runningIndex += 1
    }
  }
  if (deleteOperations.length > 0) {
    opsTracker.elementIds = newOpsTrackerElementIds
    updateIdIndexLookup()
  }

  for (let toIndex = 0; toIndex < newElements.length; toIndex++) {
    const id = newElements[toIndex]!.id
    const { index: fromIndex } = opsTracker.idMap[id]!

    if (toIndex !== fromIndex) {
      let leftSortIndex: string | null = null
      let rightSortIndex: string | null = null
      if (fromIndex >= 0 && fromIndex < toIndex) {
        leftSortIndex = opsTracker.idMap[opsTracker.elementIds[toIndex]!]?.pos || null
        rightSortIndex = opsTracker.idMap[opsTracker.elementIds[toIndex + 1]!]?.pos || null
      } else {
        leftSortIndex = opsTracker.idMap[opsTracker.elementIds[toIndex - 1]!]?.pos || null
        rightSortIndex = opsTracker.idMap[opsTracker.elementIds[toIndex]!]?.pos || null
      }

      const newSortIndex = generateKeyBetween(leftSortIndex, rightSortIndex)
      opsTracker.elementIds = moveArrayItem(opsTracker.elementIds, fromIndex, toIndex, true)
      opsTracker.idMap[id]!.pos = newSortIndex
      updateIdIndexLookup()
      moveOperations.push({ type: 'move', id, fromIndex, toIndex, pos: newSortIndex })
    }
  }

  const bulkAppendOperations: BulkAppendOperation[] = []
  const bulkDeleteOperations: BulkDeleteOperation[] = []
  if (bulkify) {
    if (appendOperations.length > 0) {
      const sortIndexes = generateNKeysBetween(
        lastKnownElements[lastKnownElements.length - 1]?.pos,
        null,
        appendOperations.length,
      )
      for (const [i, op] of appendOperations.entries()) {
        opsTracker.idMap[op.id]!.pos = sortIndexes[i]!
      }
      bulkAppendOperations.push({
        type: 'bulkAppend',
        data: appendOperations.map((op, index) => ({
          id: op.id,
          pos: sortIndexes[index]!,
          element: op.element,
        })),
      })
    }

    let lastIndex: number | null = null
    for (const op of deleteOperations) {
      if (lastIndex === null || op.index > lastIndex) {
        bulkDeleteOperations.push({
          type: 'bulkDelete',
          index: op.index,
          id: op.id,
          data: [{ id: op.id, index: op.index }],
        })
        lastIndex = op.index
      } else {
        bulkDeleteOperations[bulkDeleteOperations.length - 1]!.data.push({ id: op.id, index: op.index })
      }
    }
  }

  const operations: Operation[] = !bulkify
    ? [...updateOperations, ...appendOperations, ...deleteOperations, ...moveOperations]
    : [...updateOperations, ...bulkAppendOperations, ...bulkDeleteOperations, ...moveOperations]

  const updatedLastKnownElements = opsTracker.elementIds.map((x) => {
    const { index: _index, ...rest } = opsTracker.idMap[x]!
    return rest
  })

  return { operations, lastKnownElements: updatedLastKnownElements }
}

export type AssetAppendOperation = { type: 'append'; id: string; asset: BinaryFileData }
export type AssetDeleteOperation = { type: 'delete'; id: string }
export type AssetOperation = AssetAppendOperation | AssetDeleteOperation

export function getDeltaOperationsForAssets(
  lastKnownFileIds: Set<string>,
  files: BinaryFiles,
): { operations: AssetOperation[]; lastKnownFileIds: Set<string> } {
  const operations: AssetOperation[] = []
  const newFields = new Set<string>()

  for (const fileId of Object.keys(files)) {
    newFields.add(fileId)
    if (!lastKnownFileIds.has(fileId)) {
      operations.push({ type: 'append', id: fileId, asset: files[fileId]! })
    }
  }

  for (const fileId of lastKnownFileIds) {
    if (!(fileId in files)) {
      operations.push({ type: 'delete', id: fileId })
    }
  }

  return { operations, lastKnownFileIds: newFields }
}

/**
 * Apply element ops. Origin must be the binding instance so remote observeDeep skips self.
 * Spread `{...element}` so later Excalidraw in-place freehand mutations don't mutate Yjs state.
 */
export function applyElementOperations(
  yElements: Y.Array<YElementEntry>,
  operations: Operation[],
  origin: unknown,
): void {
  yElements.doc!.transact(() => {
    const idYjsIndexMap: { [key: string]: number } = {}
    const updateYjsIndexMap = () => {
      for (let i = 0; i < yElements.length; i++) {
        const item = yElements.get(i).get('el') as ExcalidrawElement
        idYjsIndexMap[item.id] = i
      }
    }
    updateYjsIndexMap()

    for (const op of operations) {
      switch (op.type) {
        case 'update': {
          yElements.get(idYjsIndexMap[op.id]!).set('el', { ...op.element })
          break
        }
        case 'append': {
          idYjsIndexMap[op.id] = yElements.length
          yElements.push([new Y.Map(Object.entries({ pos: op.pos, el: { ...op.element } })) as YElementEntry])
          break
        }
        case 'bulkAppend': {
          for (let i = 0; i < op.data.length; i++) {
            idYjsIndexMap[op.data[i]!.id] = yElements.length + i
          }
          yElements.push(
            op.data.map((x) => new Y.Map(Object.entries({ pos: x.pos, el: { ...x.element } })) as YElementEntry),
          )
          break
        }
        case 'delete': {
          yElements.delete(idYjsIndexMap[op.id]!, 1)
          updateYjsIndexMap()
          break
        }
        case 'bulkDelete': {
          yElements.delete(idYjsIndexMap[op.id]!, op.data.length)
          updateYjsIndexMap()
          break
        }
        case 'move': {
          yElements.get(idYjsIndexMap[op.id]!).set('pos', op.pos)
          break
        }
      }
    }
  }, origin)
}

export function applyAssetOperations(
  yAssets: Y.Map<BinaryFileData>,
  operations: AssetOperation[],
  origin?: unknown,
): void {
  yAssets.doc!.transact(() => {
    for (const op of operations) {
      switch (op.type) {
        case 'append':
          yAssets.set(op.id, op.asset)
          break
        case 'delete':
          yAssets.delete(op.id)
          break
      }
    }
  }, origin)
}
