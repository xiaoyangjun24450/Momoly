"""Annotate the saved example without changing its payloads or calling a service."""
from pathlib import Path
import hashlib
import html
import json

HERE = Path(__file__).resolve().parent
esc = html.escape
spec = json.loads((HERE / 'assumptions.json').read_text(encoding='utf-8'))
KINDS = {
    'card': ('角色卡', '人物是谁', '描述、性格、初始关系与情景、开场白；以及 Momoly 专用的初始生活配置。'),
    'book': ('世界书', '环境与资料', '世界背景、地点、路线和气候配置，也能装聊天规则；按条目规则选取。'),
    'state': ('运行状态', '现在怎么样', '当前时间、位置、活动、日程、心情、好感和等待情况，随运行改变。'),
    'history': ('聊天记录', '实际说过什么', '已保存的用户和角色原话；开场白写入会话后也属于聊天历史。'),
    'preset': ('预设', '怎样回复', '指导人物扮演和输出风格的提示词模板，不是人物经历。'),
    'user': ('用户资料', '你在世界里是谁', '当前世界的用户名与人设描述；与角色资料分别保存。'),
    'program': ('程序规则 / 配置', '本次任务怎么执行', '请求接口、参数、状态记录规则、字段格式与读写逻辑；不属于人物设定。'),
    'candidate': ('候选回复', '准备说但尚未发出', '模型已生成但还未发布的文字；当前不能当作已经送达的聊天记录。'),
}

def badge(key):
    return f'<span class="badge {key}">{KINDS[key][0]}</span>'

def badges(keys):
    return ' '.join(badge(k) for k in keys)

def table(headers, rows):
    return '<div class="table-wrap"><table><thead><tr>' + ''.join(f'<th scope="col">{esc(h)}</th>' for h in headers) + '</tr></thead><tbody>' + ''.join('<tr>' + ''.join(f'<td>{cell}</td>' for cell in row) + '</tr>' for row in rows) + '</tbody></table></div>'

def code(value):
    text = value if isinstance(value, str) else json.dumps(value, ensure_ascii=False, indent=2)
    return '<pre><code>' + esc(text) + '</code></pre>'

def main_metadata(payload):
    before = [
        ('预设：梦鲸底色', ['preset'], '来自内置预设的固定文字。它不是林夏的身份或身世。'),
        ('预设：线上聊天与身份规则', ['preset','card','user'], '规则正文来自预设；其中“林夏”和“小陈”由角色名与用户名替换进去。'),
        ('小陈的用户人设', ['user'], '来自本例的当前世界用户资料，不是林夏角色卡。'),
        ('林夏的身份与背景', ['card'], '角色卡 description：人物是谁、基本背景。'),
        ('林夏的性格', ['card'], '角色卡 personality：热情直率等长期人物特点。'),
        ('关系与聊天情景', ['card'], '角色卡 scenario：两人是熟人、手机私聊；不是程序推断出的当前关系数值。'),
        ('现代城市的环境资料', ['book'], '本例关联世界书的公共文字条目；不是地图 JSON 原样放入主请求。'),
    ]
    history = [m for m in payload['messages'] if m['role'] != 'system']
    for i, message in enumerate(history):
        if i == 0:
            before.append(('开场白的历史副本', ['card','history','state'], '原话最初来自角色卡 first_mes；创建会话时存为 assistant 历史。游戏时间前缀来自消息时间记录，不是卡里的原文。'))
        elif message['role'] == 'user':
            before.append(('你已经发出的消息', ['history','state'], '正文是用户实际输入；时间前缀由 App 的游戏时间记录添加。消息身份 user 不表示这条内容属于“用户人设资料”。'))
        else:
            before.append(('角色已经发出的回复', ['history','state'], '之前模型生成并实际发布的正文；时间前缀由 App 添加。它不是角色卡新增的一段设定。'))
    before += [
        ('共用世界书：线上消息规则', ['book','card','user'], '原文来自全角色共用世界书，虽写的是聊天规则，存放来源仍是世界书。角色名与用户名由宏替换。'),
        ('预设：承接本轮', ['preset'], '来自预设，规定如何接话、如何保持人物意愿。'),
        ('预设：发送前边界', ['preset','card'], '来自预设；角色名替换后要求只输出林夏实际发送的话。'),
        ('当前世界与关系简述', ['state','book','program'], '状态提供时间、当前位置、心情、好感和等待结果；世界地图把地点编号转换为名称。程序负责拼文字与追加限制。'),
        ('生活提示 lifePrompt', ['state','book','program'], '这是混合消息，不是一整块角色卡。状态、世界地点和程序规则拼在一起；下面逐项拆开。'),
    ]
    assert len(before) == len(payload['messages'])
    return before

