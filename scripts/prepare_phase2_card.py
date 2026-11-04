"""Adapt the supplied Xia Yizhou card without executing embedded content.

Usage: python scripts/prepare_phase2_card.py --input original.png --output artifacts/phase-02
Writes only named JSON/PNG and a machine-readable receipt in the output directory.
Existing identical outputs are allowed; different files require --replace.
"""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import struct
import zlib


def chunks(blob):
    assert blob[:8] == b"\x89PNG\r\n\x1a\n", "Not PNG"
    offset = 8
    while offset < len(blob):
        n = struct.unpack(">I", blob[offset:offset+4])[0]
        tag = blob[offset+4:offset+8]
        value = blob[offset+8:offset+8+n]
        assert len(value) == n and struct.unpack(">I", blob[offset+8+n:offset+12+n])[0] == zlib.crc32(tag+value), "Invalid PNG CRC"
        yield tag, value
        offset += n+12
    assert offset == len(blob), "Truncated PNG"


def payload(tag, value):
    if tag not in (b"tEXt", b"zTXt"):
        return None
    key, _, data = value.partition(b"\0")
    if key not in (b"chara", b"ccv3"):
        return None
    if tag == b"zTXt":
        assert data[0] == 0
        data = zlib.decompress(data[1:])
    return key, json.loads(base64.b64decode(data, validate=True))


def encode_chunk(tag, data):
    return struct.pack(">I", len(data))+tag+data+struct.pack(">I", zlib.crc32(tag+data))


