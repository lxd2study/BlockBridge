# AI Builder Mod 独立项目开发计划

## 目标

新建一个独立 Forge 1.20.1 客户端 mod，用于在单人世界或局域网主机世界中接入 OpenAI 兼容接口，并根据玩家提示词自动生成建筑方案、预览范围、确认后分批建造。

第一版只支持 integrated server 场景，不支持普通多人服务器直接改世界。

## 项目基础

- 项目名称：`ai-builder-mod`
- Mod ID：`ai_builder`
- Minecraft：`1.20.1`
- Forge：`47.x`
- Java：`17`
- 类型：客户端 mod，`clientSideOnly=true`
- 构建：ForgeGradle 6.x
- 外部依赖：无，HTTP 使用 Java 17 内置 `java.net.http.HttpClient`

建议包名：

```text
com.lxd.aibuilder
```

建议 Gradle 输出：

```text
build/libs/ai_builder-0.1.0.jar
```

## 功能范围

第一版包含：

- 独立 AI 配置页面。
- 游戏内 AI 建筑生成页面。
- 快捷键打开 AI 建筑页面。
- OpenAI 兼容 `/chat/completions` 非流式调用。
- AI 返回 JSON 建筑计划。
- 本地校验建筑计划。
- 建筑包围盒预览。
- 玩家确认后直接编辑世界方块。
- 分 tick 放置，避免一次性卡死游戏。
- 建造中取消。

第一版不包含：

- 多人服务器无权限建造。
- 消耗玩家背包材料。
- 撤销功能。
- 复杂蓝图编辑器。
- 外部代理服务。
- 流式输出。

## 配置设计

配置文件：

```text
config/ai_builder.properties
```

配置项：

```properties
baseUrl=https://api.openai.com/v1
apiKey=
model=
timeoutSeconds=60
maxWidth=48
maxHeight=32
maxDepth=48
maxBlocks=50000
blocksPerTick=128
jsonMode=true
```

校验规则：

- `baseUrl` 必须是 `http://` 或 `https://`。
- `apiKey` 允许为空，但调用 AI 前必须填写。
- `model` 调用前必须填写。
- `timeoutSeconds` 范围：`5-300`。
- `maxWidth/maxHeight/maxDepth` 范围：`1-128`，默认仍限制在 `48x32x48`。
- `maxBlocks` 范围：`1-200000`。
- `blocksPerTick` 范围：`1-2048`。

API Key 第一版明文保存在本地配置中，在 UI 中提示“仅保存在本机配置文件”。

## 核心模块

### 1. Mod 入口

类：

```text
com.lxd.aibuilder.AiBuilderMod
```

职责：

- 加载 `AiBuilderConfig`。
- 初始化 `BuildExecutor`。
- 注册 Forge client events。
- 注册 Mods 配置页。

### 2. 配置

类：

```text
com.lxd.aibuilder.config.AiBuilderConfig
```

职责：

- 从 `config/ai_builder.properties` 加载配置。
- 保存配置。
- 提供 `copy()`。
- 提供 `validate()` 和 `validateForRequest()`。

### 3. UI

类：

```text
com.lxd.aibuilder.client.AiBuilderConfigScreen
com.lxd.aibuilder.client.AiBuildScreen
```

AI 配置页字段：

- Base URL
- API Key
- Model
- Timeout seconds
- Max width
- Max height
- Max depth
- Max blocks
- Blocks per tick
- JSON mode
- 保存
- 测试连接
- 返回

AI 建筑页字段：

- 提示词输入框
- 当前目标位置摘要
- 生成按钮
- 状态信息
- 建筑名称
- 尺寸
- 预计方块数
- 材料列表
- 确认建造
- 取消建造
- 返回

UI 行为：

- 没有进入世界时，AI 建筑页禁用生成和建造。
- 普通多人服务器中禁用建造，并提示“仅支持单人世界/局域网主机”。
- 生成成功后显示预览。
- 离开页面时清理未确认预览。

### 4. 快捷键和事件

类：

```text
com.lxd.aibuilder.client.ClientAiBuilderEvents
com.lxd.aibuilder.client.AiBuilderKeyMappings
```

职责：

- 注册快捷键，默认建议 `B` 或未绑定，名称为“Open AI Builder”。
- 客户端 tick 中检测快捷键并打开 `AiBuildScreen`。
- 客户端渲染事件中绘制预览包围盒。
- 客户端 tick 中推进 `BuildExecutor` 状态显示。

### 5. AI HTTP 客户端

类：

```text
com.lxd.aibuilder.ai.AiChatClient
com.lxd.aibuilder.ai.AiBuildPrompt
com.lxd.aibuilder.ai.AiBuildRequest
com.lxd.aibuilder.ai.AiBuildResult
```

请求：

- URL：`{baseUrl}/chat/completions`
- Method：`POST`
- Header：
  - `Authorization: Bearer {apiKey}`
  - `Content-Type: application/json`
