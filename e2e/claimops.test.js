// End-to-end tests of ClaimOps over iroh: every request goes iroh-fetch -> relay -> iroh-gateway -> backend, the
// same path the browser takes. Needs the stack running (`docker compose up --build -d` in the repo root).
//
//   cd e2e && npm ci && npm test
//
// The gateway's endpoint id comes from IROH_ENDPOINT_ID, else from the frontend's /iroh.json (FRONTEND_URL,
// default http://localhost:4200), which the frontend container writes on start.
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { after, before, describe, test } from "node:test";
import { createIrohFetch } from "iroh-fetch";

const FRONTEND_URL = process.env.FRONTEND_URL ?? "http://localhost:4200";
// Each run uses its own claimant and handler names, so runs don't see each other's claims.
const run = randomUUID().slice(0, 8);
const CLAIMANT = { role: "CLAIMANT", name: `e2e-claimant-${run}` };
const OTHER_CLAIMANT = { role: "CLAIMANT", name: `e2e-other-${run}` };
const STAFF = { role: "STAFF", name: `e2e-staff-${run}` };
const OTHER_STAFF = { role: "STAFF", name: `e2e-staff2-${run}` };

let iroh;

async function gatewayConfig() {
  if (process.env.IROH_ENDPOINT_ID) {
    return { endpointId: process.env.IROH_ENDPOINT_ID, relayUrl: process.env.IROH_GATEWAY_RELAY_URL };
  }
  const deadline = Date.now() + 120_000;
  for (;;) {
    try {
      const res = await fetch(`${FRONTEND_URL}/iroh.json`, { cache: "no-store" });
      if (res.ok && res.headers.get("content-type")?.includes("json")) return await res.json();
    } catch {}
    if (Date.now() > deadline) throw new Error(`no gateway endpoint id: set IROH_ENDPOINT_ID or start the stack (${FRONTEND_URL}/iroh.json)`);
    await new Promise((r) => setTimeout(r, 2000));
  }
}

/** Calls the ClaimOps API over iroh as `who`; returns { status, body }. */
async function api(who, method, path, body) {
  const headers = { "X-User-Role": who.role, "X-User-Name": who.name };
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const res = await iroh.fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  const text = await res.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {}
  return { status: res.status, body: json, text, headers: res.headers };
}

/** Polls until `check` returns a truthy value: for state that Kafka projects asynchronously. */
async function eventually(check, what, timeoutMs = 30_000) {
  const deadline = Date.now() + timeoutMs;
  for (;;) {
    const value = await check();
    if (value) return value;
    if (Date.now() > deadline) throw new Error(`timed out waiting for ${what}`);
    await new Promise((r) => setTimeout(r, 500));
  }
}

const today = () => new Date().toISOString().slice(0, 10);

function motorClaim(overrides = {}) {
  return {
    policyNumber: `POL-TH-MOT-${run}`,
    claimType: "MOTOR",
    market: "TH",
    currency: "THB",
    incidentDate: today(),
    incidentDescription: "Rear bumper scratched in a parking lot (e2e over iroh).",
    incidentDetails: { licensePlate: "1กข 1234", vehicleModel: "Toyota Yaris" },
    claimedAmount: 12500,
    ...overrides,
  };
}

before(async () => {
  const { endpointId, relayUrl } = await gatewayConfig();
  assert.match(endpointId, /^[0-9a-f]{64}$/, "gateway endpoint id");
  iroh = await createIrohFetch({
    endpointIds: [{ endpointId, relayUrl: relayUrl || undefined }],
    connectTimeout: 60_000,
  });
});

after(() => iroh?.close());