def life_breakdown():
    return table(['生活提示中的部分', '来源', '本例含义'], [
        ['世界名、日期时间', badges(['state']), '世界运行记录；第一轮 20:00，第二轮 20:01。'],
        ['地点与天气', badges(['state','book','program']), '角色当前 location 是 home；地图把 home 解释为林夏家。气候候选来自地图，程序按时间计算本次天气；本例候选只有晴。'],
        ['活动、处境', badges(['state']), '当前居家休息；背景短句说明尚未约定吃饭的具体时间地点。日程与移动不再阻挡或截短回复。'],
        ['好感、心情、焦虑、烦躁', badges(['state']), '当前为 30、平静/50、0、0；这些是本次快照，不代表所有角色都固定这样。'],
        ['耐心与情绪反应参数', badges(['card','state']), '本例初始配置来自角色卡：耐心 360 分钟，两项倍率均为 1.0。运行状态保留这份配置；不是每轮重新从聊天猜。'],
        ['等待状态、最后回复、最近变化', badges(['state','history','program']), '程序按实际收到/送达的消息推进。你刚回复，先前等待结束；模型只能看到本次摘要。'],
        ['日程', badges(['card','state']), '本例初始卡给出每日居家安排，运行时复制到当前日程。后续已确认约定可更新当前日程，不自动改原卡模板。'],
        ['私有未发送草稿', badges(['state','candidate']), '本例为空。若有，最多取 3000 字符；没发出去，不能当用户已知道。'],
        ['当前可变事实', badges(['book','state']), '本例为空。实际运行时条目权限来自世界书，内容取当前运行值；不是任意新增记忆。'],
        ['地点 ID 与名称列表', badges(['book']), 'home=林夏家、restaurant=街角餐厅；这只是可用地点，不代表双方约好了去哪。'],
        ['回复任务、JSON 要求、证据与修改限制', badges(['program']), '程序临时加入：用户留言必须正常回复，不因忙碌延后或限制长度；返回正文和状态建议、不能把猜测当事实。不是角色自己的台词。'],
    ])

FIELD_META = {
    'gameTime': (['state'], '当前游戏时间；不是从用户“明天”两个字直接得到的完整日期。'),
    'character': (['card'], '角色卡的 description。'),
    'personality': (['card'], '角色卡的 personality。'),
    'current': (['state'], '只包含当前处境 background、位置编号 location 和当前日程 schedule；本示例不是完整角色存档。'),
    'relationshipSummary': (['state','program'], '程序把当前心情、好感、等待、反应参数等拼成摘要；不是聊天内容的自动总结。'),
    'responseProfileUninitialized': (['state'], 'false 表示反应参数已初始化，本例由卡提供；不应让模型再次随意改写。'),
    'map': (['book','state'], '来自世界书地图、保存在运行世界中；此请求发送完整地图对象。字段含地点坐标、路线分钟数、地图用户位置配置和气候候选，不是聊天历史。'),
    'mutableFacts': (['book','state'], '仅该角色私有、可修改、无程序绑定的事实条目；本例为 []。'),
    'userMessages': (['history'], '只含本次尚未答复的用户正文。第一轮是“有空”，第二轮是“好”，并非完整聊天。'),
    'candidateReply': (['candidate'], '正在审核、尚未发布的回复。整理成功后才发送并进入正式聊天历史。'),
}

