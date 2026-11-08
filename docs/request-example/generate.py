"""Offline illustrative payloads. Reads source text; never calls a service."""
from pathlib import Path
import hashlib
import json
import re

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent
PRESET = ROOT / 'data/resources/src/main/assets/defaults/online-chat-preset.json'
BOOK = ROOT / 'data/resources/src/main/assets/defaults/online-chat-worldbook.json'
RUNTIME = ROOT / 'app/src/main/java/me/rerere/momoly/ui/world/WorldLifeRuntime.kt'
TURN = ROOT / 'core/model/src/main/java/me/rerere/momoly/data/world/LifeTurn.kt'
preset = json.loads(PRESET.read_text(encoding='utf-8-sig'))
book = json.loads(BOOK.read_text(encoding='utf-8-sig'))
runtime = RUNTIME.read_text(encoding='utf-8-sig')
turn_source = TURN.read_text(encoding='utf-8-sig')
relationship_instruction = re.search(r'const val RELATIONSHIP_OUTPUT_INSTRUCTIONS = """(.*?)"""', turn_source, re.S).group(1).strip()
life_template = re.search(r'private fun lifePrompt.*?= """(.*?)"""\.trimIndent', runtime, re.S).group(1)
assessment_template = re.search(r'private suspend fun assessPlainReply.*?val instruction="""(.*?)"""\.trimIndent', runtime, re.S).group(1)

def dumps(value):
    return json.dumps(value, ensure_ascii=False, indent=2)

def macro(text):
    return text.replace('{{char}}', '林夏').replace('{{user}}', '小陈')

def save(name, value):
    (OUT / name).write_text(dumps(value) + '\n', encoding='utf-8')

schedule = [dict(id='home-day', start=0, end=1440, activity='居家休息', location='home', date='', fixed=False)]
world_map = dict(version=1, places=[
    dict(id='home', name='林夏家', region='城区', x=0.3, y=0.5),
    dict(id='restaurant', name='街角餐厅', region='城区', x=0.7, y=0.5),
], routes=[dict(**{'from':'home', 'to':'restaurant'}, minutes=10)], userLocation='restaurant', climates={'城区':['晴']})
card = dict(name='林夏', description='林夏，25岁，住在现代城市，喜欢做饭。', personality='热情直率，说话简短，有自己的安排。', scenario='林夏与小陈是熟悉的朋友，正在用手机私聊。')
user_description = '小陈，林夏的朋友，住在同一城区。'
public_fact = '这是现代城市，没有魔法。林夏家和街角餐厅都在城区，步行相距10分钟。'
background = '今天在家休息，想约小陈一起吃饭，但尚未约定具体时间和地点。'
headers = {'Content-Type':'application/json', 'Accept':'text/event-stream', 'Authorization':'Bearer EXAMPLE_KEY_NOT_REAL'}
endpoint = 'https://model.example/v1/chat/completions'

def summary(at):
    return '\n'.join([
        '【当前关系与等待状态】',
        f'当前世界时间：{at}；好感 30/100；心情：平静（强度 50/100）。',
        '阶段：正常；焦虑 0/100；烦躁 0/100。',
        '人物反应：耐心 360 分钟，冷落敏感度 1.0，恢复速度 1.0（1为中性）。',
        '主动联系意愿：正常。高好感不改变忙闲。',
        '目前没有等待用户回应的已发消息。',
        f'用户最后回复：{at}。',
        '用户没有告知有效的离开原因或返回时间；不要推断其原因。',
        f'最近变化（{at}）：用户已回复，等待结束；已有情绪和关系变化保留，随后续互动逐步恢复。',
        '按已保存状态表达，可不主动联系；用户回来不必立刻恢复，也不要重复责问。冷落变化已由程序结算，不要再次扣分。',
    ])

def situation(at):
    return f'{at}，林夏家，晴。居家休息。{background}'

def life_prompt(at):
    lines = []
    for raw in life_template.splitlines():
        line = raw.strip()
        if line.startswith('${if(a.relationship.profileSource'):
            line = '人物反应参数已固定。'
        elif line.startswith('${if(a.initial.schedule'):
            line = ''
        elif line.startswith('${if(isReply)'):
            line = re.search(r'if\(isReply\) "([^"]+)"', line).group(1)
        elif line.startswith('日程：${a.schedule'):
            line = '日程： 0:0—24:0 居家休息 林夏家'
        elif line.startswith('当前可变事实：'):
            line = '当前可变事实：'
        line = line.replace('${w.name}', '现代城市').replace('${LifeRules.situation(w,a)}', situation(at))
        line = line.replace('${RelationshipRules.summary(a,w.now)}', summary(at))
        line = line.replace('${a.draft.take(3000)}', '')
        line = line.replace('$RELATIONSHIP_OUTPUT_INSTRUCTIONS', relationship_instruction)
        line = line.replace('${w.map.places.joinToString { "${it.id}=${it.name}" }}', 'home=林夏家, restaurant=街角餐厅')
        lines.append(line)
    result = '\n'.join(lines).strip()
    assert '${' not in result and '$RELATIONSHIP' not in result
    return result