describe("claim lifecycle over iroh", () => {
  let claim;

  test("a claimant submits a motor claim", async () => {
    const res = await api(CLAIMANT, "POST", "/api/claimant/claims", motorClaim());
    assert.equal(res.status, 201, res.text);
    claim = res.body;
    assert.match(claim.claimNumber, /^CLM-TH-\d{4}-\d{6}$/);
    assert.equal(claim.status, "SUBMITTED");
    assert.equal(claim.claimantName, CLAIMANT.name);
    assert.equal(Number(claim.claimedAmount), 12500);
    assert.deepEqual(claim.incidentDetails, { licensePlate: "1กข 1234", vehicleModel: "Toyota Yaris" });
    assert.equal(res.headers.get("location"), `/api/claimant/claims/${claim.id}`);
  });

  test("the claimant finds it in their list and by number", async () => {
    const list = await api(CLAIMANT, "GET", "/api/claimant/claims");
    assert.equal(list.status, 200);
    assert.ok(list.body.some((c) => c.id === claim.id));

    const byNumber = await api(CLAIMANT, "GET", `/api/claimant/claims/by-number/${claim.claimNumber}`);
    assert.equal(byNumber.status, 200);
    assert.equal(byNumber.body.id, claim.id);
  });

  test("another claimant can't see it", async () => {
    const res = await api(OTHER_CLAIMANT, "GET", `/api/claimant/claims/by-number/${claim.claimNumber}`);
    assert.equal(res.status, 404);
  });

  test("it is in the staff queue as SUBMITTED", async () => {
    const res = await api(STAFF, "GET", "/api/staff/claims?status=SUBMITTED&market=TH");
    assert.equal(res.status, 200);
    assert.ok(res.body.some((c) => c.id === claim.id));
  });

  test("settling before pickup is refused (409)", async () => {
    const res = await api(STAFF, "POST", `/api/staff/claims/${claim.id}/settlement`, { settledAmount: 100 });
    assert.equal(res.status, 409, res.text);
  });

  test("staff picks it up", async () => {
    const res = await api(STAFF, "POST", `/api/staff/claims/${claim.id}/pickup`);
    assert.equal(res.status, 200, res.text);
    assert.equal(res.body.status, "IN_REVIEW");
    assert.equal(res.body.assignedHandler, STAFF.name);
  });

  test("only the assigned handler may act on it (403)", async () => {
    const res = await api(OTHER_STAFF, "POST", `/api/staff/claims/${claim.id}/info-requests`, { question: "?" });
    assert.equal(res.status, 403, res.text);
  });

  test("staff asks for information and the claimant answers", async () => {
    const asked = await api(STAFF, "POST", `/api/staff/claims/${claim.id}/info-requests`, {
      question: "Please send a photo of the damage.",
    });
    assert.equal(asked.status, 200, asked.text);
    assert.equal(asked.body.status, "INFO_REQUESTED");

    const seen = await api(CLAIMANT, "GET", `/api/claimant/claims/${claim.id}`);
    assert.equal(seen.body.status, "INFO_REQUESTED");
    assert.equal(seen.body.infoRequests.at(-1).question, "Please send a photo of the damage.");

    const answered = await api(CLAIMANT, "POST", `/api/claimant/claims/${claim.id}/info-responses`, {
      answer: "Photo attached to the email (ใช้ภาษาไทยได้ด้วย).",
    });
    assert.equal(answered.status, 200, answered.text);
    assert.equal(answered.body.status, "IN_REVIEW");
    assert.equal(answered.body.infoRequests.at(-1).answer, "Photo attached to the email (ใช้ภาษาไทยได้ด้วย).");
  });

  test("settling above the claimed amount is refused (422)", async () => {
    const res = await api(STAFF, "POST", `/api/staff/claims/${claim.id}/settlement`, { settledAmount: 99999 });
    assert.equal(res.status, 422, res.text);
  });

  test("staff settles it", async () => {
    const res = await api(STAFF, "POST", `/api/staff/claims/${claim.id}/settlement`, {
      settledAmount: 12000,
      note: "Approved minus excess.",
    });
    assert.equal(res.status, 200, res.text);
    assert.equal(res.body.status, "SETTLED");
    assert.equal(Number(res.body.settledAmount), 12000);

    const seen = await api(CLAIMANT, "GET", `/api/claimant/claims/by-number/${claim.claimNumber}`);
    assert.equal(seen.body.status, "SETTLED");
  });

  test("a settled claim can't be rejected (409)", async () => {
    const res = await api(STAFF, "POST", `/api/staff/claims/${claim.id}/rejection`, { reason: "too late" });
    assert.equal(res.status, 409, res.text);
  });

  test("the audit trail records every step (projected from Kafka)", async () => {
    const entries = await eventually(async () => {
      const res = await api(STAFF, "GET", `/api/staff/claims/${claim.id}/audit`);
      assert.equal(res.status, 200, res.text);
      return res.body.length >= 5 ? res.body : null;
    }, "five audit entries");
    const sorted = [...entries].sort((a, b) => a.occurredAt.localeCompare(b.occurredAt));
    assert.deepEqual(
      sorted.map((e) => e.newStatus),
      ["SUBMITTED", "IN_REVIEW", "INFO_REQUESTED", "IN_REVIEW", "SETTLED"],
    );
    assert.equal(sorted[0].eventType, "CLAIM_SUBMITTED");
    assert.equal(sorted.at(-1).eventType, "CLAIM_DECIDED");
    assert.equal(sorted.at(-1).actor, STAFF.name);
  });
});