def render_packet(filename, title, packet_id, expanded=False):
    payload = json.loads((HERE / filename).read_text(encoding='utf-8'))
    assessment = 'state' in filename
    if assessment:
        meta = [('状态整理指令', ['program'], '独立的记账规则：提取状态变化、不扮演角色、不续写聊天；不是原聊天预设。'),
                ('待整理的数据包', ['card','book','state','history','candidate'], '这里的 user 只是 API 消息身份，不表示里面所有内容都是用户说的话。它是程序打包的 JSON 字符串；按字段逐项拆解如下。')]
    else:
        meta = main_metadata(payload)
    rows = []
    cards = []
    for i, (message, (name, keys, explanation)) in enumerate(zip(payload['messages'], meta), 1):
        anchor = f'{packet_id}-m{i}'
        rows.append([f'<a href="#{anchor}">{i:02d} · {esc(name)}</a>', f'<code>{esc(message["role"])}</code>', badges(keys)])
        detail = ''
        if assessment and i == 2:
            data = json.loads(message['content'])
            assert set(data) == set(FIELD_META)
            field_rows = []
            for key, value in data.items():
                kinds, why = FIELD_META[key]
                field_rows.append([f'<code>{esc(key)}</code>', badges(kinds) + '<p>' + esc(why) + '</p>', code(value)])
            detail = table(['字段', '来源与解释', '实际值（完整）'], field_rows)
        elif not assessment and i == len(meta):
            detail = life_breakdown()
        cards.append(f'<article class="message" id="{anchor}"><h3>{i:02d} · {esc(name)}</h3><div>{badges(keys)} <code class="role">API 身份：{esc(message["role"])}</code></div><p>{esc(explanation)}</p>{detail}<details class="raw"><summary>展开这条消息的完整原文</summary>{code(message["content"])}</details></article>')
    parameters = {k:v for k,v in payload.items() if k != 'messages'}
    return f'<details class="packet" id="{packet_id}" {"open" if expanded else ""}><summary>{esc(title)} <span class="count">{len(meta)} 条 API 消息</span></summary><p>以下来源标签只用于解释，不会追加到请求包。<a href="request-example/{filename}">完整 JSON 文件</a></p><details class="raw"><summary>查看请求参数（不含消息数组）</summary>{code(parameters)}</details>{table(["顺序 / 消息", "API 身份", "资料来源"], rows)}{"".join(cards)}<details class="raw"><summary>查看完整请求体 JSON</summary>{code(payload)}</details></details>'

