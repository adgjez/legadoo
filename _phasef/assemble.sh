#!/usr/bin/env bash
set -e
cd /workspace/_phasef

# ===== Step A: 逐段精确 trim (主: 重编码=故事板时长; fallback: stream copy) =====
ffmpeg -y -hide_banner -loglevel error -i /workspace/_phasef/inputs/frame_00.mp4 -t 3.200 -c:v libx264 -preset veryfast -pix_fmt yuv420p -c:a aac /workspace/_phasef/_trimmed_phasef_seg_0.mp4 2>/dev/null || \
  ffmpeg -y -hide_banner -loglevel error -i /workspace/_phasef/inputs/frame_00.mp4 -t 3.200 -c copy -map 0 /workspace/_phasef/_trimmed_phasef_seg_0.mp4

ffmpeg -y -hide_banner -loglevel error -i /workspace/_phasef/inputs/frame_01.mp4 -t 4.800 -c:v libx264 -preset veryfast -pix_fmt yuv420p -c:a aac /workspace/_phasef/_trimmed_phasef_seg_1.mp4 2>/dev/null || \
  ffmpeg -y -hide_banner -loglevel error -i /workspace/_phasef/inputs/frame_01.mp4 -t 4.800 -c copy -map 0 /workspace/_phasef/_trimmed_phasef_seg_1.mp4

ffmpeg -y -hide_banner -loglevel error -i /workspace/_phasef/inputs/frame_02.mp4 -t 2.500 -c:v libx264 -preset veryfast -pix_fmt yuv420p -c:a aac /workspace/_phasef/_trimmed_phasef_seg_2.mp4 2>/dev/null || \
  ffmpeg -y -hide_banner -loglevel error -i /workspace/_phasef/inputs/frame_02.mp4 -t 2.500 -c copy -map 0 /workspace/_phasef/_trimmed_phasef_seg_2.mp4

ffmpeg -y -hide_banner -loglevel error -i /workspace/_phasef/inputs/frame_03.mp4 -t 6.000 -c:v libx264 -preset veryfast -pix_fmt yuv420p -c:a aac /workspace/_phasef/_trimmed_phasef_seg_3.mp4 2>/dev/null || \
  ffmpeg -y -hide_banner -loglevel error -i /workspace/_phasef/inputs/frame_03.mp4 -t 6.000 -c copy -map 0 /workspace/_phasef/_trimmed_phasef_seg_3.mp4

ffmpeg -y -hide_banner -loglevel error -i /workspace/_phasef/inputs/frame_04.mp4 -t 4.000 -c:v libx264 -preset veryfast -pix_fmt yuv420p -c:a aac /workspace/_phasef/_trimmed_phasef_seg_4.mp4 2>/dev/null || \
  ffmpeg -y -hide_banner -loglevel error -i /workspace/_phasef/inputs/frame_04.mp4 -t 4.000 -c copy -map 0 /workspace/_phasef/_trimmed_phasef_seg_4.mp4

ffmpeg -y -hide_banner -loglevel error -i /workspace/_phasef/inputs/frame_05.mp4 -t 5.500 -c:v libx264 -preset veryfast -pix_fmt yuv420p -c:a aac /workspace/_phasef/_trimmed_phasef_seg_5.mp4 2>/dev/null || \
  ffmpeg -y -hide_banner -loglevel error -i /workspace/_phasef/inputs/frame_05.mp4 -t 5.500 -c copy -map 0 /workspace/_phasef/_trimmed_phasef_seg_5.mp4

ffmpeg -y -hide_banner -loglevel error -i /workspace/_phasef/inputs/frame_06.mp4 -t 3.000 -c:v libx264 -preset veryfast -pix_fmt yuv420p -c:a aac /workspace/_phasef/_trimmed_phasef_seg_6.mp4 2>/dev/null || \
  ffmpeg -y -hide_banner -loglevel error -i /workspace/_phasef/inputs/frame_06.mp4 -t 3.000 -c copy -map 0 /workspace/_phasef/_trimmed_phasef_seg_6.mp4

ffmpeg -y -hide_banner -loglevel error -i /workspace/_phasef/inputs/frame_07.mp4 -t 7.000 -c:v libx264 -preset veryfast -pix_fmt yuv420p -c:a aac /workspace/_phasef/_trimmed_phasef_seg_7.mp4 2>/dev/null || \
  ffmpeg -y -hide_banner -loglevel error -i /workspace/_phasef/inputs/frame_07.mp4 -t 7.000 -c copy -map 0 /workspace/_phasef/_trimmed_phasef_seg_7.mp4

# ===== Step B: concat trim 后的片段 =====
cat > concat.txt <<'EOF'
file '_trimmed_phasef_seg_0.mp4'
file '_trimmed_phasef_seg_1.mp4'
file '_trimmed_phasef_seg_2.mp4'
file '_trimmed_phasef_seg_3.mp4'
file '_trimmed_phasef_seg_4.mp4'
file '_trimmed_phasef_seg_5.mp4'
file '_trimmed_phasef_seg_6.mp4'
file '_trimmed_phasef_seg_7.mp4'
EOF
ffmpeg -y -hide_banner -loglevel error -f concat -safe 0 -i concat.txt -c copy /workspace/_phasef/_concat_phasef.mp4

# ===== Step C: SRT 字幕烧录 (DejaVu 字体, force_style 对齐 SubtitleStyle) =====
ffmpeg -y -hide_banner -loglevel error -i /workspace/_phasef/_concat_phasef.mp4 \
  -vf "subtitles='/workspace/_phasef/subtitles.srt':force_style='FontName=DejaVu Sans,FontSize=26,PrimaryColour=&H00FFFFFF,OutlineColour=&H00000000,Outline=3,Alignment=2'" \
  -c:v libx264 -preset veryfast -pix_fmt yuv420p -crf 23 \
  -c:a copy \
  -s 1080x1920 \
  /workspace/_phasef/_subtitled_phasef.mp4

# ===== Step D: Final moov → start (faststart), 元信息写回 =====
ffmpeg -y -hide_banner -loglevel error -i /workspace/_phasef/_subtitled_phasef.mp4 -c copy -movflags +faststart \
  -metadata title="ArcReel Phase F (real ffmpeg run)" \
  -metadata comment="Total=36000ms, Segments=8" \
  /workspace/_phasef/phasef.final.mp4

# ===== Step E: 清理临时文件 =====
rm -f concat.txt
rm -f '_trimmed_phasef_seg_0.mp4'
rm -f '_trimmed_phasef_seg_1.mp4'
rm -f '_trimmed_phasef_seg_2.mp4'
rm -f '_trimmed_phasef_seg_3.mp4'
rm -f '_trimmed_phasef_seg_4.mp4'
rm -f '_trimmed_phasef_seg_5.mp4'
rm -f '_trimmed_phasef_seg_6.mp4'
rm -f '_trimmed_phasef_seg_7.mp4'
rm -f /workspace/_phasef/_concat_phasef.mp4 /workspace/_phasef/_subtitled_phasef.mp4
echo "assemble.sh DONE"