describe("rejection and validation", () => {
  test("a property claim can be picked up and rejected", async () => {
    const submitted = await api(CLAIMANT, "POST", "/api/claimant/claims", {
      policyNumber: `POL-SG-PRP-${run}`,
      claimType: "PROPERTY",
      market: "SG",
      currency: "USD",
      incidentDate: today(),
      incidentDescription: "Water leak from the unit above.",
      incidentDetails: { propertyAddress: "1 Example Road, Singapore", damageCategory: "Water" },
      claimedAmount: 800,
    });
    assert.equal(submitted.status, 201, submitted.text);
    const id = submitted.body.id;
    assert.equal((await api(STAFF, "POST", `/api/staff/claims/${id}/pickup`)).status, 200);
    const rejected = await api(STAFF, "POST", `/api/staff/claims/${id}/rejection`, { reason: "Not covered by the policy." });
    assert.equal(rejected.status, 200, rejected.text);
    assert.equal(rejected.body.status, "REJECTED");
  });

  test("a currency that isn't the market's or USD is refused (422)", async () => {
    const res = await api(CLAIMANT, "POST", "/api/claimant/claims", motorClaim({ currency: "JPY" }));
    assert.equal(res.status, 422, res.text);
  });

  test("a motor claim without its required details is refused (422)", async () => {
    const res = await api(CLAIMANT, "POST", "/api/claimant/claims", motorClaim({ incidentDetails: { licensePlate: "X" } }));
    assert.equal(res.status, 422, res.text);
  });

  test("an invalid body is refused (400)", async () => {
    const res = await api(CLAIMANT, "POST", "/api/claimant/claims", motorClaim({ claimedAmount: -5 }));
    assert.equal(res.status, 400, res.text);
  });
});

describe("identity and gateway rules", () => {
  test("calls without identity headers get 401", async () => {
    const res = await iroh.fetch("/api/claimant/claims");
    assert.equal(res.status, 401);
  });

  test("a claimant can't call staff endpoints (403)", async () => {
    const res = await api(CLAIMANT, "GET", "/api/staff/dashboard");
    assert.equal(res.status, 403);
  });

  test("the staff dashboard answers over iroh", async () => {
    const res = await api(STAFF, "GET", "/api/staff/dashboard");
    assert.equal(res.status, 200, res.text);
    assert.ok(Array.isArray(res.body.exposures));
    assert.ok(res.body.workload);
  });

  test("the gateway only lets /api through (Swagger UI and actuator get 403)", async () => {
    for (const path of ["/swagger-ui.html", "/v3/api-docs", "/actuator/health"]) {
      const res = await iroh.fetch(path);
      assert.equal(res.status, 403, path);
      await res.body?.cancel();
    }
  });
});
