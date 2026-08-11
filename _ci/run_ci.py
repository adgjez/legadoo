#!/usr/bin/env python3
"""
CI: Pure-Kotlin compilation of:
  (1) _AllCIStubs.kt + app/src/main/java/io/legado/app/video/** (minus ui, converter, service, Workbench)
  (2) + ArcReelPipelineDryRunTest.kt

Output: only report syntax/type errors for the two target files & all of their direct deps.
"""
import subprocess, os, sys
from pathlib import Path

KOTLINC = "/opt/_kotlin_local/kotlinc/bin/kotlinc"
ROOT     = Path("/workspace")
CI_DIR   = ROOT / "_ci"
MAIN_SRC = ROOT / "app/src/main/java"
TEST_SRC = ROOT / "app/src/test/java"
STUB     = CI_DIR / "_stubs/_AllCIStubs.kt"

# Include these main modules (pure Kotlin logic, no Compose/Activity)
INCLUDE_DIRS = [
    "io/legado/app/video/agent",
    "io/legado/app/video/api",
    "io/legado/app/video/audio",
    "io/legado/app/video/config",
    "io/legado/app/video/data",
    "io/legado/app/video/docs",
    "io/legado/app/video/pipeline",
    "io/legado/app/video/quality",
    "io/legado/app/video/realtime",
    "io/legado/app/video/states",
    "io/legado/app/video/styles",
    "io/legado/app/video/templates",   # may have Compose but pure logic parts only → include anyway
]
# Skip these explicitly even in templates/others
SKIP_HINTS = (
    "TemplateBrowseActivity", "TemplateBrowseScreen",
    "Activity.kt", "Screen.kt",
    "VideoWorkbenchActivity", "BookVideoActivity", "VideoComponents.kt",
)

main_kt_files = []
for rel in INCLUDE_DIRS:
    d = MAIN_SRC / rel
    if not d.is_dir():
        print(f"[WARN] missing source dir: {d}")
        continue
    for p in sorted(d.rglob("*.kt")):
        hint = str(p)
        if any(x in hint for x in SKIP_HINTS):
            # print(f"[skip] {p}")
            continue
        main_kt_files.append(str(p))

target_main = MAIN_SRC / "io/legado/app/video/pipeline/VideoAssembly.kt"
target_test = TEST_SRC / "io/legado/app/video/test/ArcReelPipelineDryRunTest.kt"
assert target_main.exists(), target_main
assert target_test.exists(), target_test
# Make sure our targets are included in the file list
for required in [str(target_main)]:
    if required not in main_kt_files:
        main_kt_files.append(required)

MAIN_OUT = CI_DIR / "classes/main"
TEST_OUT = CI_DIR / "classes/test"
MAIN_OUT.mkdir(parents=True, exist_ok=True)
TEST_OUT.mkdir(parents=True, exist_ok=True)

K_COMMON = [
    KOTLINC, "-jvm-target", "17",
    "-language-version", "2.0",
    "-api-version", "2.0",
    "-nowarn",  # supress stub-related deprecation / unused warnings
]

# ================ STEP 1: compile main sources + stubs together ================
print(f"\n{'='*70}")
print(f"[CI step 1/2] Compile main: stubs + {len(main_kt_files)} source files")
print(f"{'='*70}")

cmd = K_COMMON + [str(STUB), "-d", str(MAIN_OUT)] + main_kt_files
print(f"[cmd] ({len(cmd)} tokens, classes -> {MAIN_OUT})")
p = subprocess.run(cmd, capture_output=True, text=True, timeout=600)

step1_stdout = p.stdout.strip()
step1_stderr = p.stderr.strip()
# kotlinc output messages usually to stderr
errors = []
for line in (step1_stdout + "\n" + step1_stderr).splitlines():
    line = line.strip()
    if not line:
        continue
    # count ERROR-ish lines
    if any(tok in line for tok in (": error:", "error:", "Exception:", "unresolved", "incompatible")):
        errors.append(line)

if errors:
    print(f"\n⚠️   MAIN COMPILATION: {len(errors)} possible error lines\n----tail 60----\n")
    all_out = (step1_stdout + "\n" + step1_stderr).strip()
    tail_lines = all_out.splitlines()[-60:]
    for l in tail_lines:
        print("   ", l)
else:
    print(f"\n✔  MAIN COMPILE CLEAN (rc={p.returncode}), no error: markers")
    print(f"   stdout tail: {step1_stdout.splitlines()[-3:] if step1_stdout else '(none)'}")
    print(f"   stderr tail: {step1_stderr.splitlines()[-3:] if step1_stderr else '(none)'}")

step1_ok = (p.returncode == 0) and (not errors)

# ================ STEP 2: compile test on top of main classes (cp = main) ================
print(f"\n{'='*70}")
print("[CI step 2/2] Compile test: ArcReelPipelineDryRunTest.kt (against main classes)")
print(f"{'='*70}")

cmd2 = K_COMMON + [
    str(STUB),
    "-cp", str(MAIN_OUT),
    "-d", str(TEST_OUT),
    str(target_test)
] + main_kt_files
# include main sources for incremental in case compiler prefers sources vs class files for some
print(f"[cmd] ({len(cmd2)} tokens, test classes -> {TEST_OUT})")
p2 = subprocess.run(cmd2, capture_output=True, text=True, timeout=1200)

step2_stdout = p2.stdout.strip()
step2_stderr = p2.stderr.strip()
errors2 = []
for line in (step2_stdout + "\n" + step2_stderr).splitlines():
    line = line.strip()
    if not line:
        continue
    if any(tok in line for tok in (": error:", "error:", "Exception:", "unresolved", "incompatible")):
        errors2.append(line)

if errors2:
    print(f"\n⚠️   TEST COMPILATION: {len(errors2)} possible error lines")
    print("---- top 50 error markers ----")
    for l in errors2[:50]:
        print("   ", l)
    print("---- last 60 lines of combined output ----")
    all_out = (step2_stdout + "\n" + step2_stderr).strip()
    for l in all_out.splitlines()[-60:]:
        print("   ", l)
else:
    print(f"\n✔  TEST COMPILE CLEAN (rc={p2.returncode}), no error: markers")
    print(f"   stdout tail: {step2_stdout.splitlines()[-3:] if step2_stdout else '(none)'}")
    print(f"   stderr tail: {step2_stderr.splitlines()[-3:] if step2_stderr else '(none)'}")

step2_ok = (p2.returncode == 0) and (not errors2)

# ================ SUMMARY ================
print(f"\n{'='*70}")
print(f"CI SUMMARY")
print(f"{'='*70}")
print(f"[Step 1] Main compile (VideoAssembly.kt + pure-logic modules)   →  {'PASS ✔' if step1_ok else 'FAIL ✗'}  rc={p.returncode}  error_markers={len(errors)}")
print(f"[Step 2] Test compile (ArcReelPipelineDryRunTest.kt)            →  {'PASS ✔' if step2_ok else 'FAIL ✗'}  rc={p2.returncode}  error_markers={len(errors2)}")
print(f"[Targets]")
print(f"  · {target_main}")
print(f"  · {target_test}")
if step1_ok and step2_ok:
    print("\n🎉 OVERALL: CI COMPILE PASS ✔  两个目标文件 + 其纯逻辑依赖全部语法/类型编译通过")
    sys.exit(0)
else:
    print("\n🚨 OVERALL: CI COMPILE FAIL ✗  见上方 step1 / step2 报错细节")
    sys.exit(1)