- Body：
  - `model`
  - `messages`
  - `temperature: 0.2`
  - `stream: false`
  - `response_format: {"type":"json_object"}`，仅当 `jsonMode=true`

线程模型：

- 使用后台线程或 `CompletableFuture.supplyAsync()` 调用 AI。
- UI 线程只读状态，不阻塞游戏渲染。

### 6. 建筑计划模型

类：

```text
com.lxd.aibuilder.build.BuildPlan
com.lxd.aibuilder.build.BuildOperation
com.lxd.aibuilder.build.BuildMaterial
com.lxd.aibuilder.build.BuildSize
```

AI JSON 格式：

```json
{
  "name": "Small Wooden House",
  "size": {
    "width": 9,
    "height": 6,
    "depth": 7
  },
  "materials": [
    {
      "id": "minecraft:oak_planks",
      "label": "main wall"
    }
  ],
  "operations": [
    {
      "type": "fill",
      "block": "minecraft:oak_planks",
      "from": [0, 0, 0],
      "to": [8, 0, 6]
    },
    {
      "type": "hollow_box",
      "block": "minecraft:oak_planks",
      "from": [0, 1, 0],
      "to": [8, 4, 6]
    },
    {
      "type": "set",
      "block": "minecraft:oak_door",
      "at": [4, 1, 0]
    }
  ]
}
```

第一版支持操作：

- `set`：设置单个方块。
- `fill`：填充长方体。
- `hollow_box`：生成空心长方体外壳。

坐标规则：

- 所有坐标均为相对坐标。
- `x` 范围：`0 <= x < width`
- `y` 范围：`0 <= y < height`
- `z` 范围：`0 <= z < depth`

### 7. JSON 解析

类：

```text
com.lxd.aibuilder.ai.BuildPlanParser
com.lxd.aibuilder.util.JsonReader
```

实现策略：

- 不新增第三方 JSON 库。
- 第一版可以实现一个小型 JSON reader，支持对象、数组、字符串、数字、布尔、null。
- 解析 AI 响应时先读取 chat completions 的 `choices[0].message.content`。
- 再把 content 当作建筑计划 JSON 解析。
- 如果 content 被模型包在 Markdown 代码块中，剥离外层 ```json 代码块。

错误处理：

- 非 JSON 响应：提示“AI did not return valid JSON.”
- 缺字段：提示缺失字段名。
- 操作类型未知：提示未知操作类型。
- 坐标格式错误：提示具体操作序号。

### 8. 建筑计划校验

类：

```text
com.lxd.aibuilder.build.BuildPlanValidator
com.lxd.aibuilder.build.BuildPlanStats
```

校验规则：

- `name` 非空。
- `size` 三维必须大于 0。
- `size` 不超过配置上限。
- `operations` 非空。
- 每个方块 ID 必须能在 `ForgeRegistries.BLOCKS` 中找到。
- 每个坐标必须在建筑尺寸内。
- `from` 和 `to` 会规范化为 min/max。
- 预计放置方块数不超过 `maxBlocks`。

统计：

- 建筑尺寸。
- 操作数量。
- 预计放置方块数。
- 材料 ID 列表。

### 9. 目标位置和方向

类：

```text
com.lxd.aibuilder.build.BuildPlacement
```

规则：

- 优先使用玩家准星命中的方块上表面作为原点。
- 未命中方块时，使用玩家脚下位置前方 3 格作为原点。
- 根据玩家水平朝向旋转建筑：
  - North：默认 `z` 向北。
  - South：`z` 向南。
  - West：`x/z` 旋转到西向。
  - East：`x/z` 旋转到东向。
- 包围盒用于预览和覆盖提示。

### 10. 建造执行器

类：

```text
com.lxd.aibuilder.build.BuildExecutor
com.lxd.aibuilder.build.BuildTask
com.lxd.aibuilder.build.BlockPlacementStep
com.lxd.aibuilder.build.BuildStatus
```

执行条件：

- `Minecraft.getInstance().getSingleplayerServer()` 不为 null。
- 玩家当前在同一个 integrated server 世界内。
- 建筑计划已通过校验。

执行流程：

1. 将 `BuildPlan` 编译为 `BlockPlacementStep` 队列。
2. 每 tick 执行最多 `blocksPerTick` 个放置操作。
3. 使用服务端世界 `ServerLevel#setBlock` 放置方块。
4. 建造过程中更新进度：已放置/总数。
5. 用户取消时停止后续放置。

默认行为：

- 覆盖区域内已有方块。
- 不消耗材料。
- 不生成撤销记录。

## Prompt 设计

系统提示词目标：

- 要求模型只输出 JSON。
- 限制尺寸不超过配置。
- 禁止使用 modded block，除非用户明确要求且本地可用。
- 优先使用常见原版方块。
- 坐标必须从 `[0,0,0]` 开始。
- 建筑入口默认朝向本地 `z=0` 面，mod 会根据玩家方向旋转。
- 操作数量尽量少，优先使用 `fill` 和 `hollow_box`。

