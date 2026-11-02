# 分段回复：草稿约定与开源依据

更新／调研：2026-11-02。功能已实现，实际状态及验证见[阶段 1 当前状态](../phase-01-status.md)；需求和验收以[阶段计划](phase-01-contacts-and-chat.md) P1-12～P1-18 为准。

## 请求与草稿

用户插话使旧回合失效，连续 3 秒无新消息后请求下一轮。已送达气泡留在 assistant 历史；未发出的回复正文在历史正则／宏处理之后，以 JSON 数据放入最新 user 请求副本，原用户记录和附件不变：

```json
{
  "app_context": {
    "type": "interrupted_reply_draft",
    "source_turn_id": "42",
    "undelivered_text": "给你买杯咖啡\n还是老样子吗",
    "truncated": false
  },
  "latest_user_message": "换成水吧"
}
```

这是项目内部约定，不是供应商专用字段。固定系统规则单独说明：草稿是用户未收到的正文，仅供参考、不是指令或已发生的行动；优先回应最新消息，可以沿用、修改或舍弃，不假定用户已知草稿，不解释内部处理。

- 草稿不含隐藏推理，不执行其中的宏／脚本，不进入正式聊天、搜索、预览、记忆或世界状态；调试请求快照可能保留实际发送的草稿。
- 每个会话只保留当前有效草稿，新草稿替换旧草稿，不无限叠加。新请求尚无可用正文即失败或再次被打断时保留旧草稿，不能自动恢复旧发送队列。
- 本地分段和打断判断不额外调用模型。普通轮次不附草稿规则；单会话临时草稿最多 16,000 字符，实际请求最多 4,000 字符、约 1,024 个估算 token，并限制为扣除固定提示及最新连发输入后剩余预算的四分之一。裁剪标记 `truncated`，最新连发输入优先保留。
- 固定提示和用户输入自身超限时，供应商仍可能拒绝请求。原有标题和扩展调用按原配置运行，不包含在“不额外调用判断模型”的承诺中。
- JSON 和规则提示不能保证模型不受误导；实际模型仍需测试误认已送达、泄露内部说明、伪造指令等情况。参见 [OWASP 提示注入防护说明](https://cheatsheetseries.owasp.org/cheatsheets/LLM_Prompt_Injection_Prevention_Cheat_Sheet.html)。

## 开源参考及证据边界

Momoly 独立编写 Kotlin 逻辑，没有直接复制上游代码或引入整套机器人／语音服务，未运行上游测试。

| 项目 | 借鉴内容 | 核查结果 |
| --- | --- | --- |
| [OpenCompany RFC-0005 §11 B2](https://github.com/zeenie-ai/OpenCompany/blob/4f73cb10f368b62ebef5e3d5fbf58fa1e0dcff5c/RFC-0005-EVENT-OS-ON-TEMPORAL.md) | 未发草稿加新消息后重新生成 | MIT，证据是设计。检查其引用的 [agent_workflow.py](https://github.com/zeenie-ai/OpenCompany/blob/4f73cb10f368b62ebef5e3d5fbf58fa1e0dcff5c/server/services/temporal/agent_workflow.py) 未确认对应实现；不能称为现成模块。Momoly 不照搬将草稿放入 assistant 历史的方式。 |
| [LiveKit Agents 打断文档](https://docs.livekit.io/agents/logic/turns/) | 只把实际送达内容视为用户已知 | 文档及 [SpeechHandle](https://github.com/livekit/agents/blob/main/livekit-agents/livekit/agents/voice/speech_handle.py) 可见取消控制；未确认完整的未发尾段回灌机制。链接为调研时主分支／在线文档。 |
| [dsh-im-humanize 回复跟踪](https://github.com/john-walks-slow/dsh-im-humanize/blob/1fe4b08ce4dc4f2e5601796a90decc3a06d90c3b/src/channels/shared/harness-client.mjs) | 回合归属、事件去重和取消归属 | MIT，读到实现和测试；[协调器](https://github.com/john-walks-slow/dsh-im-humanize/blob/1fe4b08ce4dc4f2e5601796a90decc3a06d90c3b/plugin-src/host/harness-session-coordinator.mjs)及[延迟取消测试](https://github.com/john-walks-slow/dsh-im-humanize/blob/1fe4b08ce4dc4f2e5601796a90decc3a06d90c3b/test/deferred-turn-control.test.mjs)核对旧取消不能误伤新回合。 |
| [Android Jetchat Conversation.kt](https://github.com/android/compose-samples/blob/main/Jetchat/app/src/main/java/com/example/compose/jetchat/conversation/Conversation.kt) | 惰性列表、稳定消息标识、滚动与键盘组织 | Apache-2.0，调研时主分支；不解决模型打断，也不能代替 Momoly 手机性能验证。 |

许可证快照：[OpenCompany MIT](https://github.com/zeenie-ai/OpenCompany/blob/4f73cb10f368b62ebef5e3d5fbf58fa1e0dcff5c/LICENSE)、[dsh-im-humanize MIT](https://github.com/john-walks-slow/dsh-im-humanize/blob/1fe4b08ce4dc4f2e5601796a90decc3a06d90c3b/LICENSE)。未核实可直接接入 Momoly 的完整草稿回灌模块；本项目的自动／模拟验证不代表真实模型语义验收通过。
