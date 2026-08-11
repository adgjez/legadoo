#!/usr/bin/env python3
"""
Phase F — Real ffmpeg execution validation.
Mirrors Tier3 ManifestExporterBackend logic (buildFFmpegScript) exactly,
then executes the generated script and validates that the final mp4 duration
matches Σ storyboard[i].durationSeconds (within ±0.5s).
"""
import json, os, subprocess, shlex, sys
from pathlib import Path

WORKDIR = Path("/workspace/_phasef")
WORKDIR.mkdir(parents=True, exist_ok=True)

# ============================================================
# 1. Storyboard (NON-UNIFORM durations to defeat 5s fallback paths)
# ============================================================
FRAMES = [
    {"frameId": "frame_00", "index": 0, "durationSec": 3.2,  "color": "red",      "text": "Lin Yao walks along the misty shore."},
    {"frameId": "frame_01", "index": 1, "durationSec": 4.8,  "color": "orange",   "text": "She finds a glowing jade pendant in the sand."},
    {"frameId": "frame_02", "index": 2, "durationSec": 2.5,  "color": "yellow",   "text": "The light bursts, a green vortex opens in the sky."},
    {"frameId": "frame_03", "index": 3, "durationSec": 6.0,  "color": "green",    "text": "She falls onto a giant mushroom; ShanHaiJie reveals itself."},
    {"frameId": "frame_04", "index": 4, "durationSec": 4.0,  "color": "cyan",     "text": "The mysterious silver-haired Mo Yuan appears: 'You are finally here.'"},
    {"frameId": "frame_05", "index": 5, "durationSec": 5.5,  "color": "blue",     "text": "Their eyes meet --"},
    {"frameId": "frame_06", "index": 6, "durationSec": 3.0,  "color": "magenta",  "text": "Suddenly! A huge black bird descends from the sky!"},
    {"frameId": "frame_07", "index": 7, "durationSec": 7.0,  "color": "white",    "text": "Mo Yuan grabs her; they leap together into the waterfall."},
]
EXPECTED_TOTAL_SEC = sum(f["durationSec"] for f in FRAMES)
EXPECTED_TOTAL_MS  = int(round(EXPECTED_TOTAL_SEC * 1000))
print(f"[Storyboard] 8 frames, total = {EXPECTED_TOTAL_SEC:.1f}s ({EXPECTED_TOTAL_MS}ms)")
for f in FRAMES:
    print(f"  {f['frameId']:9s} {f['durationSec']:>5.2f}s  {f['text'][:48]}")

# ============================================================
# 2. buildTimelineFromStoryboard → continuous VideoSegment list
# ============================================================
segments = []
cursor_ms = 0
for f in FRAMES:
    dur_ms = int(round(f["durationSec"] * 1000))
    start_ms = cursor_ms
    end_ms = start_ms + dur_ms
    cursor_ms = end_ms
    segments.append({
        "segmentId": f["frameId"],
        "videoPath": str(WORKDIR / "inputs" / (f["frameId"] + ".mp4")),
        "startTimeMs": start_ms,
        "endTimeMs": end_ms,
        "subtitleText": f["text"],
    })
assert cursor_ms == EXPECTED_TOTAL_MS, f"cursor {cursor_ms} != total {EXPECTED_TOTAL_MS}"
for i in range(1, len(segments)):
    assert segments[i]["startTimeMs"] == segments[i-1]["endTimeMs"], f"timeline gap at {i}"
print(f"[Timeline]   Built {len(segments)} segments, continuous = YES")

# ============================================================
# 3. Generate input source mp4's (15s each, 1080x1920 yuv420p + aac)
# ============================================================
INPUT_DIR = WORKDIR / "inputs"
INPUT_DIR.mkdir(exist_ok=True)
SRC_DURATION = 15.0
print(f"[Inputs] Generating {len(FRAMES)} source mp4 files ({SRC_DURATION:.0f}s each)...")
for s, f in zip(segments, FRAMES):
    out = s["videoPath"]
    if Path(out).exists() and Path(out).stat().st_size > 10_000:
        print(f"  skip {os.path.basename(out)} (exists)")
        continue
    cmd = [
        "ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
        "-f", "lavfi", "-i", f"color=c={f['color']}:s=1080x1920:d={SRC_DURATION}:r=24",
        "-f", "lavfi", "-i", f"anullsrc=r=44100:cl=stereo:d={SRC_DURATION}",
        "-c:v", "libx264", "-preset", "veryfast", "-pix_fmt", "yuv420p", "-crf", "26",
        "-c:a", "aac", "-b:a", "96k",
        "-shortest",
        out
    ]
    r = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if r.returncode != 0:
        print("!!! INPUT FAILED:", " ".join(cmd))
        print(r.stderr.decode("utf-8", errors="replace")[-1200:])
        sys.exit(2)
    sz = os.path.getsize(out)
    print(f"  ok {os.path.basename(out)}  {sz/1024:.0f} KB")

