# Mock Dependency Register

This register tracks frontend mock data, offline fallbacks, and demo-only copy so the project stays honest about what is production-integrated versus illustrative.

## Current Snapshot

- Frontend source files scanned: 38
- Mock/fallback/demo references found: 107
- Source files with API or gateway integration code: 18

## Operating Policy

- Runtime fallbacks may keep the learning UI usable when backend services are offline, but each fallback must show a user-facing degraded-state message.
- Mock data should stay deterministic and local; do not mix it with persisted learner records.
- New mock assets should include the backend endpoint or product decision needed to retire them.
- Run `npm run audit:mocks` from the repository root before publishing changes that add learning-workbench features.

## Inventory

### Runtime Fallback

- `frontend-ui/src/api/knowledgeMapFeed.ts:20` - function isTutorOfflineFallbackTips(tips: string[]): boolean {
- `frontend-ui/src/api/knowledgeMapFeed.ts:118` - if (isTutorOfflineFallbackTips(rawTips)) {
- `frontend-ui/src/api/modules/ai.ts:288` - * 联调时由后端返回 {@link PublicVaultItemDTO}[]；当前页面在失败或空数组时回退 Mock。
- `frontend-ui/src/components/GlobalAiTutor.vue:345` - function tutorFallbackLines(err: unknown): string[] {
- `frontend-ui/src/components/GlobalAiTutor.vue:396` - tutorFallbackLines(new Error(lastError.value \|\| 'unknown')),
- `frontend-ui/src/components/GlobalAiTutor.vue:701` - tips.value = tutorFallbackLines(err)
- `frontend-ui/src/components/GlobalAiTutor.vue:743` - content: tutorFallbackLines(err).join('\n'),
- `frontend-ui/src/components/PromptAbLabPanel.vue:80` - <el-tag v-if="judgeResult.mock" size="small" type="info">演示/解析回退</el-tag>

### Mock Data

- `frontend-ui/src/api/modules/promptAbExperiment.ts:13` - mock?: boolean
- `frontend-ui/src/components/GlobalCommandPalette.vue:62` - const MOCK_ITEMS: CommandItem[] = [
- `frontend-ui/src/components/GlobalCommandPalette.vue:75` - if (!q) return MOCK_ITEMS
- `frontend-ui/src/components/GlobalCommandPalette.vue:76` - return MOCK_ITEMS.filter(
- `frontend-ui/src/components/GlobalCommandPalette.vue:112` - // Mock：仅演示，可在此接入 router.push 或事件总线
- `frontend-ui/src/components/KnowledgeUniverse.vue:97` - interface GraphMockData {
- `frontend-ui/src/components/KnowledgeUniverse.vue:104` - mockData?: GraphMockData
- `frontend-ui/src/components/KnowledgeUniverse.vue:112` - mockData: undefined,
- `frontend-ui/src/components/KnowledgeUniverse.vue:159` - const DEFAULT_GRAPH_MOCK: GraphMockData = {
- `frontend-ui/src/components/KnowledgeUniverse.vue:240` - const graphData = computed<GraphMockData>(() => {
- `frontend-ui/src/components/KnowledgeUniverse.vue:241` - const base = props.mockData ?? DEFAULT_GRAPH_MOCK
- `frontend-ui/src/components/KnowledgeUniverse.vue:683` - function buildKnowledgeGraph(data: GraphMockData) {
- `frontend-ui/src/components/KnowledgeUniverse.vue:1091` - () => props.mockData,
- `frontend-ui/src/components/PromptAlchemist.vue:122` - <el-button @click="loadMockData">加载 Mock</el-button>
- `frontend-ui/src/components/PromptAlchemist.vue:160` - import { getMockTokenProbabilityTree } from '@/mocks/aiMocks'
- `frontend-ui/src/components/PromptAlchemist.vue:166` - } from '@/mocks/promptGallery'
- `frontend-ui/src/components/PromptAlchemist.vue:320` - // 网络波动：无缝切换到静态 Mock 数据，确保演示不中断
- `frontend-ui/src/components/PromptAlchemist.vue:323` - treeData.value = getMockTokenProbabilityTree({
- `frontend-ui/src/components/PromptAlchemist.vue:330` - ElMessage.warning('概率树加载失败，已切换到 Mock 渲染')
- `frontend-ui/src/components/PromptAlchemist.vue:364` - function loadMockData() {
- `frontend-ui/src/components/PromptAlchemist.vue:365` - logOp('LOAD_MOCK', {
- `frontend-ui/src/components/PromptAlchemist.vue:593` - loadMockData()
- `frontend-ui/src/data/knowledgeMapGraph.ts:2` - * 知识星空 · 计算机 / 软件工程 分层知识网络 Mock
- `frontend-ui/src/data/knowledgeMapGraph.ts:453` - export const MOCK_PUBLIC_MATERIALS_BY_NODE: Record<string, PublicMaterialItem[]> = (() => {
- `frontend-ui/src/mocks/aiMocks.ts:7` - export interface VectorChunkMock {
- `frontend-ui/src/mocks/aiMocks.ts:16` - export interface TokenTreeNodeMock {
- `frontend-ui/src/mocks/aiMocks.ts:19` - children?: TokenTreeNodeMock[]
- `frontend-ui/src/mocks/aiMocks.ts:52` - }): VectorChunkMock[] {
- `frontend-ui/src/mocks/aiMocks.ts:64` - const chunks: VectorChunkMock[] = []
- `frontend-ui/src/mocks/aiMocks.ts:87` - export function getMockDocumentProcessResponse(args: {
- `frontend-ui/src/mocks/aiMocks.ts:104` - const mockChunks = chunks.map((c, idx) => ({
- `frontend-ui/src/mocks/aiMocks.ts:107` - textPreview: `Mock 文本块（用于演示）#${idx}：RAG 流程节点/参数配置与可视化映射`,
- `frontend-ui/src/mocks/aiMocks.ts:113` - chunkCount: mockChunks.length,
- `frontend-ui/src/mocks/aiMocks.ts:116` - chunks: mockChunks,
- `frontend-ui/src/mocks/aiMocks.ts:120` - export function getMockRagSearchResponse(args: {
- `frontend-ui/src/mocks/aiMocks.ts:122` - vectorChunks: VectorChunkMock[]
- `frontend-ui/src/mocks/aiMocks.ts:137` - text: `Mock 检索命中：${args.query} -> Chunk #${idx}`,
- `frontend-ui/src/mocks/aiMocks.ts:153` - export function getMockTokenProbabilityTree(args: {
- `frontend-ui/src/mocks/aiMocks.ts:157` - }): TokenTreeNodeMock {
- `frontend-ui/src/mocks/aiMocks.ts:174` - const normalize2 = (arr: TokenTreeNodeMock[]) => {
- `frontend-ui/src/mocks/aiMocks.ts:180` - const a = normalize2(childrenA as unknown as TokenTreeNodeMock[])
- `frontend-ui/src/mocks/aiMocks.ts:181` - const b = normalize2(childrenB as unknown as TokenTreeNodeMock[])
- `frontend-ui/src/mocks/promptTemplates.ts:27` - id: 'mock-interview',
- `frontend-ui/src/views/AuthPortal.vue:287` - const mockSubmit = async () => {
- `frontend-ui/src/views/AuthPortal.vue:298` - await mockSubmit()
- `frontend-ui/src/views/AuthPortal.vue:309` - await mockSubmit()
- `frontend-ui/src/views/workbench/KnowledgeMap.vue:9` - :mock-data="graphMock"
- `frontend-ui/src/views/workbench/KnowledgeMap.vue:23` - * 全屏星野 + KnowledgeUniverse（节点点击由子组件内 el-drawer 展示 Mock 详情）
- `frontend-ui/src/views/workbench/KnowledgeMap.vue:35` - const graphMock = computed(() => ({
- `frontend-ui/src/views/workbench/KnowledgeStarrySky.vue:19` - :mock-data="graphMock"
- `frontend-ui/src/views/workbench/KnowledgeStarrySky.vue:116` - const graphMock = computed(() => ({
- `frontend-ui/src/views/workbench/LearningLab.vue:34` - <p class="notion-read__meta">精读文稿 · Mock</p>
- `frontend-ui/src/views/workbench/LearningLab.vue:174` - /** Mock：按节点简单哈希出固定进度，避免每页都是同一数字 */
- `frontend-ui/src/views/workbench/Marketplace.vue:154` - /** 8 条大模型场景 Mock，接口可用后由 {@link fetchPublicVault} 替换 */
- `frontend-ui/src/views/workbench/Marketplace.vue:155` - const MOCK_VAULT: VaultItem[] = [
- `frontend-ui/src/views/workbench/Marketplace.vue:368` - items.value = MOCK_VAULT
- `frontend-ui/src/views/workbench/Marketplace.vue:371` - items.value = MOCK_VAULT
- `frontend-ui/src/views/workbench/RagVisualWorkbench.vue:106` - import { getMockDocumentProcessResponse, getMockRagSearchResponse, type VectorChunkMock } from '@/mocks/aiMocks'
- `frontend-ui/src/views/workbench/RagVisualWorkbench.vue:241` - const mockDoc = getMockDocumentProcessResponse({
- `frontend-ui/src/views/workbench/RagVisualWorkbench.vue:247` - vectorChunks.value = mockDoc.chunks.map((item, index) => ({
- `frontend-ui/src/views/workbench/RagVisualWorkbench.vue:256` - lastUploadSummary.value = `Mock 文档处理完成（真实上传失败：${message}）`
- `frontend-ui/src/views/workbench/RagVisualWorkbench.vue:260` - recentAction: '切换 Mock chunks',
- `frontend-ui/src/views/workbench/RagVisualWorkbench.vue:261` - extraContext: { documentId: mockDoc.documentId, chunkCount: mockDoc.chunkCount },
- `frontend-ui/src/views/workbench/RagVisualWorkbench.vue:264` - ElMessage.warning('上传失败，已切换到 Mock 展示')
- `frontend-ui/src/views/workbench/RagVisualWorkbench.vue:329` - const mockDoc = getMockDocumentProcessResponse({
- `frontend-ui/src/views/workbench/RagVisualWorkbench.vue:335` - vectorChunks.value = mockDoc.chunks.map((item, index) => ({
- `frontend-ui/src/views/workbench/RagVisualWorkbench.vue:345` - const mockRag = getMockRagSearchResponse({
- `frontend-ui/src/views/workbench/RagVisualWorkbench.vue:348` - vectorChunks: vectorChunks.value.map((c): VectorChunkMock => ({
- `frontend-ui/src/views/workbench/RagVisualWorkbench.vue:358` - searchResults.value = mockRag.results
- `frontend-ui/src/views/workbench/RagVisualWorkbench.vue:376` - recentAction: '切换 Mock 检索结果',
- `frontend-ui/src/views/workbench/RagVisualWorkbench.vue:377` - extraContext: { hitCount: mockRag.results.length },
- `frontend-ui/src/views/workbench/RagVisualWorkbench.vue:380` - ElMessage.warning('检索失败，已切换到 Mock 展示')

### Demo Copy

- `frontend-ui/src/components/AgentArena.vue:7` - 需启动后端（含 WebSocket）；黑板内容由「开始协作演示」触发。角色卡片可随时点击切换视角。
- `frontend-ui/src/components/AgentArena.vue:13` - 开始协作演示
- `frontend-ui/src/components/AgentArena.vue:23` - ? 'WebSocket 已接通，可点击「开始协作演示」拉取多智能体发言。'
- `frontend-ui/src/components/AgentArena.vue:287` - ElMessage.success('演示已触发，黑板将逐条出现各角色发言')
- `frontend-ui/src/components/AgentArena.vue:289` - ElMessage.error(e instanceof Error ? e.message : '触发演示失败，请检查后端与 AI 网关')
- `frontend-ui/src/components/RagBlueprint.vue:6` - <p>节点内可调参；切分策略会同步右侧 3D 点云密度。「测试检索」演示查询向量 → Top-K 连线。</p>
- `frontend-ui/src/components/RagBlueprint.vue:324` - ElMessage.success({ message: '执行流：已在 3D 视图演示 Top-K 连线', grouping: true })
- `frontend-ui/src/components/VectorSpace3D.vue:630` - ? '图谱边服务暂不可用，已用邻近节点连线演示'
- `frontend-ui/src/components/VectorSpace3D.vue:638` - edgesApiUnavailableHint.value = '无法连接图谱边服务，已用邻近节点连线演示'
- `frontend-ui/src/stores/globalLearningContext.ts:535` - /** 以当前模拟点云为「库」，从原点查询向量演示 Top-K（供 3D 高亮流） */
- `frontend-ui/src/views/AuthPortal.vue:165` - 请输入注册时使用的邮箱或手机号，我们将发送重置链接（当前为演示环境，仅模拟发送）。
- `frontend-ui/src/views/AuthPortal.vue:341` - ElMessage.success(`已向「${forgotPwdForm.account}」发送重置指引（演示模式，无真实邮件）`)
- `frontend-ui/src/views/workbench/AgentArenaWorkbench.vue:8` - <el-button class="demo-btn" :icon="VideoPlay" @click="showDemoModal = true">📺 玩法演示</el-button>
- `frontend-ui/src/views/workbench/AgentArenaWorkbench.vue:15` - <el-dialog v-model="showDemoModal" title="实验三 · 玩法演示" width="560px">
- `frontend-ui/src/views/workbench/AgentArenaWorkbench.vue:16` - <p>这里将放置多智能体协作演示视频，后续可嵌入完整实战案例。</p>
- `frontend-ui/src/views/workbench/LearningLab.vue:127` - /** 演示用：nodeId → 展示标题（真实项目可由 API 返回） */
- `frontend-ui/src/views/workbench/Marketplace.vue:369` - vaultError.value = '公共库返回为空，已展示本地演示数据。'
- `frontend-ui/src/views/workbench/Marketplace.vue:372` - vaultError.value = '无法连接 /api/v1/rag/public-vault，已展示本地演示数据。'
- `frontend-ui/src/views/workbench/PromptWorkbench.vue:8` - <el-button class="demo-btn" :icon="VideoPlay" @click="showDemoModal = true">📺 玩法演示</el-button>
- `frontend-ui/src/views/workbench/PromptWorkbench.vue:40` - <el-dialog v-model="showDemoModal" title="实验一 · 玩法演示" width="560px">
- `frontend-ui/src/views/workbench/PromptWorkbench.vue:41` - <p>这里将放置 Prompt 入门演示视频，后续可直接嵌入 B 站或本地教学视频。</p>
- `frontend-ui/src/views/workbench/RagBuildWorkbench.vue:8` - <el-button class="demo-btn" :icon="VideoPlay" @click="showDemoModal = true">📺 玩法演示</el-button>
- `frontend-ui/src/views/workbench/RagBuildWorkbench.vue:90` - <el-dialog v-model="showDemoModal" title="知识外脑 · 玩法演示" width="560px">
- `frontend-ui/src/views/workbench/RagBuildWorkbench.vue:91` - <p>这里将放置 RAG 蓝图搭建演示视频，后续可嵌入完整教学流程。</p>
- `frontend-ui/src/views/workbench/RagVisualWorkbench.vue:9` - <el-button class="demo-btn" :icon="VideoPlay" @click="showDemoModal = true">📺 玩法演示</el-button>
- `frontend-ui/src/views/workbench/RagVisualWorkbench.vue:89` - <el-dialog v-model="showDemoModal" title="实验二 · 玩法演示" width="560px">
- `frontend-ui/src/views/workbench/RagVisualWorkbench.vue:90` - <p>这里将放置 RAG 检索与 3D 向量空间联动演示视频。</p>

## Retirement Backlog

| Area | Current fallback | Practical next step |
| --- | --- | --- |
| Marketplace vault | Local `MOCK_VAULT` records when `/api/v1/rag/public-vault` is empty or unavailable | Seed backend public-vault data and show an empty state only when the API returns no records |
| RAG visual workbench | Generated document chunks and search hits after upload/search failures | Add a local fixture upload endpoint for development and reserve mock chunks for explicit demo mode |
| Knowledge map | Static graph data and per-node materials | Back graph nodes with `backend-services/sql` seed data and expose a read-only graph endpoint |
| Auth portal | Simulated login/register/reset flows | Wire to backend auth endpoints or label the module as a prototype until auth is ready |
| Tutorial videos | Placeholder modal copy | Replace with hosted videos or remove the modal from production navigation |
