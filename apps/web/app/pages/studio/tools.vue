<template>
  <section class="tools-scene" aria-labelledby="tools-title">
    <div class="page-heading">
      <div><p class="instrument-label">STUDIO / TOOLBOX CONTROL</p><h1 id="tools-title">工具控制台</h1></div>
      <button class="instrument-button" type="button" @click="newTool">新建工具</button>
    </div>

    <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
    <div class="control-grid">
      <aside class="category-panel" aria-labelledby="category-title">
        <div class="panel-heading"><h2 id="category-title">分类舱</h2><button class="plain-button" type="button" @click="newCategory">新建分类</button></div>
        <form class="console-form" @submit.prevent="saveCategory">
          <label>名称<input v-model="categoryDraft.name" maxlength="120" required></label>
          <label>Slug<input v-model="categoryDraft.slug" maxlength="160" required></label>
          <label>描述<textarea v-model="categoryDraft.description" maxlength="600" rows="2" /></label>
          <label>排序<input v-model.number="categoryDraft.sortOrder" type="number"></label>
          <p v-if="categoryError" class="form-error" role="alert">{{ categoryError }}</p>
          <div class="button-row"><button class="instrument-button" type="submit" :disabled="savingCategory">{{ savingCategory ? 'WRITING…' : '写入分类' }}</button><button v-if="categoryDraft.id" class="plain-button" type="button" @click="removeCategory">删除</button></div>
        </form>
        <ul class="category-list">
          <li v-for="category in categories" :key="category.id" :data-active="category.id === categoryDraft.id"><button type="button" @click="editCategory(category)"><span>{{ category.name }}</span><small>{{ category.sortOrder }} / v{{ category.version }}</small></button></li>
        </ul>
      </aside>

      <div class="tool-panel">
        <form class="filter-console" role="search" @submit.prevent="applyFilters">
          <label>分类<select v-model="filters.categoryId"><option value="">全部分类</option><option v-for="category in categories" :key="category.id" :value="category.id">{{ category.name }}</option></select></label>
          <label>类型<select v-model="filters.type"><option value="">全部类型</option><option v-for="type in toolTypes" :key="type" :value="type">{{ type }}</option></select></label>
          <label>状态<select v-model="filters.status"><option value="">全部状态</option><option value="ACTIVE">启用</option><option value="INACTIVE">停用</option></select></label>
          <label class="keyword-field">关键词<input v-model="filters.keyword" maxlength="240" placeholder="标题 / slug / 描述"></label>
          <button class="instrument-button" type="submit" :disabled="loading">筛选</button>
        </form>
        <p v-if="loading" class="signal-note" role="status">正在扫描工具信号…</p>
        <p v-else-if="!tools.length" class="empty-signal">当前观测范围没有工具。</p>
        <ol v-else class="tool-log">
          <li v-for="(tool, index) in tools" :key="tool.id" :data-active="tool.id === toolDraft.id">
            <button class="tool-row" type="button" @click="editTool(tool)"><span class="tool-index">{{ String(index + 1 + page * pageSize).padStart(2, '0') }}</span><span class="tool-signal" :data-status="tool.status" aria-hidden="true" /><span class="tool-copy"><strong>{{ tool.title }}</strong><small>{{ tool.slug }} · {{ tool.type }} · v{{ tool.version }}</small></span><span class="tool-state">{{ tool.status === 'ACTIVE' ? 'ACTIVE' : 'INACTIVE' }}</span></button>
          </li>
        </ol>
        <div v-if="total > pageSize" class="pagination"><button class="plain-button" type="button" :disabled="page === 0 || loading" @click="changePage(page - 1)">上一页</button><span>PAGE {{ page + 1 }} / {{ Math.ceil(total / pageSize) }}</span><button class="plain-button" type="button" :disabled="(page + 1) * pageSize >= total || loading" @click="changePage(page + 1)">下一页</button></div>

        <form class="tool-editor" @submit.prevent="saveTool">
          <div class="panel-heading"><h2>{{ toolDraft.id ? '校准工具' : '新建工具' }}</h2><button v-if="toolDraft.id" class="plain-button" type="button" @click="newTool">清空</button></div>
          <div class="form-columns">
            <label>分类<select v-model="toolDraft.categoryId"><option value="">请选择</option><option v-for="category in categories" :key="category.id" :value="category.id">{{ category.name }}</option></select><small v-if="toolErrors.categoryId" class="field-error">{{ toolErrors.categoryId }}</small></label>
            <label>类型<select v-model="toolDraft.type"><option v-for="type in toolTypes" :key="type" :value="type">{{ type }}</option></select></label>
            <label>状态<select v-model="toolDraft.status"><option value="ACTIVE">ACTIVE / 启用</option><option value="INACTIVE">INACTIVE / 停用</option></select></label>
            <label>排序<input v-model.number="toolDraft.sortOrder" type="number"></label>
            <label>标题<input v-model="toolDraft.title" maxlength="160" required><small v-if="toolErrors.title" class="field-error">{{ toolErrors.title }}</small></label>
            <label>Slug<input v-model="toolDraft.slug" maxlength="160" required><small v-if="toolErrors.slug" class="field-error">{{ toolErrors.slug }}</small></label>
          </div>
          <label>描述<textarea v-model="toolDraft.description" maxlength="600" rows="2" /></label>
          <div class="form-columns"><label>URL（仅 https）<input v-model="toolDraft.url" type="url" inputmode="url" :disabled="toolDraft.type === 'EMBEDDED'" placeholder="https://"><small v-if="toolErrors.url" class="field-error">{{ toolErrors.url }}</small></label><label>图片 URL（可选）<input v-model="toolDraft.imageUrl" type="url" inputmode="url" placeholder="https://"><small v-if="toolErrors.imageUrl" class="field-error">{{ toolErrors.imageUrl }}</small></label></div>
          <div class="form-columns"><label v-if="toolDraft.type === 'EMBEDDED'">白名单组件<select v-model="toolDraft.componentKey"><option value="">请选择</option><option v-for="key in componentKeys" :key="key" :value="key">{{ key }}</option></select><small v-if="toolErrors.componentKey" class="field-error">{{ toolErrors.componentKey }}</small></label><label>标签（逗号分隔）<input v-model="toolDraft.tags" maxlength="2080" placeholder="json, format"></label></div>
          <p v-if="toolError" class="form-error" role="alert">{{ toolError }}</p>
          <div class="button-row"><button class="instrument-button" type="submit" :disabled="savingTool">{{ savingTool ? 'WRITING…' : toolDraft.id ? '保存工具' : '创建工具' }}</button><button v-if="toolDraft.id && toolDraft.status === 'ACTIVE'" class="plain-button" type="button" :disabled="savingTool" @click="deactivateTool">立即停用</button></div>
        </form>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { components } from '@haoblog/api-client'
