#!/usr/bin/env python3
"""The simulator's web server, for this machine only (127.0.0.1).

Serves the page (site/), the wasm builds (out/web/) and two calls:
  GET  /api/examples  the starters: examples/<name>/*.kt, and embedded-compose's
                      apps/matrix sources as they are in that checkout
  POST /api/compile   {"files": {"App.kt": "..."}} -> writes them to
                      out/playground/src, builds :playground with Gradle and
                      returns the compiler's errors, or a new build number

usage: ./server.py [--port 8075]       (EC_DIR: the embedded-compose checkout)
"""
import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import threading
import time
from http import HTTPStatus
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

ROOT = Path(__file__).resolve().parent
EC = Path(os.environ.get("EC_DIR", ROOT.parent / "embedded-compose")).resolve()
OUT = ROOT / "out"
SITE = ROOT / "site"
WEB = OUT / "web"                      # what the page loads: led.wasm, playground/
SRC = OUT / "playground" / "src"       # the editor's files
STATE = OUT / "playground" / "state.json"
GRADLE_TASK = ":playground:compileDevelopmentExecutableKotlinWasmJs"
GRADLE_OUT = OUT / "gradle/playground/compileSync/wasmJs/main/developmentExecutable/kotlin"

FILE_NAME = re.compile(r"^[A-Za-z_][A-Za-z0-9_]{0,63}\.kt$")
MAX_FILES = 32
MAX_BYTES = 1 << 20
# e: file:///path/App.kt:12:5 Unresolved reference 'x'.
DIAGNOSTIC = re.compile(r"^([ew]): file://(/[^:]+):(\d+):(\d+) (.*)$")

lock = threading.Lock()


def files_hash(files):
    """As site/playground.js computes it, to tell whether the last build is of the editor's files."""
    text = "".join(f"{name}\0{files[name]}\0" for name in sorted(files))
    return hashlib.sha256(text.encode()).hexdigest()[:16]


def load_state():
    try:
        return json.loads(STATE.read_text())
    except (OSError, ValueError):
        return {"build": 0, "hash": None}


def examples():
    """[{id, title, files}]: this repo's examples/, then apps/matrix from embedded-compose."""
    result = []
    for d in sorted((ROOT / "examples").iterdir()):
        files = {f.name: f.read_text() for f in sorted(d.glob("*.kt"))}
        if not files:
            continue
        first = next(iter(files.values())).splitlines()[0]
        title = first.removeprefix("//").split(":")[0].strip() if first.startswith("//") else d.name
        result.append({"id": d.name, "title": title, "files": files})
    matrix = EC / "apps/matrix/src/linuxArm64Main/kotlin/matrix"
    if matrix.is_dir():
        files = {"App.kt": (
            "// The matrix app of embedded-compose (apps/matrix), as on a Raspberry Pi with a HUB75 panel.\n"
            "// Left/Right: page; Enter: the page's style. MatrixApp.kt and Pages.kt are that app's sources.\n"
            "import androidx.compose.runtime.Composable\n\n"
            "@Composable\nfun App() = matrix.MatrixApp()\n")}
        files.update({f.name: f.read_text() for f in sorted(matrix.glob("*.kt"))})
        result.insert(0, {"id": "matrix", "title": "Matrix (embedded-compose apps/matrix)", "files": files})
    return result


