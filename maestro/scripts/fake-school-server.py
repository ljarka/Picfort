#!/usr/bin/env python3
"""Fake school-server for device checks of the school journal (T-076, ADR 0017).

Speaks school-server's /api/v1 (its docs/api/openapi.yaml) with neutral sample data, so device
checks never touch the family's real school-server. Python standard library only.

    python3 maestro/scripts/fake-school-server.py            # :5101, key "test-key"
    PORT=5102 KEY=other python3 maestro/scripts/fake-school-server.py

From the emulator: http://10.0.2.2:5101. Test hooks (no key needed, test only):
    POST /_test/grade    {"childId":"101","subject":"Mathematics","value":"5"}  → new grade + change event
    POST /_test/message  {"childId":"101","sender":"…","subject":"…"}           → new message + change event
    POST /_test/state    {"auth":"auth_failed"|"ok","lastSuccessMinutesAgo":90,"lastRun":"error"|"ok"}
    POST /_test/reset                                                              → back to the sample data
"""
import itertools
import json
import os
import threading
import time
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

PORT = int(os.environ.get("PORT", "5101"))
KEY = os.environ.get("KEY", "test-key")
LOCK = threading.Lock()
# Ids never repeat, not even after /_test/reset or a restart: the app remembers read ids, as with
# a real server.
IDS = itertools.count(int(time.time()))


def iso(dt):
    return dt.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def now():
    return datetime.now(timezone.utc)


def numeric(value):
    base = {"1": 1, "2": 2, "3": 3, "4": 4, "5": 5, "6": 6}.get(value.rstrip("+-"))
    if base is None:
        return None
    return base + 0.5 if value.endswith("+") else base - 0.25 if value.endswith("-") else float(base)


class World:
    def __init__(self):
        t = now()
        self.children = [
            {"id": "101", "login": "ola.k", "name": "Ola"},
            {"id": "102", "login": "kuba.k", "name": "Kuba"},
        ]
        self.grades = []
        self.messages = []
        self.events = []
        self.auth = "ok"
        self.last_success = t - timedelta(minutes=12)
        self.last_run = "ok"
        self.sync_requested = None
        sample = [
            ("101", "Mathematics", "5", 3, "Test", 1, "regular", 40),
            ("101", "Mathematics", "4+", 1, "Homework", 1, "regular", 30),
            ("101", "Mathematics", "4", None, "Proposed", 1, "proposed_semester", 5),
            ("101", "English", "6", 2, "Speaking", 1, "regular", 20),
            ("101", "History", "3-", 2, "Quiz", 1, "regular", 10),
            ("101", "Art", "+", None, "Activity", 1, "regular", 8),
            ("102", "Mathematics", "3", 3, "Test", 1, "regular", 25),
            ("102", "Physical education", "5", 1, "Activity", 1, "regular", 12),
            ("102", "Mathematics", "4", None, "Semester", 1, "semester", 2),
        ]
        for child, subject, value, weight, category, sem, kind, days in sample:
            self.add_grade(child, subject, value, weight, category, sem, kind, t - timedelta(days=days), event=False)
        removed = self.add_grade("101", "History", "2", 1, "Quiz", 1, "regular", t - timedelta(days=15), event=False)
        removed["deletedAt"] = iso(t - timedelta(days=3))
        self.add_message("101", "Class teacher", "Trip on Friday",
                         "<p>Dear parents,</p><p>On <b>Friday</b> we go to the museum. "
                         "Details: <a href=\"https://example.com/trip\">example.com/trip</a></p>",
                         ["consent-form.pdf"], t - timedelta(days=2), event=False)
        self.add_message("101", "School office", "Photo day", "<p>Photo day is next Tuesday.</p>", [],
                         t - timedelta(days=6), event=False)
        self.add_message("102", "Coach", "Training moved", "<p>Training moves to Thursday 16:00.</p>", [],
                         t - timedelta(days=1), event=False)
        # A history of changes, including an update with a diff.
        for g in self.grades:
            self.event("grade", g, "created", None, at=t - timedelta(days=1))
        self.event("grade", self.grades[1], "updated", {"value": ["4", "4+"], "numericValue": [4, 4.5]})
        for m in self.messages:
            self.event("message", self.summary(m), "created", None, at=t - timedelta(days=1))

    def add_grade(self, child, subject, value, weight, category, semester, kind, issued, event=True):
        t = now()
        g = {
            "id": "g_%d" % next(IDS), "childId": child, "subject": subject, "value": value,
            "numericValue": numeric(value), "weight": weight, "category": category, "semester": semester,
            "kind": kind, "countsToAverage": kind == "regular" and numeric(value) is not None,
            "teacher": "A. Teacher", "comment": None, "issuedOn": issued.date().isoformat(),
            "firstSeenAt": iso(issued), "updatedAt": iso(issued), "deletedAt": None,
        }
        self.grades.append(g)
        if event:
            self.event("grade", g, "created", None, at=t)
        return g

    def add_message(self, child, sender, subject, html, attachments, sent, event=True):
        m = {
            "id": "m_%d" % next(IDS), "childId": child, "sender": sender, "subject": subject,
            "sentAt": iso(sent), "hasAttachments": bool(attachments), "read": False, "readAt": None,
            "deletedAt": None, "bodyHtml": html, "bodyText": html.replace("<p>", "").replace("</p>", "\n"),
            "attachments": [{"name": a} for a in attachments],
        }
        self.messages.append(m)
        if event:
            self.event("message", self.summary(m), "created", None, at=now())
        return m

    @staticmethod
    def summary(m):
        return {k: v for k, v in m.items() if k not in ("bodyHtml", "bodyText", "attachments")}

    def event(self, entity_type, entity, change, diff, at=None):
        self.events.append({
            "id": len(self.events) + 1, "childId": entity["childId"], "entityType": entity_type,
            "entityId": entity["id"], "changeType": change, "detectedAt": iso(at or now()),
            "entity": entity, "diff": diff,
        })

    def status(self):
        t = now()
        running = self.sync_requested is not None and t - self.sync_requested < timedelta(seconds=3)
        last_run = {
            "startedAt": iso(t - timedelta(minutes=12)), "finishedAt": None if running else iso(t - timedelta(minutes=11)),
            "status": "running" if running else self.last_run, "errorCode": "journal_unavailable" if self.last_run == "error" else None,
            "itemsFetched": len(self.grades) + len(self.messages), "changesDetected": 0,
        }
        return {
            "auth": self.auth, "lastSync": last_run,
            "lastSuccessfulSyncAt": iso(self.last_success) if self.last_success else None,
            "nextSyncAt": iso(t + timedelta(minutes=3)), "lastEventId": len(self.events),
            "counts": {"grades": len([g for g in self.grades if not g["deletedAt"]]), "messages": len(self.messages),
                       "unreadMessages": len([m for m in self.messages if not m["read"]])},
            "version": "fake-1.0",
        }


