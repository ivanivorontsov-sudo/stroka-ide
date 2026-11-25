#!/usr/bin/env python3
"""Локальный Git-сервер по HTTP с проверкой токена (как GitHub: логин + токен в Basic auth).
Использование: git-http-server.py <папка с bare-репозиториями> <порт> <токен>"""
import base64, os, subprocess, sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ROOT, PORT, TOKEN = sys.argv[1], int(sys.argv[2]), sys.argv[3]

class H(BaseHTTPRequestHandler):
    def log_message(self, *a): pass
    def handle_any(self):
        auth = self.headers.get("Authorization", "")
        ok = False
        if auth.startswith("Basic "):
            user, _, pw = base64.b64decode(auth[6:]).decode().partition(":")
            ok = pw == TOKEN
        if not ok:
            self.send_response(401); self.send_header("WWW-Authenticate", 'Basic realm="git"'); self.send_header("Content-Length", "0"); self.end_headers(); return
        path, _, query = self.path.partition("?")
        n = int(self.headers.get("Content-Length") or 0)
        body = self.rfile.read(n) if n else b""
        env = dict(os.environ, GIT_PROJECT_ROOT=ROOT, GIT_HTTP_EXPORT_ALL="1", PATH_INFO=path, QUERY_STRING=query,
                   REQUEST_METHOD=self.command, CONTENT_TYPE=self.headers.get("Content-Type", ""), CONTENT_LENGTH=str(len(body)),
                   REMOTE_USER="tester", REMOTE_ADDR="127.0.0.1", HTTP_CONTENT_ENCODING=self.headers.get("Content-Encoding", ""),
                   GIT_PROTOCOL=self.headers.get("Git-Protocol", ""))
        out = subprocess.run(["git", "http-backend"], input=body, env=env, capture_output=True).stdout
        head, _, rest = out.partition(b"\r\n\r\n")
        status = 200
        headers = []
        for line in head.decode().split("\r\n"):
            k, _, v = line.partition(":")
            if k.lower() == "status": status = int(v.strip().split()[0])
            elif k: headers.append((k, v.strip()))
        self.send_response(status)
        for k, v in headers: self.send_header(k, v)
        self.send_header("Content-Length", str(len(rest))); self.end_headers(); self.wfile.write(rest)
    do_GET = do_POST = handle_any

ThreadingHTTPServer(("127.0.0.1", PORT), H).serve_forever()