overview = ''.join(f'<article class="kind" id="kind-{key}">{badge(key)}<h3>{esc(title)}</h3><p>{esc(description)}</p></article>' for key, (_, title, description) in KINDS.items())
initial_table = table(['同一信息', '初始来自哪里', '本次从哪里发送'], [
    ['开场白', badges(['card']) + ' first_mes：“明天你有空吗？”', badges(['history']) + ' 建会话时复制成第一条消息；时间前缀另取状态。'],
    ['林夏现在在家', badges(['card']) + ' 初始位置 home；' + badges(['book']) + ' 定义 home 的名称', badges(['state']) + ' 当前 location，再用地图查出“林夏家”。'],
    ['日程、心情、好感', badges(['card']) + ' 初始安排与初始数值', badges(['state']) + ' 取当前保存值。可能与导入时不同，不直接覆写角色卡。'],
    ['天气', badges(['book']) + ' 地图中的气候候选', badges(['program','state']) + ' 按地区和当前时间计算；本例天气只能为晴。'],
    ['动态世界事实', badges(['book']) + ' 条目内容、可变标记与知情范围', badges(['state','book']) + ' 运行值更新后可投影回世界书；本例没有此类私有条目。'],
    ['人物反应参数', badges(['card']) + ' 本例预先提供耐心等参数', badges(['state']) + ' 当前保留值和“已初始化”标记；其他卡可能由模型首次提出。'],
])
timeline = table(['这一步', '实际归属 / 变化', '服务器请求'], [
    ['开场白：明天你有空吗？', '角色卡原文 → 创建会话 → 聊天记录', '0 次，读取固定开场白'],
    ['你：有空', '保存用户历史；状态记为用户已回复', '主请求 01；若只返纯文字，再加整理 01b'],
    ['角色：来找我吃饭', '先是候选回复；实际发出后才是历史。若带 expectsReply 和有效片段，状态开始等待', '发布和保存本身不再调用模型'],
    ['你：好', '新增历史，结束上一轮等待；不等于已经确认尚未提出的“明天中午”', '主请求 02；若只返纯文字，再加整理 02b'],
    ['角色：那就定明天中午？', '新消息仍是询问；可进入等待状态，不应当成已确认的具体日程', '这句话来自上一步的生成，不额外再请求'],
])
network_table = table(['字段 / 内容', '归属', '作用'], [
    ['服务地址、model、Authorization', badges(['program']), '用户配置的模型服务与鉴权；不属于角色卡或世界书。示例使用假值。'],
    ['temperature / max_tokens', badges(['preset','program']), '主请求来自预设（0.9 / 8192）；整理请求由程序指定（0.1 / 2048）。'],
    ['stream / stream_options', badges(['program']), '网络层要求分段返回内容与用量；不是人物记忆。'],
    ['messages / role / content', badges(['program']), 'API 包装。system 只表示系统消息身份，不能据此判断内容来自角色卡、世界书还是状态。'],
])
packets = ''.join([
    render_packet('01-main-after-free.json', '01｜你说“有空”后的主请求', 'request-01', True),
    render_packet('01b-state-if-plain.json', '01b｜仅在第一轮返回纯文字时：状态整理', 'request-01b'),
    render_packet('02-main-after-ok.json', '02｜你说“好”后的主请求', 'request-02'),
    render_packet('02b-state-if-plain.json', '02b｜仅在第二轮返回纯文字时：状态整理', 'request-02b'),
])
reference = '''
<h2 id="formats">示例以外：其他请求格式</h2>
<p>上面的四份 JSON 演示角色生活分支。业务分支决定带哪些资料，供应商适配器决定最终 HTTP 字段。下面保留其他分支的速查。</p>
<details><summary>普通聊天首轮与角色生活主请求的差异</summary>
<div class="table-wrap"><table><thead><tr><th>内容</th><th>加入条件与限制</th></tr></thead><tbody>
<tr><td>普通首轮</td><td>ChatGenerationRunner：关联预设，未关联则用内置线上聊天预设；按开关加入用户人设、角色描述/性格/情景。默认关闭角色对话示例，不发送整个角色 JSON。</td></tr>
<tr><td>世界书与历史</td><td>共用规则书和关联世界书经过常驻/关键词、概率、互斥、递归和容量筛选，默认扫描最近 2 条消息。历史取当前会话未隐藏的有效消息，含已存开场白、最新输入和已发布回复。</td></tr>
<tr><td>扩展与顺序</td><td>已启用的 PromptContributor 可在历史前后或指定位置插入资料，也可要求跳过部分历史。通常是历史前资料 → 历史及插入项 → 历史后资料，预设可调整；无内置摘要注入。</td></tr>
<tr><td>世界状态</td><td>有世界状态时追加时间、位置/移动、天气、活动、处境及当前关系/等待摘要。普通首轮不附 lifePrompt 状态输出协议；启用搜索时可附应用函数工具。</td></tr>
<tr><td>角色生活分支</td><td>WorldLifeRuntime.respond：回复、主动联系、离线补齐共用基础组装器；过滤地图条目和其他角色私有事实，已识别事实优先角色私有版本、其次公共版本，并以当前内容替换旧内容。历史可加游戏时间，末尾另加 lifePrompt；不伪造用户消息触发主动联系。</td></tr>
<tr><td>生活说明</td><td>列出时间、位置/移动与预计到达时间、天气、活动、处境、心情/强度、好感、全部当前日程、地点 ID/名称和本轮任务；日程不含 availability 通信状态。待答时必须正常回复，不因忙碌延后或截成一段、240 字符；主动联系仍可返回 messages=[]，离线补齐取当时游戏时间。草稿取 actor.draft 前 3000 字符；列角色私有可变事实，绑定条目即使列出，聊天写入仍拒绝修改。</td></tr>
<tr><td>关系/等待摘要</td><td>只取当前角色：好感、心情/强度、阶段、焦虑/烦躁、耐心/敏感度/恢复速度、定性联系意愿；等待消息前 400 字符、送达/预期回复时间、等待/超期分钟、未回应主动联系次数、最后用户回复时间、已知离开原因/返回时间及证据、最近变化与时间。不附全量日志、内部消息 ID 或规则版本。</td></tr>
<tr><td>生活参数与输出</td><td>预设参数、思考档位 AUTO，应用函数工具为空；供应商内置工具看模型配置。提示文字要求 LifeTurn JSON，含正文及可选状态/日程/事实建议、证据、expectsReply/replyEvidence、首次 responseProfile、有用户证据的 absence、有限的 anxietyDelta/irritationDelta。禁止重复扣冷落损失；无初始日程且自主安排时要求提出每日作息。默认没有强制 JSON schema 参数；返回格式不是上行包格式。</td></tr>
</tbody></table></div></details>
<details><summary>工具续答、内嵌前端和扩展：各自如何组包</summary>
<div class="table-wrap"><table><thead><tr><th>分支</th><th>消息与参数</th><th>限制</th></tr></thead><tbody>
<tr><td>普通聊天工具续答<br>GenerationEngine</td><td>保留首轮全部消息和此前工具轮，追加本轮角色正文、工具调用 ID/名称/参数、结果或错误，必要时带供应商原始块/签名。App 内部挂在 assistant 消息上，再由网络层编码。</td><td>保留已选模型、参数、工具定义，不重新加载人物资料或裁剪，不尝试备用模型；工具结果会增加包长。角色生活不走此循环。</td></tr>
<tr><td>内嵌前端 messages 格式</td><td><code>{"messages":[{"role":"user","content":"你好"}]}</code>。数组优先；content 空时取 message，跳过空正文。只接受 system/user/assistant，其他 role 改 user。</td><td rowspan="2">这是页面给 App 的入参，不是最终 HTTP。仅文本；不自动补卡、世界书、历史、状态、图片或裁剪。应用函数工具为空；generation_id 仅本地事件与取消。未指定 model 时取当前模型，参数优先内层 params，否则外层。</td></tr>
<tr><td>内嵌前端 prompt 格式</td><td><code>{"prompt":"你好"}</code>。仅没有 messages 数组时，把非空 prompt 转成一条 user。空数组不回退。</td></tr>
<tr><td>扩展自行调用<br>HostServices.callModel</td><td>扩展提供 messages: List&lt;ApiMessage&gt;、params、modelId，直接交给适配器。未指定模型则用当前模型；空参数仍可能补协议默认项。</td><td>不自动补角色或历史，不经过通用裁剪、备用模型网关，不提供应用函数工具。不同于往现成请求里贡献内容。</td></tr>
</tbody></table></div></details>
<details><summary>引用、附件、草稿：消息内部可能是什么格式</summary>
<p>以下通用处理适用于普通首轮和角色生活基础组装；不能直接套用到状态整理、内嵌前端和扩展自行调用。</p>
<div class="table-wrap"><table><thead><tr><th>条件</th><th>内容</th></tr></thead><tbody>
<tr><td>普通文字</td><td>发送侧正则替换和宏处理后的 content；不自动发送整张变量表。</td></tr>
<tr><td>引用</td><td>content 是 JSON 字符串：app_context.quoted_message + latest_user_message。引用含作者、角色、时间戳和最多 1000 Unicode 字符的快照。</td></tr>
<tr><td>普通中断草稿</td><td>最新 user 的 JSON 增加 app_context.type=interrupted_reply_draft、source_turn_id、undelivered_text、truncated，可同时带引用。草稿最多 4000 字符，另受预算限制；再加“未发送、不算事实”系统说明。生活草稿另按 3000 字符文本处理。</td></tr>
<tr><td>文本文件 / 图片</td><td>文件在正文前加入 &lt;file name="…"&gt;…&lt;/file&gt;，每个可读文件最多 10 万字符。图片携带 MIME 和 base64，由网络层转图片块，不是只发本地路径。</td></tr>
</tbody></table></div></details>
<h2 id="容量与记忆边界">容量与记忆边界</h2>
<details><summary>历史会不会全部发送？保存的内容都算记忆吗？</summary>
<ul><li>token 是模型计算文本长度的单位。内置预设总预算 80,000，回复预留 8,192，输入约 71,808 再扣固定资料；自选预设可改，无有效上限时不自动裁剪。</li>
<li>超预算主要去掉较早历史，优先保留最新连续用户输入，不删除本地记录。固定资料、超长输入可能超额；生活说明在裁剪后追加，所以不是严格最终上限，也不保证服务支持该容量。</li>
<li>当前没有内置滚动摘要或旧聊天语义检索。独立线上线下事件记忆尚未接通。主回复没有专门注入 initial.history；另写在角色描述或命中的世界书中才随它们发送。</li>
<li>旧 extState["builtin.summarize"] 可以仍存盘，但基础组装器不再读取、也不据此跳过历史；{{summary}} 不再取得旧摘要。其他已启用扩展仍可贡献内容或要求跳过历史。</li>
<li>候选状态须经 applyLifeTurn 校验；有正文的回复在最后一段实际发布时应用。模型提出可修改，不代表程序接受修改。示例中的状态只是所选情境，不能当成模型一定会得出的结果。</li></ul></details>
<h2 id="protocols">最终发给服务商的四种 HTTP 格式</h2>
<details><summary>同一份上下文，在不同接口中如何摆放</summary>
<p>这是本仓库的文字聊天适配范围。鉴权在请求头，自定义请求头/请求体可能改变结果；示例仅使用假密钥。服务地址和接口路径取决于配置，不只由模型名字决定。</p>
<div class="table-wrap"><table><thead><tr><th>协议</th><th>请求体</th><th>消息 / 图片 / 工具</th></tr></thead><tbody>
<tr><td>OpenAI 兼容 Chat Completions</td><td>model、messages、stream、参数、可选 tools</td><td>role/content；图片为 text/image_url 块，data URL；工具为 assistant.tool_calls 和 role=tool 结果。</td></tr>
<tr><td>OpenAI Responses</td><td>model、instructions、input、stream、store=false、参数/工具</td><td>所有位置 system 合并到 instructions，其余进 input；图片 input_image；工具 function_call / function_call_output，可保留原始块。</td></tr>
<tr><td>Claude Messages</td><td>model、system、messages、max_tokens、stream、参数/工具</td><td>开头连续 system 提到顶层，后面 system 转 user，相邻同角色合并，去掉开头非 user；图片 image/source/base64；工具 assistant.tool_use + user.tool_result。</td></tr>
<tr><td>Google Gemini</td><td>systemInstruction、contents、generationConfig、可选 tools；模型在 URL</td><td>开头连续 system 提到顶层，后面 system 转 user，相邻同角色合并，assistant 转 model；图片 inline_data；工具 model.functionCall + user.functionResponse。</td></tr>
</tbody></table></div><p>因此末尾 lifePrompt 在 Chat Completions 中仍是 system，在 Responses 中合入 instructions，在 Claude/Gemini 中转成 user。业务消息数组不能代替最终 HTTP 抓包。</p></details>
<h2 id="maintenance">开发者如何维护这份示例</h2>
<p>需要 Python 3，仅使用标准库。在仓库根目录执行：</p>
<pre>python -X utf8 docs/request-example/generate.py</pre>
<p>此命令读取当前源码模板，重建五份 JSON 和本页，不联网。<a href="request-example/generate.py">generate.py</a> 管情境和组包，<a href="request-example/render_annotated.py">render_annotated.py</a> 管来源解释、其他格式与页面；<a href="request-example/assumptions.json">assumptions.json</a> 记录假设及源码哈希。只改解释时可运行：</p>
<pre>python -X utf8 docs/request-example/render_annotated.py</pre>
<p>生成脚本并不运行 Android 组装器。新增字段、顺序或格式需人工同步脚本和说明，再重新生成并检查，不要只改生成后的 HTML。核对日期也需同步。触发条件维护在<a href="请求情况.md">请求情况</a>。</p>
'''
source_links = {
    '资源加载': 'data/resources/src/main/java/me/rerere/momoly/data/PromptContextLoader.kt',
    'ChatPromptAssembler': 'feature/chat/src/main/java/me/rerere/momoly/ui/chat/ChatPromptAssembler.kt',
    'PromptBuilder': 'domain/generation/src/main/java/me/rerere/momoly/domain/PromptBuilder.kt',
    'PromptMessageAssembler': 'domain/generation/src/main/java/me/rerere/momoly/domain/PromptMessageAssembler.kt',
    'WorldLifeRuntime': 'app/src/main/java/me/rerere/momoly/ui/world/WorldLifeRuntime.kt',
    'applyLifeTurn': 'core/model/src/main/java/me/rerere/momoly/data/world/LifeTurn.kt',
    'GenerationEngine': 'domain/generation/src/main/java/me/rerere/momoly/domain/GenerationEngine.kt',
    '前端桥接': 'feature/chat/src/main/java/me/rerere/momoly/ui/chat/ChatFrontendGenerationController.kt',
    'HostServices': 'app/src/main/java/me/rerere/momoly/extension/HostServices.kt',
    **{name: f'core/network/src/main/java/me/rerere/momoly/data/api/{name}.kt'
       for name in ['ChatApi', 'OpenAiAdapter', 'OpenAiResponsesAdapter', 'ClaudeAdapter', 'GoogleAdapter']},
}
reference += '<details><summary>代码入口</summary><p>' + ' · '.join(
    f'<a href="../{path}">{esc(name)}</a>' for name, path in source_links.items()) + '</p></details>'