用户提示词模板：

```text
Player request:
{userPrompt}

Limits:
- Max width: {maxWidth}
- Max height: {maxHeight}
- Max depth: {maxDepth}
- Max estimated blocks: {maxBlocks}

Return a single JSON object using this schema:
...
```

## 资源和翻译

资源路径：

```text
src/main/resources/assets/ai_builder/lang/en_us.json
src/main/resources/assets/ai_builder/lang/zh_cn.json
```

需要翻译的主要键：

- `ai_builder.screen.config.title`
- `ai_builder.screen.build.title`
- `ai_builder.screen.base_url`
- `ai_builder.screen.api_key`
- `ai_builder.screen.model`
- `ai_builder.screen.timeout`
- `ai_builder.screen.max_size`
- `ai_builder.screen.max_blocks`
- `ai_builder.screen.blocks_per_tick`
- `ai_builder.screen.json_mode`
- `ai_builder.screen.save`
- `ai_builder.screen.test`
- `ai_builder.screen.generate`
- `ai_builder.screen.confirm_build`
- `ai_builder.screen.cancel`
- `ai_builder.status.ready`
- `ai_builder.status.generating`
- `ai_builder.status.generated`
- `ai_builder.status.building`
- `ai_builder.status.done`
- `ai_builder.status.failed`
- `key.ai_builder.open`
- `key.categories.ai_builder`

## 推荐项目结构

```text
ai-builder-mod/
  build.gradle
  gradle.properties
  settings.gradle
  src/main/java/com/lxd/aibuilder/
    AiBuilderMod.java
    ai/
      AiBuildPrompt.java
      AiBuildRequest.java
      AiBuildResult.java
      AiChatClient.java
      BuildPlanParser.java
    build/
      BlockPlacementStep.java
      BuildExecutor.java
      BuildMaterial.java
      BuildOperation.java
      BuildPlacement.java
      BuildPlan.java
      BuildPlanStats.java
      BuildPlanValidator.java
      BuildSize.java
      BuildStatus.java
      BuildTask.java
    client/
      AiBuildScreen.java
      AiBuilderConfigScreen.java
      AiBuilderKeyMappings.java
      ClientAiBuilderEvents.java
      PreviewRenderer.java
    config/
      AiBuilderConfig.java
    util/
      JsonReader.java
      JsonWriter.java
  src/main/resources/
    META-INF/mods.toml
    pack.mcmeta
    assets/ai_builder/lang/en_us.json
    assets/ai_builder/lang/zh_cn.json
```

## 开发步骤

1. 使用 Forge 1.20.1 MDK 创建新项目，设置 `mod_id=ai_builder`、`mod_name=AI Builder`、`mod_group_id=com.lxd.aibuilder`。
2. 创建 `AiBuilderConfig` 并完成配置加载、保存、校验。
3. 创建 Mods 配置页 `AiBuilderConfigScreen`，先实现配置保存。
4. 创建 `AiChatClient` 和 prompt 生成逻辑，完成“测试连接”。
5. 创建 JSON reader、chat completions 响应解析、建筑计划解析。
6. 创建 `BuildPlanValidator` 和统计逻辑。
7. 创建 `AiBuildScreen`，接入异步生成、计划摘要和错误显示。
8. 创建 `BuildPlacement` 和 `PreviewRenderer`，显示建筑包围盒。
9. 创建 `BuildExecutor`，按 tick 分批放置方块。
10. 注册快捷键和客户端事件。
11. 补充中英文翻译。
12. 运行构建和手工游戏内测试。

## 验收测试

构建：

```powershell
.\gradlew build
```

配置测试：

- 保存配置后重启游戏，配置仍能加载。
- 空 Base URL、空 Model、非法超时、非法尺寸会显示错误。
- 测试连接失败时页面显示 HTTP 状态或异常信息。

AI 解析测试：

- 合法 JSON 能生成建筑计划。
- Markdown 代码块包裹的 JSON 能被解析。
- 非 JSON、缺字段、未知操作、越界坐标、未知方块 ID 都会失败并显示原因。

建造测试：

- 单人创意世界中输入“小型木屋”，生成后显示预览。
- 确认后建筑按进度分批生成。
- 建造中点击取消，后续方块停止放置。
- 不进入世界时不能建造。
- 普通多人服务器中不能建造。

边界测试：

- 超过最大尺寸的 AI 返回会被拒绝。
- 超过最大方块数的 AI 返回会被拒绝。
- AI 超时不会卡住游戏。
- API Key 错误不会导致崩溃。

## 后续版本方向

- 增加撤销功能。
- 增加只填空气/先清空区域/覆盖三种区域策略。
- 增加生存模式材料消耗。
- 增加结构模板导出 `.nbt`。
- 增加服务端 mod，实现多人服务器授权建造。
- 增加建筑历史记录和复用。
- 增加本地 Ollama 模式。
