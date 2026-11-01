# 默认线上聊天预设与常驻规则世界书

更新日期：2026-11-01。

## 1. 已添加的资源

| 资源 | 内容 | 使用方式 |
| --- | --- | --- |
| [默认聊天预设](/home/xyj/muyang_ws/Momoly/data/resources/src/main/assets/defaults/online-chat-preset.json) | 角色身份、世界设定与历史的提示顺序；线上消息边界；发送前自检 | 首次创建默认预设时加载；没有关联预设的角色自动使用它。 |
| [全角色常驻规则世界书](/home/xyj/muyang_ws/Momoly/data/resources/src/main/assets/defaults/online-chat-worldbook.json) | 中文自然回应、角色差异、关系分寸、事实边界、消息节奏和正反例 | 每个角色的聊天上下文自动加载同一份，不需要逐角色勾选。 |

两份 JSON 均按 SillyTavern 格式组织，也可以单独导入酒馆。预设负责总体编排，世界书补充具体交流习惯；没有引入外部插件运行时、额外检查模型或生成后删括号的正则。

## 2. 在 APP 中的行为

- “预设”页面的内置默认预设包含新的线上规则。旧版本只含资源 ID 的空白默认预设会被填充，原 ID 保留，角色关联不会断开。已有自定义内容或参数的默认预设不覆盖；需要使用新版本内容时，可另行导入上述 JSON。
- “世界书”页面会出现“Momoly 线上聊天规则（全角色）”。共用书与角色自己的世界书一起加载，但不会自动写入角色的世界书关联列表，也不参与角色所属世界的归类。
- 同一份共用书只加载一次；改名后仍按固定 ID 找到它，编辑内容后也不会在下次启动时被重置。
- 内置共用书与默认预设一样保留为基础资源，不提供整本删除入口；可以通过世界书编辑器修改内容或关闭其中的常驻条目。
- 新建、导入角色沿用原有默认预设关联入口；已有未关联预设的角色在加载上下文时使用默认预设。已选择其他有效预设的角色保留选择，同时加载共用规则书。

共用书使用一条完整的常驻规则，激活概率为 100%，没有关键词、延迟或冷却限制，按 `system` 深度 0 注入。使用一条规则是为了减少预算裁剪时只剩半套要求的情况；它仍遵守应用原有的世界书预算设置。

## 3. 内容取舍与来源

这是根据产品要求重新编写的中文规则，没有整套移植上游人设、代码或固定身份。

- [MaiBot 人设协议](https://github.com/Mai-with-u/MaiBot/blob/c6429547af711555e3d4d1b8d04b413572cb616c/docs/personality-import.md) 与 [回复模板](https://github.com/Mai-with-u/MaiBot/blob/c6429547af711555e3d4d1b8d04b413572cb616c/prompts/zh-CN/maisaka_replyer.prompt)：借鉴人格与表达风格分开、承接具体对话、中文口语与减少机械追问的思路，不采用固定论坛口吻或刻意错字。
- [EchoText](https://github.com/mattjaybe/SillyTavern-EchoText/blob/35a7940f77691807be08c4e2e153118341a90c6e/lib/config.js)：借鉴角色私聊定位和清楚的身份、上下文组织；不覆盖角色原本性格。
- [RPG-Phone](https://github.com/tavern-rpg-suite/RPG-Phone/blob/5d3df0d5bf30b3e6619c5b7190bf2d8c4473df01/index.js)：借鉴短信正文、少量自然消息与禁止旁白；不使用姓名前缀、固定恋爱主题、跨世界手机或模拟媒体事件。
- [Sudachi](https://github.com/LimeBlogs/Sudachi-Next/blob/c62174f6ab6ca2a38115956e1d70a8c337c26f12/Sudachi-Next-Preset.json)：借鉴中文聊天与反全知方向；不引入动作、内心描写或禁止具体数字的文风规则。
- [AIRI 消息分段](https://github.com/moeru-ai/airi/blob/9e7917d9c660f4e8e05d3c04a5f71fe316c39b49/integrations/telegram-bot/src/prompts/message-split-v1.velin.md)：借鉴完整意思优先、仅在自然停顿时拆分的原则。

## 4. 目前的边界

规则已经接入上下文，但提示约束本身不能保证模型每次都无旁白。角色卡中直接展示的开场白及备选不会因此自动改写；流式发布和生成结果的程序检查也不在这次资源接入改动内。

默认预设锁定主提示和尾部提示，默认关闭角色示例，减少原有叙事格式的影响；用户仍可以在编辑器中调整。角色描述、深度提示及其他预设若含冲突指令，仍需用真实模型检验效果。

后续质量验收继续使用热情、寡言两种角色，各不少于 10 轮连续对话，并覆盖邀请见面、带标记与无标记动作、情绪自述和已知状态自述。本次尚未完成真实模型质量验收。

## 5. 已完成的验证

- `:data:resources:testDebugUnitTest`：30 项通过，包含新增的 4 项默认资源测试。覆盖初始化和 ID 保留、自定义内容保留、全角色加载、关联去重、改名后识别、同名资源保护，以及使用真实提示组装器检查角色主提示与示例冲突。
- `:domain:generation:test`：51 项通过。
- 两份 JSON 的语法、提示顺序引用、常驻属性和基础宏检查通过，`git diff --check` 通过。

测试按项目的 SDK 37 编译，Robolectric 4.16.1 运行在其支持的 API 36。没有进行设备安装或真实模型多轮聊天验收。
