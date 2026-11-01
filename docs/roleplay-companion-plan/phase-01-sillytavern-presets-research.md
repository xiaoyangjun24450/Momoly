# 阶段 1：酒馆预设、插件与常驻世界书调研

> 实现更新：已按后续要求加入默认聊天预设和全角色共用的常驻规则书，并接入上下文加载。当前行为见 [默认资源说明](default-online-chat-resources.md)；本文保留为实现前的调研依据。

更新日期：2026-11-01。本文承接首轮调研，按最新要求收窄：角色卡导入暂时仅新增“没有世界书则拒绝”；活人感与纯消息规则最终以当前项目可用的 SillyTavern 预设，或所有角色共用的常驻规则世界书交付。

本次核对了 GitHub 仓库、实际提示词和 Momoly 加载链路。Star 为查询时数值。未运行上游插件，也未用真实模型验证提示词效果；以下区分可直接表达的规则与仍需接入的能力。

## 1. 结论

**常驻世界书可以承载这组规则，预设也可以。关键是共用一份角色无关的聊天规则，让身份、性格和关系继续来自当前角色与上下文。**

在本次查到的候选里，EchoText 和 RPG-Phone 的私聊提示最贴近需求；Sudachi 的聊天模式可作中文参考。Nemo 的 Texting 模块更接近“用聊天界面讲故事”，不能因为名称含 Texting 就直接采用。

这些针对性的预设或插件多为几十至几百 Star。本次没有找到同时满足“高 Star、中文自然私聊、完全禁止旁白、直接适配 Momoly”的完整成品。酒馆助手等 Star 更多的工具提供脚本运行能力，本身不提供已经验证的活人感规则。

## 2. 具体候选

