#!/usr/bin/env python3
"""Builds the playground as a static site (for GitHub Pages) into out/pages.

There is no server.py there: the examples run prebuilt (optimised Gradle
builds), and edited code is compiled by JetBrains' Kotlin Playground server
(api.kotlinlang.org), sent with the toolkit's sources (toolkit.json).

usage: ./pages.py [out-dir]       (EC_DIR: the embedded-compose checkout)
"""
import json
import shutil
import subprocess
import sys
from pathlib import Path

import server

ROOT = server.ROOT
GRADLE_OUT = server.OUT / "gradle/playground/compileSync/wasmJs/main/productionExecutable/optimized"


def gradle(*tasks):
    subprocess.run([str(ROOT / "gradlew"), "-q", "--console=plain", *tasks], cwd=ROOT, check=True)


def toolkit_sources():
    """{name: text} of everything the playground compiles besides the user's files.
    Names start with "ec-", which the editor doesn't allow, so they can't clash."""
    gradle(":toolkit:runtimeWithoutRunApp")
    paths = sorted(p for p in (server.EC / "core/ui/src/linuxArm64Main/kotlin/ui").glob("*.kt") if p.name != "Runtime.kt")
    paths += sorted((server.EC / "core/led/src/linuxArm64Main/kotlin").rglob("*.kt"))
    paths.append(server.OUT / "gradle/toolkit/generated/ec/ui/EcRuntime.kt")
    paths += sorted((ROOT / "toolkit/src/wasmJsMain/kotlin").rglob("*.kt"))
    paths += sorted((ROOT / "playground/src/wasmJsMain/kotlin").glob("*.kt"))
    sources = {"ec-" + p.name: p.read_text() for p in paths}
    assert len(sources) == len(paths), "two toolkit files with one name"
    return sources


def main():
    out = Path(sys.argv[1] if len(sys.argv) > 1 else server.OUT / "pages").resolve()
    subprocess.run([str(ROOT / "led/build.sh")], check=True)
    shutil.rmtree(out, ignore_errors=True)
    shutil.copytree(server.SITE, out)
    (out / ".nojekyll").touch()
    (out / "build/examples").mkdir(parents=True)
    shutil.copy(server.WEB / "led.wasm", out / "build/led.wasm")

    examples = server.examples()
    (out / "examples.json").write_text(json.dumps(examples))
    (out / "toolkit.json").write_text(json.dumps(toolkit_sources()))

    # Each example, optimised. The playground's sources are put back after.
    server.SRC.mkdir(parents=True, exist_ok=True)
    saved = {p.name: p.read_text() for p in server.SRC.glob("*.kt")}
    try:
        for e in examples:
            print(f"building {e['id']}...", flush=True)
            for old in server.SRC.glob("*.kt"):
                old.unlink()
            for name, text in e["files"].items():
                (server.SRC / name).write_text(text)
            gradle(":playground:compileProductionExecutableKotlinWasmJs")
            dst = out / "build/examples" / e["id"]
            shutil.copytree(GRADLE_OUT, dst, ignore=shutil.ignore_patterns("*.map"))
    finally:
        for old in server.SRC.glob("*.kt"):
            old.unlink()
        for name, text in saved.items():
            (server.SRC / name).write_text(text)

    size = sum(p.stat().st_size for p in out.rglob("*") if p.is_file())
    print(f"{out}: {size // 1024} kB. Serve it as static files, e.g. GitHub Pages.")


if __name__ == "__main__":
    main()
