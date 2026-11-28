// Puts the test server into a known state for the Events flows (T-054, T-055) and Search (T-057).
// Runs on the computer, so it talks to the test server on localhost, never the family's server.
// Inputs (runScript env): BIRTH_DATE for Ola ("YYYY-MM-DD"); EVENTS=1 to ensure the filter events;
// SCHOOL=1 to remove everyone from School (test server only); SCHOOL=2 then adds family members Ola
// and Kuba with known timetables (Ola: Maths Mon, Art Tue; Kuba: Chess club Mon); SCHOOL_ADD=<title>
// adds a Monday lesson to Ola, as another phone would;
// PEOPLE=1 to ensure 14 Immich people "Osoba 01".."Osoba 14" on the test account (faces tagged by
// hand on its photos: 01 on two photos, 02 on the first of them, the others on one photo each);
// ALBUM=<name> to create an Immich album <name> with the newest photo on the test account (T-070);
// ALBUM_GONE=<name> to check no album <name> is left while that photo still is.
// REMINDER=<title> to put event <title> today, REMINDER_IN minutes from now (default 3, local time; T-039).
// SHOPPING=<title> to put a fresh list <title> with "Mleko", "Chleb", "Jajka" (T-040); sets
// output.reminderHour12 / reminderAmPm / reminderMinute (the time picker's 12-hour dial) and reminderLabel
// ("Today, 09:45"): the next 5-minute mark at least 2 minutes ahead, local time.
// BEST=1 to tag 3 photos of one month on the test account with ai/best/frame-worthy, as the Picfort
// server's photo AI would (T-094, T-097, T-101); output.bestMonth is that month ("July, 2025").
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
  school.children.forEach(function (c) {
    check(http.request(server + "/school/children/" + c.id, { method: "DELETE", headers: headers }), "DELETE child")
  })
}

function schoolPost(path, body) {
  return check(http.post(server + "/school" + path, { headers: headers, body: JSON.stringify(body) }), "POST " + path)
}

if (typeof SCHOOL !== "undefined" && SCHOOL === "2") {
  const ala = schoolPost("/children", { name: "Ola", color: "pink", immich_user_id: memberId("Ola") })
  check(http.request(server + "/school/children/" + ala.id + "/periods", {
    method: "PUT", headers: headers,
    body: JSON.stringify([{ number: 1, start_time: "08:00", end_time: "08:45" }, { number: 2, start_time: "08:55", end_time: "09:40" }]),
  }), "PUT periods")
  schoolPost("/children/" + ala.id + "/entries", { kind: "lesson", weekday: 1, period: 1, title: "Maths T029" })
  schoolPost("/children/" + ala.id + "/entries", { kind: "lesson", weekday: 2, period: 1, title: "Art T029" })
  const bartek = schoolPost("/children", { name: "Kuba", color: "blue", immich_user_id: memberId("Kuba") })
  schoolPost("/children/" + bartek.id + "/entries", {
    kind: "activity", weekday: 1, title: "Chess club T029", start_time: "15:00", end_time: "16:00",
  })
}