def message(role, content):
    return dict(role=role, content=content)

def main_messages(history, at):
    prompts = {p['identifier']:p for p in preset['prompts']}
    before, after = [], []
    target = before
    for toggle in preset['prompt_order'][0]['order']:
        if not toggle['enabled']:
            continue
        item = prompts[toggle['identifier']]
        if item.get('marker'):
            key = item['identifier']
            if key == 'chatHistory':
                target = after
                continue
            content = {'personaDescription':user_description, 'charDescription':card['description'],
                       'charPersonality':card['personality'], 'scenario':card['scenario'], 'worldInfoAfter':public_fact}.get(key, '')
        else:
            content = macro(item['content'])
        if content:
            target.append(message(item.get('role', 'system'), content))
    default_rule = message('system', macro(book['entries']['0']['content']))
    living = message('system', f'当前世界事实（不能用旧开场覆盖）：{situation(at)}。\n{summary(at)}\n完整日程和数值不直接向用户公开。重新生成仅改写表达，不改变已经发生的事实。')
    return before + history + [default_rule] + after + [living, message('system', life_prompt(at))]

def body(messages, assessment=False):
    return dict(model='example-chat', stream=True, stream_options=dict(include_usage=True),
                temperature=0.1 if assessment else 0.9, max_tokens=2048 if assessment else 8192, messages=messages)

def assessment_messages(at, user, reply):
    instructions = '\n'.join(line.strip() for line in assessment_template.splitlines()).strip()
    instructions = instructions.replace('$RELATIONSHIP_OUTPUT_INSTRUCTIONS', relationship_instruction)
    data = dict(gameTime=at.replace(' ', 'T'), character=card['description'], personality=card['personality'],
                current=dict(background=background, location='home', schedule=schedule),
                relationshipSummary=summary(at), responseProfileUninitialized=False, map=world_map,
                mutableFacts=[], userMessages=user, candidateReply=reply)
    return [message('system', instructions), message('user', json.dumps(data, ensure_ascii=False, separators=(',', ':')))]

hist1 = [message('assistant', '【游戏时间 2026-11-07 20:00】\n明天你有空吗？'), message('user', '【游戏时间 2026-11-07 20:00】\n有空')]
hist2 = hist1 + [message('assistant', '【游戏时间 2026-11-07 20:00】\n来找我吃饭'), message('user', '【游戏时间 2026-11-07 20:01】\n好')]
requests = [
    ('01-main-after-free.json', '第一次主请求：用户说“有空”', body(main_messages(hist1, '2026-11-07 20:00'))),
    ('01b-state-if-plain.json', '可选整理：第一轮仅返回“来找我吃饭”时', body(assessment_messages('2026-11-07 20:00', '有空', '来找我吃饭'), True)),
    ('02-main-after-ok.json', '第二次主请求：用户说“好”', body(main_messages(hist2, '2026-11-07 20:01'))),
    ('02b-state-if-plain.json', '可选整理：第二轮仅返回“那就定明天中午？”时', body(assessment_messages('2026-11-07 20:01', '好', '那就定明天中午？'), True)),
]
for filename, title, payload in requests:
    save(filename, payload)
    assert json.loads((OUT / filename).read_text(encoding='utf-8')) == payload
    assert all('${' not in m['content'] and '{{char}}' not in m['content'] and '{{user}}' not in m['content'] for m in payload['messages'])
    if 'state' in filename:
        assert len(payload['messages']) == 2
        assert json.loads(payload['messages'][1]['content'])['mutableFacts'] == []
assert [m for m in requests[0][2]['messages'] if m['role'] != 'system'] == hist1
assert [m for m in requests[2][2]['messages'] if m['role'] != 'system'] == hist2
save('assumptions.json', dict(endpoint=endpoint, headers=headers, note='虚构演示配置。预设/共用规则/生活及整理指令读取当前源码；离线构造并归一化缩进，未运行Android组装器、未抓包、未请求模型。',
    card=card, userDescription=user_description, publicFact=public_fact, worldMap=world_map,
    background=background, schedule=schedule, responseProfile=dict(patienceMinutes=360, sensitivity=1.0, recoverySpeed=1.0),
    profileSource='card', extensions=[], attachments=[], mutableFacts=[], customRequestFields={},
    conditions='会话已保存卡内开场白及游戏时间；App前台、两轮成功且无额外主动联系/工具/重试；无正则、无裁剪；心情与好感不变。',
    sources={p.relative_to(ROOT).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in [PRESET, BOOK, RUNTIME, TURN]}))

import runpy
runpy.run_path(str(OUT / "render_annotated.py"))
print("Generated docs/请求包内容.html and example JSON files.")
