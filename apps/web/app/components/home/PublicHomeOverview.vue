<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import type { Component } from 'vue'
import type { components } from '@haoblog/api-client'
import type { GardenGraph } from '../../utils/publicGarden'
import { useDataSaver } from '../../composables/useDataSaver'
import { useMotionPreference } from '../../composables/useMotionPreference'
import { useTheme } from '../../utils/theme'

type Site = components['schemas']['SiteResponse']
type ArticleList = components['schemas']['ArticleListResponse']
type SceneState = 'waiting' | 'loading' | 'three' | 'fallback' | 'flag-off' | 'constrained' | 'empty'

const props = defineProps<{
  site: Site
  recent: ArticleList
  siteError?: boolean
  recentError?: boolean
}>()

const { enabled: dataSaver } = useDataSaver()
const { reduced } = useMotionPreference()
const { theme, setTheme } = useTheme()
const starMapSection = ref<HTMLElement | null>(null)
const sceneComponent = shallowRef<Component | null>(null)
const sceneGraph = ref<GardenGraph | null>(null)
const sceneState = ref<SceneState>('waiting')
let observer: IntersectionObserver | null = null
let active = true
let titleClickCount = 0
let titleClickTimer: ReturnType<typeof setTimeout> | undefined

const sceneStatus = computed(() => {
  switch (sceneState.value) {
    case 'loading': return '星图接入中；Three.js 与公开图谱只在此刻请求。'
    case 'three': return '桌面低功耗 3D 已接入；下方键盘清单始终可用。'
    case 'fallback': return '动态星图不可用，当前保留静态关系图与文字版入口。'
    case 'flag-off': return '3D 信号未开启，当前保留静态关系图与文字版入口。'
    case 'constrained': return '当前设备走静态降级路径，不加载 3D。'
    case 'empty': return '暂无公开图谱节点，当前保留静态关系图与文字版入口。'
    default: return '进入视口后才接入 3D；在此之前只显示 SSR 静态说明。'
  }
})

onMounted(() => {
  if (!props.site.threeDEnabled) {
    sceneState.value = 'flag-off'
    return
  }
  if (dataSaver.value || reduced.value || window.innerWidth < 768 || window.matchMedia('(pointer: coarse)').matches) {
    sceneState.value = 'constrained'
    return
  }
  if (!canUseWebGL()) {
    sceneState.value = 'fallback'
    return
  }
  if (!('IntersectionObserver' in window) || !starMapSection.value) {
    sceneState.value = 'fallback'
    return
  }
  observer = new IntersectionObserver(entries => {
    if (entries.some(entry => entry.isIntersecting)) void loadThreeScene()
  }, { threshold: .15 })
  observer.observe(starMapSection.value)
})

onBeforeUnmount(() => {
  active = false
  observer?.disconnect()
  observer = null
  if (titleClickTimer) clearTimeout(titleClickTimer)
})

function canUseWebGL() {
  try {
    const canvas = document.createElement('canvas')
    return Boolean(canvas.getContext('webgl'))
  } catch {
    return false
  }
}

async function loadThreeScene() {
  if (sceneState.value !== 'waiting' || !active) return
  sceneState.value = 'loading'
  try {
    const graph = await $fetch<GardenGraph>('/api/v1/public/garden')
    if (!active) return
    if (!graph.nodes.length) {
      sceneState.value = 'empty'
      return
    }
    const module = await import('./HomeThreeScene.client.vue')
    if (!active) return
    sceneGraph.value = graph
    sceneComponent.value = module.default
    sceneState.value = 'three'
  } catch {
    if (active) sceneState.value = 'fallback'
  }
}

function handleTitleClick() {
  titleClickCount += 1
  if (titleClickTimer) clearTimeout(titleClickTimer)
  titleClickTimer = setTimeout(() => { titleClickCount = 0 }, 1200)
  if (titleClickCount < 5) return
  titleClickCount = 0
  setTheme(theme.value === 'blueprint' ? 'night' : 'blueprint')
}

function handleSceneFailure() {
  sceneComponent.value = null
  sceneGraph.value = null
  sceneState.value = 'fallback'
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date(value)).replaceAll('/', '.')
}
</script>