if (typeof SCHOOL_ADD !== "undefined" && SCHOOL_ADD) {
  const ala = check(http.get(server + "/school", { headers: headers }), "GET /school").children
    .find(function (c) { return c.immich_user_id === memberId("Ola") })
  schoolPost("/children/" + ala.id + "/entries", { kind: "lesson", weekday: 1, period: 2, title: SCHOOL_ADD })
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

if (typeof ALBUM !== "undefined" && ALBUM) {
  const albums = check(http.get(immich + "/api/albums", { headers: headers }), "GET albums")
  albums.filter(function (a) { return a.albumName === ALBUM }).forEach(function (a) {
    check(http.request(immich + "/api/albums/" + a.id, { method: "DELETE", headers: headers }), "DELETE old album")
  })
  const newest = check(http.post(immich + "/api/search/metadata", {
    headers: headers, body: JSON.stringify({ type: "IMAGE", size: 1, order: "desc" }),
  }), "search photos").assets.items[0]
  check(http.post(immich + "/api/albums", {
    headers: headers, body: JSON.stringify({ albumName: ALBUM, assetIds: [newest.id] }),
  }), "POST album")
  output.albumPhoto = newest.id
}

if (typeof ALBUM_GONE !== "undefined" && ALBUM_GONE) {
  const albums = check(http.get(immich + "/api/albums", { headers: headers }), "GET albums")
  if (albums.some(function (a) { return a.albumName === ALBUM_GONE })) throw new Error("Album " + ALBUM_GONE + " is still in Immich")
  if (typeof PHOTO !== "undefined" && PHOTO) {
    check(http.get(immich + "/api/assets/" + PHOTO, { headers: headers }), "the album's photo is still in Immich")
  }
}

if (typeof REMINDER !== "undefined" && REMINDER) {
  const minutes = typeof REMINDER_IN !== "undefined" && REMINDER_IN ? Number(REMINDER_IN) : 3
  const at = new Date(Date.now() + minutes * 60000)
  const two = function (n) { return (n < 10 ? "0" : "") + n }
  const day = at.getFullYear() + "-" + two(at.getMonth() + 1) + "-" + two(at.getDate())
  const body = { title: REMINDER, start_date: day, start_time: two(at.getHours()) + ":" + two(at.getMinutes()) }
  const upcoming = check(http.get(server + "/events?after=" + day, { headers: headers }), "GET /events")
  const found = upcoming.find(function (e) { return e.title === REMINDER })
  if (found) {
    check(http.request(server + "/events/" + found.id, { method: "PATCH", headers: headers, body: JSON.stringify(body) }), "PATCH " + REMINDER)
  } else {
    check(http.post(server + "/events", { headers: headers, body: JSON.stringify(body) }), "POST " + REMINDER)
  }
}

if (typeof SHOPPING !== "undefined" && SHOPPING) {
  const lists = check(http.get(server + "/lists", { headers: headers }), "GET /lists")
  lists.filter(function (l) { return l.title === SHOPPING }).forEach(function (l) {
    check(http.request(server + "/lists/" + l.id, { method: "DELETE", headers: headers }), "DELETE " + SHOPPING)
  })
  check(http.post(server + "/lists", {
    headers: headers,
    body: JSON.stringify({ title: SHOPPING, items: ["Mleko", "Chleb", "Jajka"] }),
  }), "POST " + SHOPPING)
  const at = new Date(Date.now() + 2 * 60000)
  at.setMinutes(Math.ceil(at.getMinutes() / 5) * 5, 0, 0)
  const two = function (n) { return (n < 10 ? "0" : "") + n }
  output.reminderHour12 = String(at.getHours() % 12 === 0 ? 12 : at.getHours() % 12)
  output.reminderAmPm = at.getHours() < 12 ? "AM" : "PM"
  output.reminderMinute = String(at.getMinutes())
  output.reminderLabel = (at.getDate() === new Date().getDate() ? "Today" : "Tomorrow") + ", " +
    two(at.getHours()) + ":" + two(at.getMinutes())
}

if (typeof BEST !== "undefined" && BEST === "1") {
  const tags = check(http.request(immich + "/api/tags", {
    method: "PUT", headers: headers, body: JSON.stringify({ tags: ["ai/best/frame-worthy"] }),
  }), "PUT /api/tags")
  const tag = tags.find(function (t) { return t.value === "ai/best/frame-worthy" })
  if (!tag) throw new Error("ai/best/frame-worthy tag missing after upsert")
  const recent = check(http.post(immich + "/api/search/metadata", {
    headers: headers, body: JSON.stringify({ type: "IMAGE", size: 200, order: "desc" }),
  }), "search newest photos").assets.items
  const byMonth = {}
  recent.forEach(function (a) {
    const month = (a.localDateTime || a.fileCreatedAt || "").substring(0, 7)
    if (!month) return
    if (!byMonth[month]) byMonth[month] = []
    byMonth[month].push(a.id)
  })
  // The newest month with at least 3 photos: the strip shows from 3 best photos (BEST_MIN).
  const month = Object.keys(byMonth).sort().reverse().find(function (m) { return byMonth[m].length >= 3 })
  if (!month) throw new Error("No month with 3 photos on the test account")
  check(http.request(immich + "/api/tags/assets", {
    method: "PUT", headers: headers,
    body: JSON.stringify({ tagIds: [tag.id], assetIds: byMonth[month].slice(0, 3) }),
  }), "tag best photos")
  const names = ["January", "February", "March", "April", "May", "June", "July", "August", "September",
    "October", "November", "December"]
  output.bestMonth = names[parseInt(month.substring(5, 7), 10) - 1] + ", " + month.substring(0, 4)
}