import { onMounted, reactive, ref } from 'vue'
import { toolFormToCreateRequest, toolFormToUpdateRequest, toolToForm, validateToolForm, type ToolFormModel } from '../../utils/studioToolForm'

definePageMeta({ layout: 'studio' })
type ToolType = components['schemas']['ToolType']
const { listCategories, createCategory, updateCategory, deleteCategory, listTools, createTool, updateTool } = useAdminTools()
const categories = ref<components['schemas']['ToolCategoryResponse'][]>([])
const tools = ref<components['schemas']['ToolResponse'][]>([])
const page = ref(0); const pageSize = 20; const total = ref(0); const loading = ref(false)
const errorMessage = ref(''); const categoryError = ref(''); const toolError = ref(''); const savingCategory = ref(false); const savingTool = ref(false)
const filters = reactive<{ categoryId: string; type: ToolType | ''; status: components['schemas']['ToolStatus'] | ''; keyword: string }>({ categoryId: '', type: '', status: '', keyword: '' })
const categoryDraft = reactive<{ id: string; name: string; slug: string; description: string; sortOrder: number; version: number | null }>({ id: '', name: '', slug: '', description: '', sortOrder: 0, version: null })
const toolDraft = reactive<ToolFormModel>(toolToForm(null))
const toolErrors = ref<Record<string, string>>({})
const toolTypes: ToolType[] = ['LINK', 'EMBEDDED', 'SHOWCASE']; const componentKeys = ['json-format', 'base64', 'url-codec', 'timestamp', 'regex-test'] as const