| 项目 | Star / 仓库标注许可证 | 实际找到的内容 | 可以吸收的部分 | 必须调整或排除的部分 |
| --- | --- | --- | --- | --- |
| [EchoText](https://github.com/mattjaybe/SillyTavern-EchoText) | 49 / MIT | 独立角色私聊面板；身份、设定、记忆、回复提醒分段组装 | 把互动明确为线上发消息；使用角色口吻；禁止名字前缀和时间戳；按角色控制篇幅 | 默认仍允许 Markdown，部分情绪模块允许身体动作，部分人格选项会覆盖原角色；不能整套照搬。 |
| [RPG-Phone](https://github.com/tavern-rpg-suite/RPG-Phone) | 6 / AGPL-3.0 | 提示中明确禁止旁白与第三人称动作；短回复模式强调少量短信和不固定追问 | 纯消息边界、自然短回复、减少客服式建议和机械问句 | 预设同时要求姓名冒号、模拟照片/语音/系统状态、允许多人回复，并设定“跨世界魔法手机”和固定恋爱主题；均与本需求存在差异。 |
| [Sudachi-Next](https://github.com/LimeBlogs/Sudachi-Next) | 111 / MIT | 实际 JSON 有 181 个 prompt 条目，包括“聊天（关闭人称）”“生动化”“反全知”等 | 中文聊天模式、跟随角色性格、限制角色知道的信息 | “生动化”还要求动作与内心描写；部分“反八股”限制具体数字，会妨碍回答 18:00。不能整份开启。 |
| [NemoEngine](https://github.com/NemoVonNirgend/NemoEngine) | 192 / API 未识别许可证 | NemoNet 有 Texting Format、Texting Style Toggle | 按模块组织表达风格的思路 | Texting Format 要求发送者、时间戳、输入状态、撤回等叙事元素；开关依赖其他变量消费者，不是独立可用的规则。 |
| [NemoPresetExt](https://github.com/NemoVonNirgend/NemoPresetExt) | 95 / API 未识别许可证 | 为复杂预设提供分组、选项等管理能力 | 参考编辑体验 | 属于预设管理扩展，不等于活人感实现；Momoly 不需要为几个规则条目引入整套界面。 |
| [ST-Prompt-Template](https://github.com/zonde306/ST-Prompt-Template) | 503 / AGPL-3.0 | EJS 条件、变量、世界书与提示注入 | 后续按线上/故事模式切换内容时可参考 | 本阶段静态共用规则无需依赖 EJS；不能把浏览器插件与 Momoly 兼容层等同。 |
| [Tavern-Helper](https://github.com/N0VI028/JS-Slash-Runner) | 1,394 / PolyForm NonCommercial 1.0.0 | 脚本生成、消息读写、事件接口 | 若后续需要脚本式生成流程，可参考接口边界 | 是源码公开的脚本工具，不是现成预设；其非商业许可证也不等同于 MIT。 |

### 2.1 源码定位

- EchoText：[默认提示](https://github.com/mattjaybe/SillyTavern-EchoText/blob/35a7940f77691807be08c4e2e153118341a90c6e/lib/config.js#L163)、[上下文组装](https://github.com/mattjaybe/SillyTavern-EchoText/blob/35a7940f77691807be08c4e2e153118341a90c6e/index.js#L2064)、[情绪与人格覆盖](https://github.com/mattjaybe/SillyTavern-EchoText/blob/35a7940f77691807be08c4e2e153118341a90c6e/lib/untethered-chat.js#L138)。适合提炼提示内容；它有自己的浏览器 UI、历史和生成流程，不是一个可直接导入的预设 JSON。
- RPG-Phone：[主提示与短回复提示](https://github.com/tavern-rpg-suite/RPG-Phone/blob/5d3df0d5bf30b3e6619c5b7190bf2d8c4473df01/index.js#L16)。禁止旁白的目标接近，但输出协议及角色关系假设需要改写。
- Sudachi：[实际预设 JSON](https://github.com/LimeBlogs/Sudachi-Next/blob/c62174f6ab6ca2a38115956e1d70a8c337c26f12/Sudachi-Next-Preset.json)。聊天条目 identifier 为 `d71e515b-f3f0-49fc-bd90-7050f0bba1f9`，通过 `setvar` 写入风格，仍需其他条目读取变量。应提炼独立中文规则，不直接复制孤立开关。
- Nemo：[NemoNet 提示目录](https://github.com/NemoVonNirgend/NemoEngine/tree/986dd7f5395a81cef2b90c4de6d2c8bb13130edf/NemoNet/Prompts)。核对的条目是 `095 - 🎬 Texting Format` 和 `488 - Texting Style Toggle`；默认均关闭，后者仅设置追踪变量。

## 3. 预设与常驻世界书如何选择

[SillyTavern 官方世界书说明](https://docs.sillytavern.app/usage/core-concepts/worldinfo/) 明确允许世界书插入行为指导，且支持全局世界书与角色专属世界书并用；它不只适合存地点或背景。[Prompt Manager](https://docs.sillytavern.app/usage/prompts/prompt-manager/) 则负责控制 Chat Completion 提示内容、开关与排列顺序。

| 形式 | 优点 | 当前项目中的实际情况 |
| --- | --- | --- |
| 独立聊天预设 | 规则、顺序、覆盖行为集中管理；可控制是否使用角色示例 | 已支持 `prompts`、`prompt_order`、`forbid_overrides`、深度注入，适合承载规则。是否用于所有角色仍取决于各角色的预设关联。 |
| 共用常驻规则世界书 | 保留原有模型参数和预设，规则可独立修改；一份书复用于多个角色 | 条目与多书关联已支持，但目前只读取当前角色的关联书，没有全局世界书集合。可以先关联同一份书给多个角色；自动覆盖所有现有及新角色需要额外接入。 |
| 酒馆浏览器插件 | 能自行组织生成、拆分消息和处理事件 | 依赖 SillyTavern 浏览器上下文，不能直接作为 Kotlin 模块安装；适合借鉴提示，完整移植成本更高。 |

当前证据见 [PromptContextLoader.kt:50](/home/xyj/muyang_ws/Momoly/data/resources/src/main/java/me/rerere/momoly/data/PromptContextLoader.kt:50)：只遍历 `card.enabledWorldBookIds`；预设也读取角色的 `linkedPresetId`。仅在世界书 JSON 中添加一个自造的 `global: true` 字段，不会使所有角色自动使用它。

**全局规则书和角色所属世界书是两种用途。** 例如“线上聊天规则”可以同时用于校园和奇幻角色，但不能因此把这些角色归成同一个世界，也不能代替角色 PNG 本身必须附带的世界书。角色世界书继续负责世界归类与设定，共用规则书只提供行为约束。

本轮没有修改全局加载或生成流程；最终可以选择预设，或选择共用规则书并补齐全角色加载。两者不需要同时堆叠一整份重复规则。

## 4. 常驻规则书的可用配置与限制

当前 [世界书解析器](/home/xyj/muyang_ws/Momoly/data/resources/src/main/java/me/rerere/momoly/data/worldbook/WorldBookParser.kt) 和 [提示组装器](/home/xyj/muyang_ws/Momoly/domain/generation/src/main/java/me/rerere/momoly/domain/PromptBuilder.kt:338) 已支持这些必要机制：

- 启用条目，设置 `constant: true`；无需关键词命中。
- 关闭向量激活，采用 100% 激活概率，不设置延迟、冷却或必须递归才触发。
- 可放在角色定义之后，或作为 `system` 角色按聊天深度注入。具体位置需要结合当前预设验证，不能把“放最后”视为天然高于其他指令。
- 使用标准 `{{char}}`、`{{user}}` 宏即可，规则内容先保持普通文本，不依赖 EJS 或外部脚本。

“常驻”意味着不依赖关键词，不代表必然全量进入每次请求。本地代码仍会进行概率、分组和 token 预算筛选；`ignore_budget` 没有对应的运行模型字段。规则应短而完整，并检查真实请求中是否存在，不能只看世界书开关已经打开。

目前的 `EjsTemplate`、`generateRaw` 等兼容 API 位于 [tavern-helper-compat.js](/home/xyj/muyang_ws/Momoly/app/src/main/assets/frontend/tavern-helper-compat.js:520)。存在这些接口不代表任意 ST 插件都能运行，也不证明 Kotlin 的提示组装会自动执行世界书中的全部 EJS 语法；本次不把复杂模板作为交付前提。

## 5. 规则内容可以怎样拆分

无论最终采用预设还是常驻书，都可以将同一套内容组织为以下几个短条目。这里是待验证的内容提纲，不是已通过真实聊天验收的成品：

| 条目 | 主要约束 |
| --- | --- |
| 线上交流边界 | 当前是即时消息交流；只输出真正发给用户的话，不写姓名冒号、动作、旁白、场景与未说出口的心理活动。 |
| 角色与关系 | 身份、性格和表达习惯来自当前角色；关系依据明确设定及共同历史，不能统一预设为恋爱或亲密关系。 |
| 自然回复 | 回应用户具体内容，允许自己的观点与情绪；不固定安慰开头、不逐句复述、不每轮追问、不无必要长篇说教，也不靠错别字制造自然感。 |
| 信息与行为边界 | 可以表达感受、计划和已知状态；不能通过消息直接执行拥抱、同行等面对面行为，也不能编造位置、日程和经历。 |
| 输出节奏 | 一条完整消息通常足够；确有停顿或补充才分成少量消息。不按每个句号拆分，也不让模型伪造输入状态、已读回执或发送时间。 |
| 输出前自检 | 检查这一轮是否仍有动作或旁白、是否代替用户发言、是否只是给小说正文换上引号；发现冲突时调整为角色会实际发送的话。 |

正例：“我有点担心你”“我在图书馆，刚坐下”（上下文支持该状态时）“要不要约个时间见面？”

反例：“她担忧地望着你”“我伸手把你抱进怀里”“林夏：（摸摸你的头）别难过”“[正在输入……]”。

通用规则不应把热情角色与寡言角色的回复强行统一，也不能把回答所需的时间、数量和世界设定当成“机器味”过滤掉。

## 6. 单靠预设或世界书能做到的边界

预设和世界书主要控制**接下来调用模型生成的内容**。模型自检是提示约束，不等于执行了一次独立程序校验；不能把导入预设成功视为纯消息验收通过。

当前有三点尤其需要在后续验收时单独处理：

1. [ConversationOps.kt:20](/home/xyj/muyang_ws/Momoly/domain/generation/src/main/java/me/rerere/momoly/domain/ConversationOps.kt:20) 直接展示卡片开场白与备选；它们没有经过模型，加入规则书不会自动把原始叙事改成聊天。
2. [PromptBuilder.kt:293](/home/xyj/muyang_ws/Momoly/domain/generation/src/main/java/me/rerere/momoly/domain/PromptBuilder.kt:293) 中的 `forbid_overrides` 可以阻止卡片替换预设的主提示，但角色描述、示例、深度提示和其他书仍可能包含冲突内容；共用世界书不是程序上的最高优先级策略。
3. [ChatGenerationRunner.kt:85](/home/xyj/muyang_ws/Momoly/feature/chat/src/main/java/me/rerere/momoly/ui/chat/ChatGenerationRunner.kt:85) 的流式选择来自角色设置；仅在导入预设中写 `stream_openai: false` 不能保证当前聊天隐藏未检查正文。显示正则也不会自动把历史中的原文全部改为纯消息。

因此本轮调研以可复用的提示内容为主，不再默认引入独立检查模型或完整代理系统。最终仍按阶段 1 的实际输出标准验收；若提示方案不能通过，再针对具体失败入口讨论必要补充，而不是先扩大实现范围。

## 7. 本轮导入改动

[CharacterRepository.kt:208](/home/xyj/muyang_ws/Momoly/data/resources/src/main/java/me/rerere/momoly/data/character/CharacterRepository.kt:208) 新增世界书存在检查，在任何角色资源写入前拒绝缺少可读取 `character_book` 对象的导入。仍识别 `data.character_book` 和根级 `character_book` 两种原有位置。

没有新增书名、条目数量、角色性格或日程字段限制；有世界书但没有书名时继续沿用原有补名行为。需求、阶段计划和导入说明已同步到这个范围。

验证：已检查改动范围和本地文档链接。执行 `:data:resources:testDebugUnitTest` 时，Gradle 在构建配置阶段因缺少 JDK 17 失败（当前系统为 JDK 21），尚未进入编译和测试；不视为测试通过。