# ============================================================
# 4. generate SRT
# ============================================================
def ms_to_srt(t):
    h = t // 3_600_000; t %= 3_600_000
    m = t // 60_000;    t %= 60_000
    s = t // 1000;      ms = t % 1000
    return f"{h:02d}:{m:02d}:{s:02d},{ms:03d}"

SRT_PATH = WORKDIR / "subtitles.srt"
with open(SRT_PATH, "w", encoding="utf-8") as f:
    for i, s in enumerate(segments, 1):
        f.write(f"{i}\n")
        f.write(f"{ms_to_srt(s['startTimeMs'])} --> {ms_to_srt(s['endTimeMs'])}\n")
        f.write(s["subtitleText"] + "\n\n")
print(f"[SRT]      Wrote {SRT_PATH} ({SRT_PATH.stat().st_size} B)")

# ============================================================
# 5. Generate MANIFEST JSON
# ============================================================
BASE_NAME = "phasef"
MANIFEST = {
    "projectId": "phasef_project",
    "aspectRatio": "9:16",
    "totalDurationMs": EXPECTED_TOTAL_MS,
    "totalSegments": len(segments),
    "outputPath": str(WORKDIR / f"{BASE_NAME}.final.mp4"),
    "segments": [
        {
            "segmentId": s["segmentId"],
            "sourcePath": s["videoPath"],
            "startMs": s["startTimeMs"],
            "endMs": s["endTimeMs"],
            "durationMs": s["endTimeMs"] - s["startTimeMs"],
            "subtitle": s["subtitleText"],
        } for s in segments
    ],
}
MANIFEST_PATH = WORKDIR / "manifest.json"
MANIFEST_PATH.write_text(json.dumps(MANIFEST, ensure_ascii=False, indent=2), encoding="utf-8")
print(f"[Manifest] Wrote {MANIFEST_PATH}")

# ============================================================
# 6. Generate ffmpeg.sh — mirrors VideoAssembly.kt#buildFFmpegScript
# ============================================================
SH_PATH = WORKDIR / "assemble.sh"
FINAL_OUT = MANIFEST["outputPath"]
SRT_ABS = str(SRT_PATH)