function newCategory() { Object.assign(categoryDraft, { id: '', name: '', slug: '', description: '', sortOrder: 0, version: null }); categoryError.value = '' }
function editCategory(category: typeof categories.value[number]) { Object.assign(categoryDraft, { ...category, description: category.description || '' }); categoryError.value = '' }
function newTool() { Object.assign(toolDraft, toolToForm(null, categories.value[0]?.id || '')); toolErrors.value = {}; toolError.value = '' }
function editTool(tool: typeof tools.value[number]) { Object.assign(toolDraft, toolToForm(tool)); toolErrors.value = {}; toolError.value = '' }
function toError(cause: unknown, fallback: string) { return cause instanceof Error ? cause.message : fallback }

async function loadCategories() { categories.value = await listCategories() }
async function loadTools() { loading.value = true; errorMessage.value = ''; try { const result = await listTools({ page: page.value, size: pageSize, categoryId: filters.categoryId || undefined, type: filters.type || undefined, status: filters.status || undefined, keyword: filters.keyword }); tools.value = result.items; total.value = result.total } catch (cause) { errorMessage.value = toError(cause, '工具列表暂时不可用。') } finally { loading.value = false } }
async function load() { try { await loadCategories(); if (!toolDraft.categoryId) toolDraft.categoryId = categories.value[0]?.id || ''; await loadTools() } catch (cause) { errorMessage.value = toError(cause, '工具控制台暂时不可用。') } }
async function saveCategory() { savingCategory.value = true; categoryError.value = ''; try { const payload = { name: categoryDraft.name.trim(), slug: categoryDraft.slug.trim(), description: categoryDraft.description.trim() || null, sortOrder: categoryDraft.sortOrder }; const result = categoryDraft.id ? await updateCategory(categoryDraft.id, { ...payload, version: categoryDraft.version ?? 0 }) : await createCategory(payload); await loadCategories(); editCategory(result) } catch (cause) { categoryError.value = toError(cause, '分类写入失败。') } finally { savingCategory.value = false } }
async function removeCategory() { if (!categoryDraft.id || !window.confirm('删除这个工具分类？')) return; categoryError.value = ''; try { await deleteCategory(categoryDraft.id, categoryDraft.version ?? 0); newCategory(); await loadCategories(); await loadTools() } catch (cause) { categoryError.value = toError(cause, '分类删除失败，可能仍被工具使用。') } }
async function saveTool() { toolErrors.value = validateToolForm(toolDraft); if (Object.keys(toolErrors.value).length) return; savingTool.value = true; toolError.value = ''; try { const result = toolDraft.id ? await updateTool(toolDraft.id, toolFormToUpdateRequest(toolDraft)) : await createTool(toolFormToCreateRequest(toolDraft)); await loadTools(); editTool(result) } catch (cause) { toolError.value = toError(cause, '工具写入失败。') } finally { savingTool.value = false } }
async function deactivateTool() { toolDraft.status = 'INACTIVE'; await saveTool() }
async function applyFilters() { page.value = 0; await loadTools() }
async function changePage(next: number) { page.value = next; await loadTools() }
onMounted(load)
</script>