def adapt(original):
    card = json.loads(json.dumps(original, ensure_ascii=False))
    data = card.get("data", card)
    assert data["name"] == "夏以昼", "This adapter is for the supplied Xia Yizhou fixture only"
    life = {
        "version": 1, "initialTime": "2048-06-12T17:30:00", "initialLocation": "supply",
        "background": "夏以昼刚结束一轮拦截任务，正在天行市远空舰队补给点交接装备。今晚仍有值班事项，能看终端时会给你留言，明天计划休假。",
        "history": "夏以昼，25岁，深空航天署远空舰队执舰官，擅长飞行与空战，Evol 为引力控制。你们曾由同一位奶奶抚养长大，成年后解除了亲属关系。他毕业于天行市航空航天大学，常年承担深空巡逻任务，与你一直保持着亲近的联系。",
        "affinity": 82, "mood": "任务后的疲惫与牵挂", "moodIntensity": 62,
        "schedule": [],
    }
    routines = [
        ("sleep", 0, 360, "休息，终端静音", "quarters", "unavailable"),
        ("breakfast", 360, 420, "整理制服、早餐和查看私人消息", "quarters", "free"),
        ("training", 420, 480, "飞行前检查和训练", "hangar", "brief"),
        ("morning", 480, 720, "舰队值班与航线审核", "command", "unavailable"),
        ("lunch", 720, 780, "补给与午间休息", "supply", "free"),
        ("patrol", 780, 1020, "执行近空巡逻任务", "patrol", "unavailable"),
        ("handover", 1020, 1080, "交接装备和任务简报", "supply", "brief"),
        ("dinner", 1080, 1200, "返回住处、晚饭和私人时间", "quarters", "free"),
        ("night", 1200, 1320, "审核次日值班安排", "command", "brief"),
        ("reading", 1320, 1440, "回到住处，阅读和放松", "quarters", "free"),
    ]
    for ident, start, end, activity, location, availability in routines:
        life["schedule"].append(dict(id=ident, start=start, end=end, activity=activity,
                                     location=location, availability=availability, date="", fixed=False))
    # The opening explicitly promises leave tomorrow; use a dated override instead of forgetting it.
    life["schedule"].append(dict(id="leave", start=480, end=1320, activity="休假，处理私人安排；尚未发生见面",
                                location="quarters", availability="free", date="2048-06-13", fixed=False))
    places = [
        ("linkong_home", "临空市住处", "临空市", .12, .80),
        ("association", "猎人协会", "临空市", .40, .82),
        ("quarters", "天行市住处", "天行市", .16, .44),
        ("supply", "舰队补给点", "天行市", .53, .46),
        ("command", "航天署指挥区", "天行市", .73, .18),
        ("hangar", "飞行训练场", "天行市", .23, .14),
        ("patrol", "近空巡逻区", "近空", .88, .52),
    ]
    world_map = dict(version=1, userLocation="linkong_home",
                     places=[dict(id=i, name=n, region=r, x=x, y=y) for i,n,r,x,y in places],
                     routes=[dict(zip(("from", "to", "minutes"), route)) for route in [
                         ("linkong_home", "association", 20), ("linkong_home", "quarters", 90),
                         ("quarters", "supply", 15), ("quarters", "hangar", 10),
                         ("quarters", "command", 20), ("supply", "command", 10),
                         ("command", "patrol", 30)]],
                     climates={"临空市": ["晴", "多云", "阴", "阵雨"], "天行市": ["晴", "多云", "低云", "小雨"], "近空": ["航路晴朗", "高空薄云"]})
    data.setdefault("extensions", {})["momoly_life"] = life
    entries = data["character_book"]["entries"]
    next_id = max(e.get("id", -1) for e in entries)+1
    def add(comment, content, meta):
        entries.append(dict(id=next_id+len(entries)-44, keys=[], secondary_keys=[], comment=comment,
                            content=content, constant=False, selective=False, insertion_order=100,
                            enabled=True, position="after_char", extensions={"momoly": meta}))
    add("Momoly 世界地图", json.dumps(world_map, ensure_ascii=False), {"kind": "map", "version": 1})
    add("临空市当前天气", "多云", {"kind": "fact", "id": "linkong_weather", "mutable": True, "scope": "public", "binding": "weather:linkong_home"})
    add("夏以昼当前活动", "补给点交接", {"kind": "fact", "id": "xy_current_activity", "mutable": True, "scope": "self", "binding": "activity:self"})
    add("夏以昼与用户的新约定", "本轮聊天尚未达成新的约定。", {"kind": "fact", "id": "xy_agreements", "mutable": True, "scope": "self"})
    data["creator_notes"] = data.get("creator_notes", "") + "\n\nMomoly 阶段二测试改造：新增的具体日期、示意地图坐标、路线耗时、作息和数值是本次测试场景，不宣称为原作官方设定。原人物设定与44条世界观保留。运行状态由 Momoly 管理；没有新增远程脚本或 MVU 依赖。"
    card["spec"] = "chara_card_v2"
    card["spec_version"] = "2.0"
    if "data" not in card:
        card = {"spec": "chara_card_v2", "spec_version": "2.0", "data": data}
    for key in ("character_book", "extensions", "creator_notes"):
        if key in original:
            card[key] = data[key]
    return card


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--replace", action="store_true")
    args = parser.parse_args()
    original = args.input.resolve().read_bytes()
    source_chunks = list(chunks(original))
    cards = dict(p for t,v in source_chunks if (p := payload(t,v)))
    source_card = cards.get(b"ccv3", cards.get(b"chara"))
    assert source_card, "PNG has no rolecard payload"
    result = adapt(source_card)
    v3 = dict(result, spec="chara_card_v3", spec_version="3.0")
    packed = bytearray(original[:8])
    for tag, value in source_chunks:
        if payload(tag,value):
            continue
        if tag == b"IEND":
            for key, card in ((b"chara", result), (b"ccv3", v3)):
                packed += encode_chunk(b"tEXt", key+b"\0"+base64.b64encode(json.dumps(card,ensure_ascii=False).encode("utf-8")))
        packed += encode_chunk(tag,value)
    output_chunks = list(chunks(bytes(packed)))
    decoded = dict(p for t,v in output_chunks if (p := payload(t,v)))
    assert decoded[b"chara"] == result and decoded[b"ccv3"] == v3
    assert [x for x in source_chunks if not payload(*x)] == [x for x in output_chunks if not payload(*x)], "Image or ancillary chunk changed"
    before = source_card.get("data",source_card)
    after = result["data"]
    assert all(after[k] == v for k,v in before.items() if k not in ("extensions", "character_book", "creator_notes"))
    assert after["character_book"]["entries"][:44] == before["character_book"]["entries"]
    assert all(after["extensions"][k] == v for k,v in before["extensions"].items())
    outputs = {"夏以昼-阶段二.json": (json.dumps(result,ensure_ascii=False,indent=2)+"\n").encode("utf-8"), "夏以昼-阶段二.png": bytes(packed)}
    receipt = dict(originalPath=str(args.input.resolve()), originalSha256=hashlib.sha256(original).hexdigest(),
                   sourceType="text", capabilities=["regex"], originalWorldEntries=44, outputWorldEntries=48,
                   imageChunksPreserved=True, jsonPngParity=True, originalFieldsPreserved=True,
                   dependencies={"host_required": ["Momoly phase 2"], "remote_runtime": [], "newScripts": []},
                   files={k:hashlib.sha256(v).hexdigest() for k,v in outputs.items()},
                   evidence="offline-artifact only; runtime acceptance recorded separately in phase-02 design")
    outputs["card-validation.json"]=(json.dumps(receipt,ensure_ascii=False,indent=2)+"\n").encode("utf-8")
    args.output.mkdir(parents=True,exist_ok=True)
    for name, content in outputs.items():
        target=(args.output/name).resolve()
        assert target.parent == args.output.resolve() and target != args.input.resolve()
        if target.exists() and target.read_bytes()!=content and not args.replace:
            raise FileExistsError(f"Refusing to overwrite {target}; inspect and use --replace explicitly")
    for name, content in outputs.items():
        temporary=args.output/(name+".tmp")
        temporary.write_bytes(content)
        temporary.replace(args.output/name)
    print(json.dumps(receipt,ensure_ascii=False,indent=2))


if __name__ == "__main__":
    main()
