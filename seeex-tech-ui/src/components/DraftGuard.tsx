import { Modal } from '@arco-design/web-react'
import { useBeforeUnload, useBlocker } from 'react-router-dom'
import { createContext, useCallback, useContext, useEffect, useId, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
interface Entry {
  dirty: boolean
  busy: boolean
}
interface Registry {
  set: (id: string, value: Entry) => void
  remove: (id: string) => void
}
const Context = createContext<Registry | null>(null)
/** 全应用只注册一个路由阻断器，多个抽屉共同报告未保存状态。 */
export function DraftGuardProvider({ children }: { children: ReactNode }) {
  const [entries, setEntries] = useState<Record<string, Entry>>({})
  const set = useCallback(
    (id: string, value: Entry) =>
      setEntries((old) =>
        old[id]?.dirty === value.dirty && old[id]?.busy === value.busy ? old : { ...old, [id]: value },
      ),
    [],
  )
  const remove = useCallback(
    (id: string) =>
      setEntries((old) => {
        if (!(id in old)) return old
        const next = { ...old }
        delete next[id]
        return next
      }),
    [],
  )
  const registry = useMemo(() => ({ set, remove }), [set, remove])
  const dirty = Object.values(entries).some((row) => row.dirty),
    busy = Object.values(entries).some((row) => row.busy)
  useBeforeUnload(
    useCallback(
      (event: BeforeUnloadEvent) => {
        if (dirty || busy) {
          event.preventDefault()
          event.returnValue = ''
        }
      },
      [dirty, busy],
    ),
  )
  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) =>
      (dirty || busy) &&
      (currentLocation.pathname !== nextLocation.pathname || currentLocation.search !== nextLocation.search),
  )
  const shown = useRef(false)
  useEffect(() => {
    if (blocker.state !== 'blocked') {
      shown.current = false
      return
    }
    if (shown.current) return
    shown.current = true
    Modal.confirm({
      title: busy ? '操作进行中，请等待结果' : '离开并放弃未保存内容？',
      content: busy ? '操作结果尚未返回，请先完成当前操作。' : '尚未保存的内容将丢失。',
      onOk: () => {
        if (busy) blocker.reset()
        else blocker.proceed()
      },
      onCancel: () => blocker.reset(),
    })
  }, [blocker, busy])
  return <Context.Provider value={registry}>{children}</Context.Provider>
}
/** 草稿只在内存中，刷新和站内跳转均须明确放弃。 */
export function useDraftGuard(dirty: boolean, busy = false) {
  const registry = useContext(Context),
    id = useId()
  useEffect(() => {
    registry?.set(id, { dirty, busy })
    return () => registry?.remove(id)
  }, [id, registry, dirty, busy])
}