<style scoped>
.tools-scene { max-width: 110rem; margin: 0 auto; }
.page-heading, .panel-heading, .button-row { display: flex; align-items: end; justify-content: space-between; gap: var(--space-4); }
.page-heading { margin-bottom: var(--space-8); } h1 { margin: .8rem 0 0; font-size: clamp(2.5rem, 8vw, 6rem); } h2 { margin: 0; color: var(--color-text-main); font: var(--text-lg)/1.2 var(--font-display); }
.control-grid { display: grid; grid-template-columns: minmax(15rem, 22rem) minmax(0, 1fr); gap: var(--space-8); align-items: start; }
.category-panel, .tool-editor { padding: var(--space-4); border: 1px solid var(--color-border); background: color-mix(in srgb, var(--color-bg-sub) 72%, transparent); }
.category-panel { display: grid; gap: var(--space-5); } .console-form, .tool-editor { display: grid; gap: var(--space-4); }
label { display: grid; gap: .35rem; color: var(--color-text-muted); font: var(--text-xs)/1.3 var(--font-mono); } input, select, textarea { width: 100%; min-width: 0; padding: .55rem .6rem; border: 1px solid var(--color-border); border-radius: 0; background: var(--color-bg-base); color: var(--color-text-main); font: var(--text-sm)/1.35 var(--font-body); } textarea { resize: vertical; } input:focus, select:focus, textarea:focus { border-color: var(--color-accent); outline: 1px solid var(--color-accent); }
.category-list, .tool-log { display: grid; gap: 1px; margin: 0; padding: 0; list-style: none; background: var(--color-border); } .category-list li, .tool-log li { background: var(--color-bg-base); } .category-list li[data-active='true'], .tool-log li[data-active='true'] { background: color-mix(in srgb, var(--color-accent) 9%, var(--color-bg-base)); } .category-list button { display: flex; width: 100%; justify-content: space-between; gap: .5rem; padding: .7rem; border: 0; background: transparent; color: var(--color-text-main); text-align: left; cursor: pointer; font: var(--text-sm)/1.3 var(--font-body); } .category-list small { color: var(--color-text-muted); font: var(--text-xs)/1 var(--font-mono); }
.tool-panel { display: grid; gap: var(--space-6); min-width: 0; } .filter-console { display: grid; grid-template-columns: repeat(4, minmax(8rem, 1fr)) auto; align-items: end; gap: var(--space-3); padding-bottom: var(--space-3); border-bottom: 1px solid var(--color-border); } .keyword-field { min-width: 12rem; }
.tool-row { display: grid; grid-template-columns: 2.5rem .5rem minmax(0, 1fr) auto; align-items: center; gap: .7rem; width: 100%; padding: .8rem .7rem; border: 0; background: transparent; color: var(--color-text-main); text-align: left; cursor: pointer; } .tool-index, .tool-state, .tool-copy small, .pagination, .signal-note, .empty-signal { font: var(--text-xs)/1.3 var(--font-mono); } .tool-index, .tool-copy small, .tool-state { color: var(--color-text-muted); } .tool-copy { display: grid; gap: .25rem; min-width: 0; } .tool-copy strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font: var(--text-base)/1.3 var(--font-display); } .tool-signal { width: .45rem; height: .45rem; border-radius: 50%; background: var(--color-warn); } .tool-signal[data-status='ACTIVE'] { background: var(--color-accent); }
.form-columns { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--space-4); } .instrument-button { width: fit-content; padding: .7rem 1rem; border: 1px solid var(--color-accent); background: transparent; color: var(--color-accent); cursor: pointer; font: var(--text-xs)/1 var(--font-mono); } .instrument-button:hover:not(:disabled), .instrument-button:focus-visible { background: var(--color-accent); color: var(--color-accent-ink); } .instrument-button:disabled { cursor: wait; opacity: .55; } .plain-button { padding: .4rem 0; border: 0; background: transparent; color: var(--color-text-muted); cursor: pointer; font: var(--text-xs)/1 var(--font-mono); } .plain-button:hover:not(:disabled), .plain-button:focus-visible { color: var(--color-accent); } .form-error, .field-error { color: var(--color-warn); font: var(--text-xs)/1.4 var(--font-mono); } .pagination { display: flex; justify-content: center; gap: var(--space-6); color: var(--color-text-muted); }
@media (max-width: 860px) { .control-grid { grid-template-columns: 1fr; } .category-panel { grid-template-columns: minmax(0, 1fr) minmax(12rem, 1fr); } .category-list { grid-column: 1 / -1; } }
@media (max-width: 600px) { .filter-console, .form-columns, .category-panel { grid-template-columns: 1fr; } .filter-console .instrument-button { justify-self: start; } }
@media (max-width: 360px) { .page-heading { align-items: start; flex-direction: column; } .category-panel, .tool-editor { padding: var(--space-3); } .tool-row { grid-template-columns: 2rem .45rem minmax(0, 1fr); } .tool-state { display: none; } }
</style>
