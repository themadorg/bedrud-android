import type { ExcalidrawImperativeAPI } from '@excalidraw/excalidraw/types'
import type { ComponentType, ReactNode, RefObject } from 'react'
import {
  copyWhiteboardAsPng,
  copyWhiteboardAsSvg,
  pasteToWhiteboard,
  selectAllWhiteboardElements,
  toggleWhiteboardGrid,
  toggleWhiteboardSnap,
  toggleWhiteboardStats,
  toggleWhiteboardViewMode,
  toggleWhiteboardZenMode,
} from './whiteboardMenuActions'

/** Same MainMenu export as Excalidraw (from excalidrawLazy) — keeps one React/context graph. */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
type MainMenuComponent = ComponentType<{ children?: ReactNode }> & Record<string, any>

interface WhiteboardMainMenuProps {
  apiRef: RefObject<ExcalidrawImperativeAPI | null>
  MainMenu: MainMenuComponent
}

function runWithApi(apiRef: RefObject<ExcalidrawImperativeAPI | null>, fn: (api: ExcalidrawImperativeAPI) => void) {
  const api = apiRef.current
  if (!api) return
  fn(api)
}

function runAsyncWithApi(
  apiRef: RefObject<ExcalidrawImperativeAPI | null>,
  fn: (api: ExcalidrawImperativeAPI) => Promise<void>,
) {
  const api = apiRef.current
  if (!api) return
  void fn(api).catch(() => {})
}

export function WhiteboardMainMenu({ apiRef, MainMenu }: WhiteboardMainMenuProps) {
  return (
    <MainMenu>
      <MainMenu.Item onSelect={() => pasteToWhiteboard()} shortcut="Ctrl+V">
        Paste
      </MainMenu.Item>
      <MainMenu.Item onSelect={() => runAsyncWithApi(apiRef, copyWhiteboardAsPng)} shortcut="Shift+Alt+C">
        Copy to clipboard as PNG
      </MainMenu.Item>
      <MainMenu.Item onSelect={() => runAsyncWithApi(apiRef, copyWhiteboardAsSvg)}>
        Copy to clipboard as SVG
      </MainMenu.Item>
      <MainMenu.Separator />
      <MainMenu.Item onSelect={() => runWithApi(apiRef, selectAllWhiteboardElements)} shortcut="Ctrl+A">
        Select all
      </MainMenu.Item>
      <MainMenu.Separator />
      <MainMenu.Item onSelect={() => runWithApi(apiRef, toggleWhiteboardGrid)} shortcut="Ctrl+'">
        Toggle grid
      </MainMenu.Item>
      <MainMenu.Item onSelect={() => runWithApi(apiRef, toggleWhiteboardSnap)} shortcut="Alt+S">
        Snap to objects
      </MainMenu.Item>
      <MainMenu.Item onSelect={() => runWithApi(apiRef, toggleWhiteboardZenMode)} shortcut="Alt+Z">
        Zen mode
      </MainMenu.Item>
      <MainMenu.Item onSelect={() => runWithApi(apiRef, toggleWhiteboardViewMode)} shortcut="Alt+V">
        View mode
      </MainMenu.Item>
      <MainMenu.Item onSelect={() => runWithApi(apiRef, toggleWhiteboardStats)} shortcut="Alt+/">
        {'Canvas & Shape properties'}
      </MainMenu.Item>
      <MainMenu.Separator />
      <MainMenu.DefaultItems.SearchMenu />
      <MainMenu.DefaultItems.Help />
      <MainMenu.DefaultItems.ClearCanvas />
      <MainMenu.Separator />
      <MainMenu.DefaultItems.Socials />
      <MainMenu.DefaultItems.ChangeCanvasBackground />
    </MainMenu>
  )
}