<template>
  <div class="home-overview observation-scene" :class="{ 'is-data-saver': dataSaver }">
    <section id="calibration-scene" class="home-act home-calibration-scene" aria-labelledby="site-title">
      <div class="home-calibration">
        <div>
          <p class="instrument-label">FIRST FRAME / 夜空校准 / CALIBRATION</p>
          <p class="home-readout"><span class="status-light" aria-hidden="true" /> PUBLIC SSR / SIGNAL LOCKED</p>
        </div>
        <p class="home-coordinate">N 31°14′ · E 121°28′<br>UTC+08 / NIGHT SHIFT</p>
      </div>
      <h1 id="site-title"><button class="home-title-button" type="button" aria-label="站点标题，连续点击五次切换蓝图主题" @click="handleTitleClick">{{ site.title }}</button></h1>
      <p class="home-identity">{{ site.authorName }} / DEVELOPER OBSERVATORY</p>
      <p class="signal-copy">{{ site.description }}</p>
      <p v-if="siteError" class="signal-note" role="alert">站点信号暂时不可用，当前显示基础信息。</p>
      <a class="continue-reading" href="#starmap-title"><span aria-hidden="true">↓</span> 继续校准 / CONTINUE READING</a>
      <span class="home-scanline" aria-hidden="true" />
    </section>

    <section ref="starMapSection" id="starmap-scene" class="home-act home-starmap-scene" aria-labelledby="starmap-title">
      <div class="home-act-heading">
        <div>
          <p class="instrument-label">SECOND FRAME / KNOWLEDGE SIGNAL</p>
          <h2 id="starmap-title">星图接入</h2>
        </div>
        <p class="home-frame-readout">HOME SCENE / LOW POWER</p>
      </div>
      <p class="home-starmap-copy">公开文章、标签、分类和工具在同一张关系图上留下轨道。首屏只输出这段说明；桌面设备滚动到本幕后，才按条件接入 Three.js。</p>
      <div class="home-static-starmap" role="img" aria-label="静态知识星图降级：文章、标签、分类和工具通过关系线连接">
        <span class="home-orbit home-orbit--article">ARTICLE / 文章</span>
        <span class="home-orbit home-orbit--tag">TAG / 标签</span>
        <span class="home-orbit home-orbit--category">CATEGORY / 分类</span>
        <span class="home-orbit home-orbit--tool">TOOL / 工具</span>
        <i class="home-orbit-core" aria-hidden="true" />
      </div>
      <p class="home-starmap-status" role="status" aria-live="polite">{{ sceneStatus }}</p>
      <p class="home-starmap-fallback">可访问降级：<a href="/garden">打开知识星图文字时间线</a>，或直接前往<a href="/articles">近期文章</a>和<a href="/tools">公开工具</a>。</p>
      <component :is="sceneComponent" v-if="sceneComponent && sceneGraph" :graph="sceneGraph" @failed="handleSceneFailure" />
      <a class="continue-reading" href="#recent-title"><span aria-hidden="true">↓</span> 进入近期日志 / NEXT FRAME</a>
    </section>

    <section id="recent-log" class="home-act home-recent-scene" aria-labelledby="recent-title">
      <div class="recent-heading">
        <div>
          <p class="instrument-label">THIRD FRAME / RECENT LOG</p>
          <h2 id="recent-title">近期观测日志</h2>
        </div>
        <span class="log-count">{{ String(recent.total).padStart(2, '0') }} ENTRIES</span>
      </div>
      <p v-if="recentError" class="signal-note" role="alert">近期文章信号暂时不可用，请稍后重试。</p>
      <p v-else-if="recent.items.length === 0" class="signal-note">当前没有已锁定的公开文章。</p>
      <ol v-else class="recent-list">
        <li v-for="(article, index) in recent.items" :key="article.id">
          <span class="recent-index">{{ String(index + 1).padStart(2, '0') }}</span>
          <time :datetime="article.publishedAt">{{ formatDate(article.publishedAt) }}</time>
          <NuxtLink :to="`/articles/${article.slug}`">{{ article.title }}<small>{{ article.excerpt || '信号已锁定，正文可读。' }}</small></NuxtLink>
        </li>
      </ol>
      <NuxtLink class="recent-more" to="/articles">查看全部文章 →</NuxtLink>
    </section>
  </div>
</template>