style_arg = "FontName=DejaVu Sans,FontSize=26,PrimaryColour=&H00FFFFFF,OutlineColour=&H00000000,Outline=3,Alignment=2"
with open(SH_PATH, "w", encoding="utf-8") as sh:
    sh.write("#!/usr/bin/env bash\n")
    sh.write("set -e\n")
    sh.write(f"cd {shlex.quote(str(WORKDIR))}\n\n")

    # ===== Step A: trim each segment to exact storyboard duration =====
    # Primary: reencode trim (frame-accurate, +pix_fmt yuv420p matches Kt Tier3 Step A default)
    # Fallback: -c copy (less accurate, only when libx264 is unavailable)
    sh.write("# ===== Step A: 逐段精确 trim (主: 重编码=故事板时长; fallback: stream copy) =====\n")
    for i, s in enumerate(segments):
        dur_sec = (s["endTimeMs"] - s["startTimeMs"]) / 1000.0
        in_path  = s["videoPath"]
        out_path = WORKDIR / f"_trimmed_{BASE_NAME}_seg_{i}.mp4"
        sh.write(
            f"ffmpeg -y -hide_banner -loglevel error -i {shlex.quote(in_path)} -t {dur_sec:.3f} -c:v libx264 -preset veryfast -pix_fmt yuv420p -c:a aac {shlex.quote(str(out_path))} 2>/dev/null || \\\n"
            f"  ffmpeg -y -hide_banner -loglevel error -i {shlex.quote(in_path)} -t {dur_sec:.3f} -c copy -map 0 {shlex.quote(str(out_path))}\n\n"
        )

    # ===== Step B: concat trimmed segments =====
    sh.write("# ===== Step B: concat trim 后的片段 =====\n")
    sh.write("cat > concat.txt <<'EOF'\n")
    for i in range(len(segments)):
        sh.write(f"file '_trimmed_{BASE_NAME}_seg_{i}.mp4'\n")
    sh.write("EOF\n")
    concat_out = WORKDIR / f"_concat_{BASE_NAME}.mp4"
    sh.write(
        f"ffmpeg -y -hide_banner -loglevel error -f concat -safe 0 -i concat.txt -c copy {shlex.quote(str(concat_out))}\n\n"
    )

    # ===== Step C: subtitles burn =====
    sh.write("# ===== Step C: SRT 字幕烧录 (DejaVu 字体, force_style 对齐 SubtitleStyle) =====\n")
    intermed = WORKDIR / f"_subtitled_{BASE_NAME}.mp4"
    srt_escaped = SRT_ABS.replace("\\", "\\\\").replace(":", "\\:").replace("'", "\\'")
    sh.write(
        f"ffmpeg -y -hide_banner -loglevel error -i {shlex.quote(str(concat_out))} \\\n"
        f"  -vf \"subtitles='{srt_escaped}':force_style='{style_arg}'\" \\\n"
        f"  -c:v libx264 -preset veryfast -pix_fmt yuv420p -crf 23 \\\n"
        f"  -c:a copy \\\n"
        f"  -s 1080x1920 \\\n"
        f"  {shlex.quote(str(intermed))}\n\n"
    )

    # ===== Step D: faststart + metadata =====
    sh.write("# ===== Step D: Final moov → start (faststart), 元信息写回 =====\n")
    sh.write(
        f"ffmpeg -y -hide_banner -loglevel error -i {shlex.quote(str(intermed))} -c copy -movflags +faststart \\\n"
        f"  -metadata title=\"ArcReel Phase F (real ffmpeg run)\" \\\n"
        f"  -metadata comment=\"Total={EXPECTED_TOTAL_MS}ms, Segments={len(segments)}\" \\\n"
        f"  {shlex.quote(str(FINAL_OUT))}\n\n"
    )
    sh.write("# ===== Step E: 清理临时文件 =====\n")
    sh.write("rm -f concat.txt\n")
    for i in range(len(segments)):
        sh.write(f"rm -f '_trimmed_{BASE_NAME}_seg_{i}.mp4'\n")
    sh.write(f"rm -f {shlex.quote(str(concat_out))} {shlex.quote(str(intermed))}\n")
    sh.write("echo \"assemble.sh DONE\"\n")

os.chmod(SH_PATH, 0o755)
print(f"[ffmpeg.sh] Wrote {SH_PATH} ({SH_PATH.stat().st_size} B)")

# ============================================================
# 7. Self-test: parse -t values to match Phase AC/Z assertions
# ============================================================
import re as _re
raw = SH_PATH.read_text(encoding="utf-8")
trim_values = [float(m.group(1)) for m in _re.finditer(r"-t\s+([0-9]+\.[0-9]+)", raw)]
trim_unique = trim_values[0::2]
assert len(trim_unique) == len(FRAMES), f"-t calls (dedup) {len(trim_unique)} != frames {len(FRAMES)}"
for f, got in zip(FRAMES, trim_unique):
    assert abs(got - f["durationSec"]) < 0.001, f"trim mismatch {f['frameId']}: {got} vs {f['durationSec']}"
total_trim_ms = int(round(sum(trim_unique) * 1000))
assert total_trim_ms == EXPECTED_TOTAL_MS, f"Σ trim {total_trim_ms}ms != storyboard {EXPECTED_TOTAL_MS}ms"
print(f"[SelfTest] ffmpeg -t counts OK: {len(trim_unique)} segments, Σ trim = {total_trim_ms}ms")

# ============================================================
# 8. Execute assemble.sh (real ffmpeg pipeline ~ 30-90s)
# ============================================================
if Path(FINAL_OUT).exists():
    os.remove(FINAL_OUT)
print("\n[Run] Executing assemble.sh...")
print("=" * 60)
r = subprocess.run(["bash", str(SH_PATH)], stdout=None, stderr=None)
if r.returncode != 0:
    print("=" * 60)
    print(f"!!! assemble.sh FAILED rc={r.returncode}")
    sys.exit(3)
print("=" * 60)

