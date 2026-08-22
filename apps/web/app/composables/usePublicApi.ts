export function usePublicApi<T>(path: string, options: any = {}) {
  const config = useRuntimeConfig()
  const baseURL = import.meta.server ? config.apiBaseUrl : config.public.apiBase
  const headers = import.meta.server ? useRequestHeaders(['save-data']) : undefined
  return useFetch<T>(path, { baseURL, headers, ...options })
}