def compile_files(files):
    """Builds the playground from files; returns the JSON answer for /api/compile."""
    t0 = time.monotonic()
    with lock:
        SRC.mkdir(parents=True, exist_ok=True)
        for old in SRC.glob("*.kt"):
            old.unlink()
        for name, text in files.items():
            (SRC / name).write_text(text)
        proc = subprocess.run(
            [str(ROOT / "gradlew"), "-q", "--console=plain", GRADLE_TASK],
            cwd=ROOT, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            text=True, timeout=900)
        diagnostics, other = [], []
        for line in proc.stdout.splitlines():
            m = DIAGNOSTIC.match(line)
            if m:
                path = Path(m.group(2))
                if path.parent == SRC:  # warnings from the toolkit aren't the user's
                    diagnostics.append({"severity": "error" if m.group(1) == "e" else "warning",
                                        "file": path.name, "line": int(m.group(3)),
                                        "column": int(m.group(4)), "message": m.group(5)})
                elif m.group(1) == "e":
                    other.append(line)
            elif proc.returncode != 0 and line.strip():
                other.append(line)
        # Gradle's own failure report adds nothing to the compiler's errors.
        log = "" if any(d["severity"] == "error" for d in diagnostics) else "\n".join(other[:80])
        answer = {"ok": proc.returncode == 0, "diagnostics": diagnostics,
                  "log": log, "seconds": round(time.monotonic() - t0, 1)}
        if proc.returncode == 0:
            state = load_state()
            state = {"build": state["build"] + 1, "hash": files_hash(files)}
            tmp = WEB / "playground.tmp"
            shutil.rmtree(tmp, ignore_errors=True)
            shutil.copytree(GRADLE_OUT, tmp)
            shutil.rmtree(WEB / "playground", ignore_errors=True)
            tmp.rename(WEB / "playground")
            STATE.write_text(json.dumps(state))
            answer["build"] = state["build"]
        return answer


class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(SITE), **kwargs)

    def log_message(self, fmt, *args):
        pass

    def end_headers(self):
        # Builds change under the same names; always fetch them anew.
        self.send_header("Cache-Control", "no-store")
        super().end_headers()

    def translate_path(self, path):
        path = path.split("?", 1)[0].split("#", 1)[0]
        if path.startswith("/build/"):
            target = (WEB / path[len("/build/"):]).resolve()
            return str(target) if target.is_relative_to(WEB) else str(WEB / "missing")
        return super().translate_path(path)

    def guess_type(self, path):
        if str(path).endswith((".mjs", ".js")):
            return "text/javascript"
        if str(path).endswith(".wasm"):
            return "application/wasm"
        return super().guess_type(path)

    def send_json(self, obj, status=HTTPStatus.OK):
        body = json.dumps(obj).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == "/api/examples":
            return self.send_json(examples())
        if self.path == "/api/status":
            state = load_state()
            state["ready"] = (WEB / "playground" / "playground.mjs").is_file()
            return self.send_json(state)
        return super().do_GET()

    def do_POST(self):
        if self.path != "/api/compile":
            return self.send_error(HTTPStatus.NOT_FOUND)
        length = int(self.headers.get("Content-Length") or 0)
        if length > MAX_BYTES:
            return self.send_json({"ok": False, "log": "too much code"}, HTTPStatus.REQUEST_ENTITY_TOO_LARGE)
        try:
            files = json.loads(self.rfile.read(length))["files"]
            assert isinstance(files, dict) and 0 < len(files) <= MAX_FILES
            assert all(isinstance(t, str) and FILE_NAME.match(n) for n, t in files.items())
        except (ValueError, KeyError, TypeError, AssertionError):
            return self.send_json({"ok": False, "log": "bad request: expected {files: {Name.kt: text}}"},
                                  HTTPStatus.BAD_REQUEST)
        try:
            answer = compile_files(files)
        except subprocess.TimeoutExpired:
            answer = {"ok": False, "diagnostics": [], "log": "the build took too long"}
        print(f"compile: {'ok' if answer['ok'] else 'failed'} in {answer.get('seconds')} s"
              f" ({len(answer.get('diagnostics', []))} diagnostics)", flush=True)
        self.send_json(answer)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--port", type=int, default=8075)
    args = ap.parse_args()
    if not (EC / "core/hal/ec_hal.h").is_file():
        sys.exit(f"no embedded-compose at {EC} (set EC_DIR)")
    if not (WEB / "led.wasm").is_file():
        subprocess.run([str(ROOT / "led/build.sh")], check=True)
    if not (WEB / "playground").is_dir():
        print("first build of the playground (a few minutes the very first time)...", flush=True)
        first = next(e for e in examples() if e["id"] == "hello")
        print(compile_files(first["files"]).get("log") or "ok", flush=True)
    server = ThreadingHTTPServer(("127.0.0.1", args.port), Handler)
    print(f"embedded-compose-hub75-playground: http://127.0.0.1:{args.port}/  (sources from {EC})", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
