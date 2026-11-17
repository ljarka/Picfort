// Puts the fake school-server (maestro/scripts/fake-school-server.py, localhost:5101) into a
// known state for the school journal flows (T-076…T-081). Never the family's school-server.
// Inputs (runScript env): RESET=1 back to the sample data; MESSAGE=<subject> a new message for
// Kuba ("Coach", with a link); GRADE=<value> a new Mathematics grade for Ola (Test, weight 2);
// OFFLINE=<seconds> no answers for that long (0 ends it).
const base = "http://localhost:5101/_test/"

function hook(name, body) {
  const res = http.post(base + name, {
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  })
  if (!res.ok) throw new Error("fake school-server " + name + ": HTTP " + res.status + " (is it running? python3 maestro/scripts/fake-school-server.py)")
}

if (typeof RESET !== "undefined" && RESET) hook("reset", {})
if (typeof MESSAGE !== "undefined" && MESSAGE) {
  hook("message", {
    childId: "102",
    sender: "Coach",
    subject: MESSAGE,
    html: "<p>The match is on <b>Saturday</b> at 10:00. Map: <a href=\"https://example.com/map\">example.com/map</a></p>",
  })
}
if (typeof GRADE !== "undefined" && GRADE) {
  hook("grade", { childId: "101", subject: "Mathematics", value: GRADE, category: "Test", weight: 2 })
}
if (typeof OFFLINE !== "undefined" && OFFLINE !== "") hook("offline", { seconds: Number(OFFLINE) })
