from flask import Flask, request, jsonify
import subprocess, tempfile, os, shutil

app = Flask(__name__)

def which(exe): 
    # On Windows: kotlinc.bat; elsewhere: kotlinc
    return shutil.which(exe) or exe

def run(cmd, timeout=20, cwd=None, input_data=None):
    """Run a command, return (rc, stdout, stderr). Raises on timeout."""
    p = subprocess.run(
        cmd,
        cwd=cwd,
        input=input_data,
        capture_output=True,
        text=True,
        timeout=timeout,
        shell=False
    )
    return p.returncode, p.stdout, p.stderr

def tool_ok(tool, *args):
    try:
        rc, out, err = run([tool, *args], timeout=8)
        return rc == 0 or out or err  # prints a version is fine
    except Exception:
        return False

@app.post("/execute/kotlin")
def execute_kotlin():
    """Specific endpoint for Kotlin execution as requested by the user."""
    try:
        data = request.get_json(force=True)
        code = data.get("code", "")
        stdin = data.get("stdin", "")
    except Exception:
        code = request.data.decode("utf-8", errors="replace")
        stdin = ""

    if not code.strip():
        return jsonify({"success": False, "output": "", "error": "Empty source received."}), 200

    kotlinc_bin = which("kotlinc.bat") if os.name == "nt" else which("kotlinc")
    java_bin = which("java")

    if not tool_ok(kotlinc_bin, "-version"):
        return jsonify({"success": False, "output": "", "error": "kotlinc not available on server."}), 200

    try:
        with tempfile.TemporaryDirectory() as tmp:
            src_path = os.path.join(tmp, "Main.kt")
            jar_path = os.path.join(tmp, "app.jar")
            with open(src_path, "w", encoding="utf-8") as f:
                f.write(code)

            # Compile
            rc, out, err = run([kotlinc_bin, src_path, "-include-runtime", "-d", jar_path, "-jvm-target", "1.8"], timeout=30)
            if rc != 0:
                return jsonify({"success": False, "output": out, "error": err}), 200

            # Execute
            rc, out, err = run([java_bin, "-jar", jar_path], timeout=15, input_data=stdin)
            return jsonify({"success": rc == 0, "output": out, "error": err}), 200

    except subprocess.TimeoutExpired:
        return jsonify({"success": False, "output": "", "error": "Execution Timed Out"}), 200
    except Exception as e:
        return jsonify({"success": False, "output": "", "error": str(e)}), 200

@app.post("/compile")
def compile_code():
    try:
        data = request.get_json(force=True)
        code = data.get("code", "")
        stdin = data.get("stdin", "")
    except Exception:
        # Fallback for raw data if JSON parsing fails
        code = request.data.decode("utf-8", errors="replace")
        stdin = ""

    if not code.strip():
        return jsonify({"success": False, "output": "",
                        "error": "Empty source received."}), 200

    # Resolve tool names per OS
    kotlinc_bin = which("kotlinc.bat") if os.name == "nt" else which("kotlinc")
    java_bin    = which("java")
    javac_bin   = which("javac.bat")   if os.name == "nt" else which("javac")
    python_bin  = which("python")      or which("python3")
    gcc_bin     = which("gcc")
    gpp_bin     = which("g++")

    # Pre-flight checks (only for the requested language)
    # We'll do this inside the language-specific block instead of here globally to avoid failing if one tool is missing but not needed.

    try:
        data = request.get_json(force=True)
        code = data.get("code", "")
        stdin = data.get("stdin", "")
        ext = data.get("ext", "kt").strip().lower().replace(".", "")
    except Exception:
        code = request.data.decode("utf-8", errors="replace")
        stdin = ""
        ext = "kt"

    if not code.strip():
        return jsonify({"success": False, "output": "",
                        "error": "Empty source received."}), 200

    try:
        with tempfile.TemporaryDirectory() as tmp:
            if ext == "kt":
                if not tool_ok(kotlinc_bin, "-version"):
                    return jsonify({"success": False, "output": "", "error": "kotlinc not available."}), 200
                src_path = os.path.join(tmp, "Main.kt")
                jar_path = os.path.join(tmp, "app.jar")
                with open(src_path, "w", encoding="utf-8") as f: f.write(code)
                rc, out, err = run([kotlinc_bin, src_path, "-include-runtime", "-d", jar_path, "-jvm-target", "1.8"], timeout=30)
                if rc != 0: return jsonify({"success": False, "output": out, "error": err}), 200
                rc, out, err = run([java_bin, "-jar", jar_path], timeout=15, input_data=stdin)
                return jsonify({"success": rc == 0, "output": out, "error": err}), 200

            elif ext == "java":
                if not tool_ok(javac_bin, "-version"):
                    return jsonify({"success": False, "output": "", "error": "javac not available."}), 200
                src_path = os.path.join(tmp, "Main.java")
                with open(src_path, "w", encoding="utf-8") as f: f.write(code)
                rc, out, err = run([javac_bin, src_path], timeout=30)
                if rc != 0: return jsonify({"success": False, "output": out, "error": err}), 200
                rc, out, err = run([java_bin, "-cp", tmp, "Main"], timeout=15, input_data=stdin)
                return jsonify({"success": rc == 0, "output": out, "error": err}), 200

            elif ext == "py":
                if not python_bin:
                    return jsonify({"success": False, "output": "", "error": "python not available."}), 200
                src_path = os.path.join(tmp, "script.py")
                with open(src_path, "w", encoding="utf-8") as f: f.write(code)
                rc, out, err = run([python_bin, src_path], timeout=15, input_data=stdin)
                return jsonify({"success": rc == 0, "output": out, "error": err}), 200

            elif ext == "c":
                if not tool_ok(gcc_bin, "--version"):
                    return jsonify({"success": False, "output": "", "error": "gcc not available."}), 200
                src_path = os.path.join(tmp, "main.c")
                exe_path = os.path.join(tmp, "app.exe" if os.name == "nt" else "app")
                with open(src_path, "w", encoding="utf-8") as f: f.write(code)
                rc, out, err = run([gcc_bin, src_path, "-o", exe_path], timeout=30)
                if rc != 0: return jsonify({"success": False, "output": out, "error": err}), 200
                rc, out, err = run([exe_path], timeout=15, input_data=stdin)
                return jsonify({"success": rc == 0, "output": out, "error": err}), 200

            elif ext == "cpp":
                if not tool_ok(gpp_bin, "--version"):
                    return jsonify({"success": False, "output": "", "error": "g++ not available."}), 200
                src_path = os.path.join(tmp, "main.cpp")
                exe_path = os.path.join(tmp, "app.exe" if os.name == "nt" else "app")
                with open(src_path, "w", encoding="utf-8") as f: f.write(code)
                rc, out, err = run([gpp_bin, src_path, "-o", exe_path], timeout=30)
                if rc != 0: return jsonify({"success": False, "output": out, "error": err}), 200
                rc, out, err = run([exe_path], timeout=15, input_data=stdin)
                return jsonify({"success": rc == 0, "output": out, "error": err}), 200

            else:
                return jsonify({"success": False, "output": "", "error": f"Unsupported extension: {ext}"}), 200

    except subprocess.TimeoutExpired as te:
        return jsonify({"success": False, "output": "",
                        "error": f"TimeoutExpired: {te}"}), 200
    except Exception as e:
        return jsonify({"success": False, "output": "",
                        "error": f"{type(e).__name__}: {e}"}), 200

if __name__ == "__main__":
    # Change host to "0.0.0.0" to allow connections from other devices on the same Wi-Fi
    app.run(host="0.0.0.0", port=5000, debug=False, use_reloader=False)