# ============================================================
# 9. ffprobe final duration → ASSERTIONS
# ============================================================
if not Path(FINAL_OUT).exists() or Path(FINAL_OUT).stat().st_size < 10_000:
    print(f"!!! Final output missing or too small: {FINAL_OUT}")
    sys.exit(4)

probe = subprocess.run(
    ["ffprobe", "-v", "error",
     "-show_entries", "format=duration,size:stream=index,codec_type,width,height",
     "-of", "json", FINAL_OUT],
    capture_output=True, text=True
)
if probe.returncode != 0:
    print("ffprobe failed:"); print(probe.stderr); sys.exit(5)
info = json.loads(probe.stdout)
fmt = info["format"]
actual_sec = float(fmt["duration"])
actual_ms  = int(round(actual_sec * 1000))
size_kb    = int(fmt["size"]) / 1024
v = next(s for s in info["streams"] if s["codec_type"] == "video")
a = next(s for s in info["streams"] if s["codec_type"] == "audio")

print("\n========== Phase F : Real Execution Report ==========")
print(f"Output       : {FINAL_OUT}")
print(f"File size    : {size_kb:,.0f} KB")
print(f"Resolution   : {v['width']}x{v['height']}")
print(f"Video codec  : {v.get('codec_name','?')}    Audio codec : {a.get('codec_name','?')}")
print(f"Storyboard   : {EXPECTED_TOTAL_SEC:.3f}s  ({EXPECTED_TOTAL_MS} ms)")
print(f"Actual mp4   : {actual_sec:.3f}s  ({actual_ms} ms)")
print(f"Diff         : {actual_sec - EXPECTED_TOTAL_SEC:+.3f}s  ({actual_ms - EXPECTED_TOTAL_MS:+d} ms)")

ok = True
# 1) global duration ±0.25s (8 segments, worst-case 1 frame each ~ 33ms → 264ms)
if abs(actual_ms - EXPECTED_TOTAL_MS) > 250:
    print(f"!! FAIL #1: 总时长偏差 > 0.25s (storyboard {EXPECTED_TOTAL_MS}ms vs actual {actual_ms}ms, Δ = {actual_ms - EXPECTED_TOTAL_MS}ms)")
    ok = False
else:
    print(f"✔ PASS #1: 总时长 storyboard == final mp4 (±0.25s 容器/帧级公差)")

# 2) manifest
man_loaded = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
if man_loaded["totalDurationMs"] != EXPECTED_TOTAL_MS:
    print("!! FAIL #2: manifest.totalDurationMs != storyboard")
    ok = False
else:
    print("✔ PASS #2: manifest.totalDurationMs == Σ storyboard[i].durationSeconds × 1000")

# 3) trim count & sum
print(f"✔ PASS #3: ffmpeg.sh -t 调用数（去重后）= {len(trim_unique)} == 分镜数 {len(FRAMES)}")
print(f"✔ PASS #4: Σ ffmpeg -t 秒数 × 1000 = {total_trim_ms}ms == storyboard {EXPECTED_TOTAL_MS}ms")

# 4) per-segment timeline continuity + duration match
prev_end = 0
frame_mismatches = 0
for i, seg in enumerate(man_loaded["segments"]):
    sm, em = seg["startMs"], seg["endMs"]
    want_dur = int(round(FRAMES[i]["durationSec"]*1000))
    if sm != prev_end:
        print(f"!! FAIL #5a @ {i} {seg['segmentId']}: timeline gap start={sm} prev_end={prev_end}")
        ok = False; frame_mismatches += 1
    if (em - sm) != want_dur:
        print(f"!! FAIL #5b @ {i} {seg['segmentId']}: duration={em-sm}ms vs storyboard {want_dur}ms")
        ok = False; frame_mismatches += 1
    prev_end = em
if frame_mismatches == 0:
    print("✔ PASS #5: manifest 逐段 startMs/endMs 连续无空洞 + 逐段 duration 严格对齐 storyboard.durationSeconds")

# 6) resolution
if v['width'] != 1080 or v['height'] != 1920:
    print(f"!! FAIL #6: 输出分辨率 {v['width']}x{v['height']} != 1080x1920")
    ok = False
else:
    print("✔ PASS #6: 输出分辨率 1080x1920 (9:16) 与 aspectRatio 一致")

print("=====================================================")
if not ok:
    print("OVERALL: FAIL")
    sys.exit(99)
print("OVERALL: ALL PASS ✔  最终 mp4 时长 与 storyboard 承诺 完全一致")
