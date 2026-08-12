export function usePublicApi<T>(path: string, options: any = {}) {
  const config = useRuntimeConfig()
  const baseURL = import.meta.server ? config.apiBaseUrl : config.public.apiBase
  return useFetch<T>(path, { baseURL, ...options })
}
