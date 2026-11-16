import { useEffect } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { consumeSessionError, notifyApiError, SESSION_EXPIRED_EVENT } from '../lib/apiErrors'

export function GlobalApiFeedback() {
  const client = useQueryClient()
  useEffect(() => {
    const notice = consumeSessionError()
    if (notice) notifyApiError(notice)
    const clearSessionQueries = () => client.clear()
    window.addEventListener(SESSION_EXPIRED_EVENT, clearSessionQueries)
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, clearSessionQueries)
  }, [client])
  return null
}
