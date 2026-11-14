// Puts the test server into a known state for the Events flows (T-054, T-055) and Search (T-057).
// Runs on the computer, so it talks to the test server on localhost, never the family's server.
// Inputs (runScript env): BIRTH_DATE for Ola ("YYYY-MM-DD"); EVENTS=1 to ensure the filter events;
// SCHOOL=1 to delete the School flow's child "Ala T029" (the flow adds it again);
// PEOPLE=1 to ensure 14 Immich people "Osoba 01".."Osoba 14" on the test account (faces tagged by
// hand on its photos: 01 on two photos, 02 on the first of them, the others on one photo each).
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

const immich = MAESTRO_IMMICH_URL
let profiles = null
function memberId(name) {
  if (!profiles) profiles = check(http.get(server + "/family", { headers: headers }), "GET /family").profiles
  const p = profiles.find(function (p) { return p.display_name === name })
  if (!p) throw new Error("Test member " + name + " is missing on the test server")
  return p.user_id
}
if (typeof BIRTH_DATE !== "undefined" && BIRTH_DATE) {
  const ola = memberId("Ola")
  check(http.request(server + "/family/" + ola, {
    method: "PATCH", headers: headers, body: JSON.stringify({ birth_date: BIRTH_DATE }),
  }), "PATCH Ola's birth date")
}

if (typeof EVENTS !== "undefined" && EVENTS === "1") {
  const ola = memberId("Ola")
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

if (typeof SCHOOL !== "undefined" && SCHOOL === "1") {
  const school = check(http.get(server + "/school", { headers: headers }), "GET /school")
  school.children.filter(function (c) { return c.name === "Ala T029" }).forEach(function (c) {
    check(http.request(server + "/school/children/" + c.id, { method: "DELETE", headers: headers }), "DELETE child")
  })
}

if (typeof PEOPLE !== "undefined" && PEOPLE === "1") {
  // Immich lists only people with at least one face.
  const people = check(http.get(immich + "/api/people?withHidden=true&size=500", { headers: headers }), "GET people").people
  const photos = check(http.post(immich + "/api/search/metadata", {
    headers: headers, body: JSON.stringify({ type: "IMAGE", size: 20, order: "desc" }),
  }), "search photos").assets.items
  if (photos.length < 14) throw new Error("The test library needs at least 14 photos")
  function face(personId, assetId) {
    check(http.post(immich + "/api/faces", {
      headers: headers,
      body: JSON.stringify({ assetId: assetId, personId: personId, imageWidth: 1000, imageHeight: 1000, x: 100, y: 100, width: 200, height: 200 }),
    }), "tag a face")
  }
  for (let i = 1; i <= 14; i++) {
    const name = "Osoba " + (i < 10 ? "0" : "") + i
    if (people.find(function (p) { return p.name === name })) continue
    const person = check(http.post(immich + "/api/people", { headers: headers, body: JSON.stringify({ name: name }) }), "POST person")
    if (i === 1) { face(person.id, photos[0].id); face(person.id, photos[1].id) }
    else if (i === 2) face(person.id, photos[0].id)
    else face(person.id, photos[i].id)
  }
}