WORLD = World()


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        print("fake-school-server: " + fmt % args)

    def send(self, code, body, headers=None):
        data = json.dumps(body).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Cache-Control", "no-store")
        for k, v in (headers or {}).items():
            self.send_header(k, v)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def error(self, code, error_code, message, headers=None):
        self.send(code, {"error": {"code": error_code, "message": message}}, headers)

    def body(self):
        length = int(self.headers.get("Content-Length") or 0)
        if not length:
            return {}
        try:
            return json.loads(self.rfile.read(length))
        except ValueError:
            return {}

    def handle_any(self, method):
        global WORLD
        url = urlparse(self.path)
        path = url.path.rstrip("/") or "/"
        q = {k: v[0] for k, v in parse_qs(url.query).items()}
        # Read the body before taking the lock: a slow or broken client must not block the others.
        body = self.body() if method in ("POST", "PUT") else {}
        with LOCK:
            w = WORLD
            if path == "/health":
                return self.send(200, {"status": "ok"})
            if path.startswith("/_test/") and method == "POST":
                return self.test_hook(path, body)
            if not path.startswith("/api/v1/"):
                return self.error(404, "NOT_FOUND", "No such path")
            if self.headers.get("X-School-Key") != KEY:
                return self.error(401, "UNAUTHORIZED", "Valid X-School-Key header is required.",
                                  {"WWW-Authenticate": "School-Key"})
            child = q.get("child")
            if child is not None:
                match = [c for c in w.children if child in (c["id"], c["login"])]
                if not match:
                    return self.error(404, "CHILD_NOT_FOUND", "Unknown child")
                child = match[0]["id"]

            def mine(items):
                return [i for i in items if child is None or i["childId"] == child]

            include_deleted = q.get("includeDeleted") == "true"
            if path == "/api/v1/status" and method == "GET":
                return self.send(200, w.status())
            if path == "/api/v1/children" and method == "GET":
                return self.send(200, {"children": w.children})
            if path == "/api/v1/grades" and method == "GET":
                items = [g for g in mine(w.grades) if include_deleted or not g["deletedAt"]]
                return self.send(200, {"items": sorted(items, key=lambda g: g["issuedOn"], reverse=True)})
            if path == "/api/v1/grades/subjects" and method == "GET":
                sem = q.get("semester")
                items = [g for g in mine(w.grades) if not g["deletedAt"] and (sem is None or str(g["semester"]) == sem)]
                subjects = {}
                for g in sorted(items, key=lambda g: g["issuedOn"]):
                    subjects.setdefault(g["subject"], []).append(g)
                out = []
                for name in sorted(subjects):
                    counted = [g for g in subjects[name] if g["countsToAverage"] and g["numericValue"] is not None]
                    weights = sum((g["weight"] or 1) for g in counted)
                    avg = round(sum(g["numericValue"] * (g["weight"] or 1) for g in counted) / weights, 2) if weights else None
                    out.append({"subject": name, "average": avg, "grades": subjects[name]})
                return self.send(200, {"items": out})
            if path == "/api/v1/messages" and method == "GET":
                items = [m for m in mine(w.messages) if include_deleted or not m["deletedAt"]]
                if q.get("unread") == "true":
                    items = [m for m in items if not m["read"]]
                if "since" in q:
                    items = [m for m in items if m["sentAt"] >= q["since"]]
                items = sorted(items, key=lambda m: m["sentAt"], reverse=True)
                start = int(q.get("cursor", "0"))
                limit = int(q.get("limit", "50"))
                page = items[start:start + limit]
                nxt = str(start + limit) if start + limit < len(items) else None
                return self.send(200, {"items": [w.summary(m) for m in page], "nextCursor": nxt})
            if path.startswith("/api/v1/messages/"):
                parts = path.split("/")
                found = [m for m in w.messages if m["id"] == parts[4]] if len(parts) > 4 else []
                if parts[4:] == ["read-all"] and method == "POST":
                    marked = 0
                    for m in mine(w.messages):
                        if not m["read"]:
                            m["read"], m["readAt"], marked = True, iso(now()), marked + 1
                    return self.send(200, {"marked": marked})
                if not found:
                    return self.error(404, "NOT_FOUND", "No such message")
                m = found[0]
                if len(parts) == 5 and method == "GET":
                    return self.send(200, m)
                if len(parts) == 6 and parts[5] == "read" and method in ("PUT", "DELETE"):
                    m["read"] = method == "PUT"
                    m["readAt"] = iso(now()) if m["read"] else None
                    return self.send(200, m)
            if path == "/api/v1/changes" and method == "GET":
                if "after" in q and "since" in q:
                    return self.error(400, "BAD_REQUEST", "after and since together")
                items = mine(w.events)
                if "since" in q:
                    items = [e for e in items if e["detectedAt"] >= q["since"]]
                else:
                    items = [e for e in items if e["id"] > int(q.get("after", "0"))]
                if "type" in q:
                    items = [e for e in items if e["entityType"] == q["type"]]
                limit = int(q.get("limit", "100"))
                page = items[:limit]
                cursor = page[-1]["id"] if page else int(q.get("after", "0"))
                return self.send(200, {"items": page, "nextCursor": cursor, "hasMore": len(items) > limit})
            if path == "/api/v1/sync" and method == "POST":
                if w.auth == "auth_failed":
                    return self.error(409, "AUTH_FAILED", "E-journal login failed")
                t = now()
                if w.sync_requested and t - w.sync_requested < timedelta(minutes=5):
                    left = 300 - int((t - w.sync_requested).total_seconds())
                    return self.error(429, "SYNC_TOO_SOON", "Wait", {"Retry-After": str(left)})
                w.sync_requested = t
                w.last_success = t
                w.last_run = "ok"
                return self.send(202, {"status": "started"})
            return self.error(404, "NOT_FOUND", "No such endpoint")

    def test_hook(self, path, body):
        global WORLD
        w = WORLD
        if path == "/_test/reset":
            WORLD = World()
            return self.send(200, {"ok": True})
        if path == "/_test/grade":
            g = w.add_grade(body.get("childId", "101"), body.get("subject", "Mathematics"), body.get("value", "5"),
                            body.get("weight", 2), body.get("category", "Test"), body.get("semester", 1),
                            body.get("kind", "regular"), now())
            return self.send(200, g)
        if path == "/_test/message":
            m = w.add_message(body.get("childId", "101"), body.get("sender", "Class teacher"),
                              body.get("subject", "New message"), body.get("html", "<p>Hello.</p>"),
                              body.get("attachments", []), now())
            return self.send(200, w.summary(m))
        if path == "/_test/state":
            if "auth" in body:
                w.auth = body["auth"]
            if "lastSuccessMinutesAgo" in body:
                minutes = body["lastSuccessMinutesAgo"]
                w.last_success = None if minutes is None else now() - timedelta(minutes=minutes)
            if "lastRun" in body:
                w.last_run = body["lastRun"]
            return self.send(200, w.status())
        return self.error(404, "NOT_FOUND", "No such test hook")

    def do_GET(self):
        self.handle_any("GET")

    def do_POST(self):
        self.handle_any("POST")

    def do_PUT(self):
        self.handle_any("PUT")

    def do_DELETE(self):
        self.handle_any("DELETE")


if __name__ == "__main__":
    print("Fake school-server on :%d (key %s). Never use real data here." % (PORT, KEY))
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
