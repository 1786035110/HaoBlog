export function usePublicApi<T>(path: string, options: any = {}) {
  const config = useRuntimeConfig()
  const baseURL = import.meta.server ? config.apiBaseUrl : config.public.apiBase
  const headers = import.meta.server ? useRequestHeaders(['save-data', 'x-request-id']) : undefined
  return useFetch<T>(path, { baseURL, headers, timeout: 5000, retry: 0, ...options })
}
