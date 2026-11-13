// Puts the test server into a known state for the Events flows (T-054, T-055).
// Runs on the computer, so it talks to the test server on localhost, never the family's server.
// Inputs (runScript env): BIRTH_DATE for Ola ("YYYY-MM-DD"); EVENTS=1 to ensure the filter events.
// Credentials come from MAESTRO_* (scripts/maestro-smoke.sh); nothing is logged.

function check(res, what) {
  if (!res.ok) throw new Error(what + " failed: HTTP " + res.status)
  return res.body ? json(res.body) : {}
}

const login = check(http.post(MAESTRO_IMMICH_URL + "/api/auth/login", {
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ email: MAESTRO_IMMICH_EMAIL, password: MAESTRO_IMMICH_PASSWORD }),
}), "Immich login")
const server = MAESTRO_PICFORT_SERVER_HOST
const headers = { "Authorization": "Bearer " + login.accessToken, "Content-Type": "application/json" }

const profiles = check(http.get(server + "/family", { headers: headers }), "GET /family").profiles
function memberId(name) {
  const p = profiles.find(function (p) { return p.display_name === name })
  if (!p) throw new Error("Test member " + name + " is missing on the test server")
  return p.user_id
}
const ola = memberId("Ola")

if (typeof BIRTH_DATE !== "undefined" && BIRTH_DATE) {
  check(http.request(server + "/family/" + ola, {
    method: "PATCH", headers: headers, body: JSON.stringify({ birth_date: BIRTH_DATE }),
  }), "PATCH Ola's birth date")
}

if (typeof EVENTS !== "undefined" && EVENTS === "1") {
  const kuba = memberId("Kuba")
  const today = new Date()
  const inDays = function (n) {
    const d = new Date(today.getTime() + n * 86400000)
    return d.toISOString().substring(0, 10)
  }
  const upcoming = check(http.get(server + "/events?after=" + inDays(0), { headers: headers }), "GET /events")
  function ensure(title, members) {
    const found = upcoming.find(function (e) { return e.title === title })
    const body = { title: title, start_date: inDays(5), members: members }
    if (found) {
      check(http.request(server + "/events/" + found.id, { method: "PATCH", headers: headers, body: JSON.stringify(body) }), "PATCH " + title)
    } else {
      check(http.post(server + "/events", { headers: headers, body: JSON.stringify(body) }), "POST " + title)
    }
  }
  ensure("Basen T054", [ola, kuba])
  ensure("Wspolne T054", [])
}
