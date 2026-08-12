export function useMotionPreference() {
  const reduced = ref(false)
  let media: MediaQueryList | undefined
  const update = (event?: MediaQueryListEvent) => { reduced.value = event?.matches ?? media?.matches ?? false }
  onMounted(() => {
    media = window.matchMedia('(prefers-reduced-motion: reduce)')
    update()
    media.addEventListener('change', update)
  })
  onBeforeUnmount(() => media?.removeEventListener('change', update))
  return { reduced: readonly(reduced) }
}