css = '''
:root{color-scheme:light;--ink:#1c2b39;--muted:#526374;--line:#d7e1e8}*{box-sizing:border-box}body{margin:0;background:#f4f6f8;color:var(--ink);font:16px/1.75 system-ui,-apple-system,"Segoe UI",sans-serif}main{max-width:1140px;margin:auto;padding:42px 28px 80px}h1{font-size:clamp(27px,4vw,40px);line-height:1.3;margin:12px 0}h2{font-size:25px;margin-top:42px}h3{font-size:18px;margin:8px 0}p{margin:10px 0}a{color:#175ba6;text-underline-offset:3px}nav{display:flex;gap:10px;flex-wrap:wrap;margin:24px 0}nav a{padding:7px 14px;background:#fff;border:1px solid var(--line);border-radius:8px;text-decoration:none}.eyebrow{letter-spacing:.1em;font-size:12px;color:var(--muted)}.lead{font-size:20px;max-width:860px}.note{border-left:4px solid #2f6ca9;padding:14px 18px;background:#eaf2fb;margin:20px 0}.warn{border-color:#b07821;background:#fff5df}.kinds{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px}.kind{background:white;border:1px solid var(--line);border-radius:12px;padding:16px}.kind p{font-size:14px;color:var(--muted)}.badge{display:inline-block;white-space:nowrap;border:1px solid transparent;border-radius:5px;padding:2px 8px;margin:2px 4px 2px 0;font-size:12px;font-weight:650}.card{color:#69328b;background:#f0e7f7}.book{color:#225c49;background:#e5f2eb}.state{color:#8a531a;background:#fff0d7}.history{color:#245e91;background:#e5effb}.preset{color:#884158;background:#f9e8ef}.user{color:#34616b;background:#e6f2f4}.program{color:#4d5866;background:#e9edf1}.candidate{color:#995030;background:#fce9df}.table-wrap{max-width:100%;overflow-x:auto;background:white;border:1px solid var(--line);border-radius:10px;margin:18px 0}table{width:100%;border-collapse:collapse;font-size:14px}th{text-align:left;color:#425367;background:#edf2f6}td,th{padding:13px 16px;vertical-align:top;border-bottom:1px solid var(--line);overflow-wrap:anywhere}tbody tr:last-child td{border-bottom:0}pre{white-space:pre-wrap;overflow-wrap:anywhere;word-break:break-word;background:#f0f4f7;border-radius:8px;padding:16px;font:13px/1.8 Consolas,monospace;margin:12px 0}td pre{min-width:230px;max-height:none;margin:8px 0;padding:10px}code{font:13px/1.7 Consolas,monospace;overflow-wrap:anywhere}.packet{margin:20px 0;border:1px solid #b7c9d9;border-radius:12px;background:white;padding:20px;scroll-margin-top:20px}summary{cursor:pointer;font-weight:650;line-height:1.6}.packet>summary{font-size:20px}.count{font-size:13px;color:var(--muted);font-weight:400;margin-left:10px}.message{margin:24px 0;border-top:1px solid var(--line);padding-top:20px;scroll-margin-top:20px}.role{color:var(--muted);display:inline-block}.raw{margin:12px 0}.raw>summary{font-size:14px;color:#325d83}.footer{font-size:13px;color:var(--muted);margin-top:30px}button{font:inherit;background:white;color:#23496c;border:1px solid #b7c9d9;border-radius:7px;padding:8px 14px;cursor:pointer}button:focus-visible,a:focus-visible,summary:focus-visible{outline:3px solid #2d76c5;outline-offset:3px}.toolbar{display:flex;gap:10px;flex-wrap:wrap}@media(max-width:760px){main{padding:24px 16px 50px}.kinds{grid-template-columns:repeat(2,minmax(0,1fr))}.packet{padding:14px}td,th{padding:10px}td pre{min-width:180px}.lead{font-size:18px}}@media(max-width:430px){.kinds{grid-template-columns:1fr}h2{font-size:22px}}
'''
page = f'''<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>请求包内容｜Momoly</title><style>{css}</style></head><body><main>
<div class="eyebrow">MOMOLY · 请求包内容 · 更新 2026-11-08 · 示例日期 2026-11-07</div><h1>这句话背后，模型收到了哪些资料？</h1>
<p class="lead">角色卡告诉模型“这个人是谁”，世界书提供环境与资料，运行状态告诉模型“此刻怎么样”。请求里还会放入预设、聊天记录、用户资料和程序规则。</p>
<div class="note"><strong>来源标签不是 API 身份。</strong>一条 <code>system</code> 消息既可能来自角色卡，也可能来自世界书或状态；一条 <code>user</code> 消息也可能是程序打包的数据，不能把它一律理解为用户原话。</div>
<nav aria-label="页面导航"><a href="README.md">文档索引</a><a href="请求情况.md">何时发请求</a><a href="#formats">其他格式</a><a href="#容量与记忆边界">容量与记忆</a><a href="#maintenance">维护方法</a><a href="#sources">资料分类</a><a href="#initial">初始与当前</a><a href="#timeline">对话过程</a><a href="#request-01">第一轮</a><a href="#request-01b">第一轮整理</a><a href="#request-02">第二轮</a><a href="#request-02b">第二轮整理</a></nav>
<h2 id="sources">先分清八种来源</h2><div class="kinds">{overview}</div>
<h2 id="initial">同一件事，初始设定和当前状态放在不同地方</h2><p>角色卡与世界书中的设定可以被复制进运行存档。发送时要看实际读取的是哪一份，不能只凭内容像“人物资料”就算作角色卡。</p>{initial_table}
<div class="note"><strong>两个容易混淆的例子：</strong>“林夏热情直率”是角色卡；“林夏现在有点烦躁”是状态。“只发聊天正文”既写在当前内置预设，也写在共用世界书里；相似的规则不代表它们来自同一个文件。</div>
<h2 id="timeline">把你的五句话逐步对上</h2>{timeline}<p><strong>正常共 2 次请求；一轮补整理为 3 次；两轮都补整理为 4 次。</strong>本例排除额外主动联系、工具调用和失败重试。每次都重新带所需上下文，服务器不会自动续上上次聊天。</p>
<h2>请求外壳也有自己的来源</h2><p>四个可能请求均发往同一演示接口：<code>POST {esc(spec['endpoint'])}</code>。模型：<code>example-chat</code>。</p>{network_table}
<details class="raw"><summary>查看完整示例配置与请求头</summary>{code(spec)}</details>
<h2>逐个请求查看：每一条都标明来源</h2><p>先看每个请求的来源表，再看逐条解释；“展开完整原文”保留全部提示词。来源说明不改动原始 JSON。</p><div class="toolbar"><button type="button" id="expand">展开四个请求</button><button type="button" id="collapse">收起四个请求</button></div>
{packets}
<div class="note warn"><strong>当前示例中的信息缺口：</strong>第二轮状态整理只拿到“好”和“那就定明天中午？”，没有整个邀约历史。不能自动理解为用户已确认明天中午；状态整理也不能凭空补回上次请求。程序的原文检查不能完全保证模型语义判断正确。</div>
{reference}
<p class="footer">本页为离线构造示例，不是真机抓包，生成脚本不调用模型。提示词模板来自仓库源码；人物、状态和服务配置为虚构，部分拼装逻辑由脚本模拟，不能代替真实运行验证。<a href="../AGENTS.md">维护规则</a></p>
</main><script>document.getElementById('expand').addEventListener('click',()=>document.querySelectorAll('.packet').forEach(e=>e.open=true));document.getElementById('collapse').addEventListener('click',()=>document.querySelectorAll('.packet').forEach(e=>e.open=false));document.querySelectorAll('a[href^="#"]').forEach(a=>a.addEventListener('click',()=>{{const target=document.getElementById(a.getAttribute('href').slice(1));if(target?.classList.contains('packet'))target.open=true;}}));</script></body></html>'''
paths = [HERE / f for f in ['01-main-after-free.json','01b-state-if-plain.json','02-main-after-ok.json','02b-state-if-plain.json']]
before = {p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in paths}
(HERE.parent / '请求包内容.html').write_text(page, encoding='utf-8')
assert before == {p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in paths}
print('Annotated 34 API messages across four requests; all payloads unchanged.')
