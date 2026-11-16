import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ConfigProvider } from '@arco-design/web-react'
import { RouterProvider } from 'react-router-dom'
import zhCN from '@arco-design/web-react/es/locale/zh-CN'
import { router } from '../routes/router'
import { GlobalApiFeedback } from '../components/GlobalApiFeedback'
import { normalizeApiError, notifyApiError } from '../lib/apiErrors'

const queryClient = new QueryClient({
  queryCache: new QueryCache({ onError: (error) => { notifyApiError(error) } }),
  mutationCache: new MutationCache({ onError: (error) => { notifyApiError(error) } }),
  defaultOptions: {
    queries: {
      retry: (count, error) => {
        const failure = normalizeApiError(error)
        if (failure.suppressNotification || (failure.status !== undefined && failure.status < 500)) return false
        return count < 1
      },
    },
    mutations: { retry: false },
  },
})

export function AppProviders() { return <QueryClientProvider client={queryClient}><ConfigProvider locale={zhCN}><GlobalApiFeedback /><RouterProvider router={router} /></ConfigProvider></QueryClientProvider> }

