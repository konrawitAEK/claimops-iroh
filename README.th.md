# ClaimOps ผ่าน iroh

[English](README.md) · **ภาษาไทย**

แอปเต็มระบบที่ใช้ [iroh-bridge](https://github.com/irohkit/iroh-bridge): ระบบจัดการเคลมประกันรถยนต์และทรัพย์สินในภูมิภาค APAC (frontend Angular 22,
backend Spring Boot, PostgreSQL, Kafka) โดย backend **ไม่เปิดพอร์ตเลย** เบราว์เซอร์เข้าถึง backend ได้ทาง
[iroh-gateway](docs/iroh-bridge.th.md#backend-iroh-gateway) เท่านั้น backend จึงจะอยู่หลัง NAT บนเครื่องอื่นก็ได้เหมือนกัน
ในแอปนี้ ผู้เคลมส่งเคลมและติดตามสถานะได้ ส่วนพนักงานรับเคลมไปดูแล ขอข้อมูลเพิ่ม แล้วอนุมัติจ่ายหรือปฏิเสธ

แอปนี้มาจาก [claimops-platform](https://github.com/konrawitAEK/claimops-platform) รายละเอียดการออกแบบอยู่ใน
[docs/ARCHITECTURE.th.md](docs/ARCHITECTURE.th.md) [สิ่งที่แก้เพื่อใช้ iroh](#สิ่งที่แก้เพื่อใช้-iroh) มีไม่มาก:
ฝั่ง frontend เพิ่มไฟล์ใหม่ไฟล์เดียว ส่วนโค้ด backend ไม่ได้แก้เลย

iroh ถูกดึงมาใช้แบบ import ทั้งหมด repo นี้ไม่มีซอร์สโค้ดของ iroh-bridge:

| ส่วน | มาจาก |
|---|---|
| `iroh-fetch` (ฝั่งเบราว์เซอร์และ e2e test) | npm แพ็กเกจ [`iroh-fetch`](https://www.npmjs.com/package/iroh-fetch) เวอร์ชัน 0.6.3 |
| `iroh-gateway` (ข้าง backend) | Docker image `ghcr.io/irohkit/iroh-gateway:0.6.3` |
| relay ของตัวเอง (ไม่บังคับ) | config ใน [deploy/relay](deploy/relay/README.md) ที่ copy มาจาก iroh-bridge |

เอกสารเพิ่มเติม: [คู่มือสำหรับมือใหม่](BEGINNERS.md) (NAT, relay, iroh คืออะไร) และ
[คู่มืออ้างอิง iroh-bridge ภาษาไทย](docs/iroh-bridge.th.md) (ตัวเลือกทั้งหมดของ `iroh-fetch` และ `iroh-gateway`)

```
 เบราว์เซอร์ (host ไหนก็ได้)                                  docker compose (อยู่หลัง NAT ได้)
┌──────────────────────┐            ┌───────┐              ┌──────────────┐  HTTP   ┌─────────┐   ┌──────────┐
│ Angular + iroh-fetch │ ─────────▶ │ relay │ ◀─────────── │ iroh-gateway │ ──────▶ │ backend │ ─▶│ Postgres │
│ HttpClient → /api/** │  WebSocket └───────┘    ต่อออก     │  เฉพาะ /api   │  :8080  │ Spring  │   │ Kafka    │
└──────────────────────┘                                   └──────────────┘         └─────────┘   └──────────┘
        ▲ ไฟล์ static (index.html, JS, iroh.json, .wasm) มาจาก container frontend ที่พอร์ต 4200
```

## วิธีรัน

ต้องมี Docker แล้วรันจากโฟลเดอร์นี้:

```bash
docker compose up --build -d
```

เปิด <http://localhost:4200> ใช้แถบด้านบนสลับระหว่างมุมมองผู้เคลม (Claimant) กับพนักงาน (Staff) รวมถึงเปลี่ยนผู้ใช้และตลาด

| Service | ที่อยู่ |
|---|---|
| แอป (Angular) | <http://localhost:4200> |
| Backend | ไม่เปิดพอร์ต เข้าถึงได้ผ่าน gateway เท่านั้น (ดู [เรียก API จากเครื่องเรา](#เรียก-api-จากเครื่องเรา)) |
| Gateway | ดู `IROH_ENDPOINT_ID=...` ได้จาก `docker compose logs gateway` |
| PostgreSQL | `localhost:5432` ชื่อฐานข้อมูล ผู้ใช้ และรหัสผ่านเป็น `claimops` ทั้งหมด |
| Kafka | `localhost:9092` จากเครื่อง host |

ตอนเริ่มทำงาน container frontend จะถาม endpoint id กับ relay จาก gateway (ผ่าน admin `/status` ของ gateway
ซึ่งเข้าได้เฉพาะใน network ของ compose) แล้วเขียนลงไฟล์ `/iroh.json` หน้าเว็บจะอ่านไฟล์นี้ ต่อไปหา gateway ให้เสร็จก่อน
แล้วค่อยเริ่ม Angular คำขอ API แรกจึงวิ่งผ่าน iroh ตั้งแต่ต้น การเชื่อมต่อใช้เวลาราวหนึ่งถึงสองวินาที
ระหว่างนั้นหน้าเว็บจะแสดงข้อความ "Connecting to ClaimOps…"

ถ้าอยากเช็กว่าคำขอ API วิ่งผ่าน iroh จริง ให้ดูแท็บ Network ของเบราว์เซอร์ จะไม่มีคำขอไปที่ `/api` เลย
ส่วน gateway จะนับคำขอเหล่านี้ไว้:

```bash
docker compose exec frontend wget -qO- http://gateway:9090/metrics | grep requests_total
```

| งาน | คำสั่ง |
|---|---|
| ดูสถานะ | `docker compose ps` |
| ดู log ของ backend | `docker compose logs -f backend` |
| build ใหม่เฉพาะ service ที่แก้โค้ด | `docker compose up -d --build backend` (หรือ `frontend`) |
| หยุดโดยเก็บข้อมูลไว้ | `docker compose down` |
| ล้างทุกอย่าง (gateway จะได้ endpoint id ใหม่ด้วย) | `docker compose down -v && docker compose up --build -d` |

กุญแจลับของ gateway อยู่ใน volume `gateway-data` ถ้าเก็บ volume ไว้ endpoint id จะเหมือนเดิม แต่ `down -v` จะลบมันทิ้ง
แล้ว gateway จะได้ id ใหม่ (frontend จะอ่าน id ใหม่เองตอนเริ่มครั้งถัดไป)

### ล้างเฉพาะฐานข้อมูล

เลขเคลมจะเริ่มนับใหม่ที่ 000001 Kafka ยังเก็บ event เก่าไว้ แต่ consumer อ่านผ่านไปแล้ว จึงไม่ถูกเล่นซ้ำ

```bash
docker compose exec postgres psql -U claimops -c "TRUNCATE claims, info_requests, liability_exposures, claim_audit_log, processed_events; ALTER SEQUENCE claim_number_seq RESTART;"
```

## เรียก API จากเครื่องเรา

backend ไม่เปิดพอร์ต จึงต้องใช้ [`iroh-proxy`](docs/iroh-bridge.th.md#local-port-iroh-proxy) ซึ่งเปิด gateway ให้เป็นพอร์ตบนเครื่องเรา
ใช้กับ curl, Postman หรือโปรแกรม HTTP อะไรก็ได้:

```bash
npx -y -p iroh-fetch iroh-proxy <endpoint id> --port 8787

curl -H 'X-User-Role: STAFF' -H 'X-User-Name: bob' http://127.0.0.1:8787/api/staff/dashboard
```

gateway ปล่อยผ่านเฉพาะ `/api` (ตั้งด้วย `IROH_ALLOW_PATHS` ใน [docker-compose.yml](docker-compose.yml)) Swagger UI จึงตอบ 403
ถ้าต้องการใช้ ให้เพิ่ม `/swagger-ui,/v3/api-docs` เข้าไปใน `IROH_ALLOW_PATHS` แล้วเปิด
<http://127.0.0.1:8787/swagger-ui.html> หรือจะกลับไปเปิดพอร์ตของ backend ระหว่างพัฒนาก็ได้

## วาง frontend ไว้ที่อื่น

container frontend ทำหน้าที่แค่เสิร์ฟไฟล์ static และไม่ต้องต่อถึง backend จึงย้ายไปรันบนเครื่องอื่นได้
หรือจะเอาผล build (`npm run build` ได้ไฟล์ใน `dist/browser`) ไปวางบน static host ไหนก็ได้ ขอแค่มีไฟล์ `/iroh.json`
อยู่ข้าง `index.html`:

```json
{ "endpointId": "<endpoint id ของ gateway>", "relayUrl": "https://euc1-1.relay.n0.iroh.link" }
```

| ฟิลด์ | |
|---|---|
| `endpointId` | ต้องมี: endpoint id ของ gateway |
| `relayUrl` | ไม่บังคับ: relay ที่ gateway ต่ออยู่ ช่วยให้เชื่อมต่อครั้งแรกเร็วขึ้น |
| `clientRelayUrl` | ไม่บังคับ: relay ของเราเองให้เบราว์เซอร์ใช้ แทน relay สาธารณะของ n0 |
| `relayToken` | ไม่บังคับ: token ของ relay นั้น ซึ่งจะเปิดเผยในไฟล์นี้ วิธีที่ดีกว่าดูที่ [รับ relay token จาก backend](docs/iroh-bridge.th.md#relay-token-from-your-backend) |

ถ้าใช้ Docker image ให้ตั้งเป็น environment variable แทน (`IROH_ENDPOINT_ID`, `IROH_GATEWAY_RELAY_URL`,
`IROH_RELAY_URL`, `IROH_RELAY_TOKEN`) ดูรายละเอียดใน
[docker-entrypoint.d/40-iroh-config.sh](frontend/docker-entrypoint.d/40-iroh-config.sh)

ฝั่งเครื่อง backend ให้รันทุก service ใน [docker-compose.yml](docker-compose.yml) ยกเว้น `frontend`

## ใช้ relay ของตัวเอง

ค่าเริ่มต้นใช้ relay สาธารณะของ n0 ซึ่งเหมาะกับการพัฒนา แต่ไม่มี SLA และอาจถูกจำกัดการใช้งาน
สำหรับใช้งานจริงให้ตั้ง relay ของตัวเองด้วยไฟล์ใน [deploy/relay](deploy/relay/README.md)
(มีสคริปต์ติดตั้ง, systemd unit และ docker-compose) แล้วตั้งค่าเพิ่มใน [docker-compose.yml](docker-compose.yml):

- service `gateway`: `IROH_RELAY_URL` และ `IROH_RELAY_TOKEN` (token สำหรับ gateway)
- service `frontend`: `IROH_RELAY_URL` (ให้เบราว์เซอร์ใช้ relay เดียวกัน) และ `IROH_RELAY_TOKEN` ถ้า relay ต้องใช้ token

รายละเอียดดูที่ [รัน relay ของคุณเอง](docs/iroh-bridge.th.md#run-your-own-relay)

## พัฒนา

เปิดฝั่ง backend ใน Docker (ไม่ต้องเปิด container frontend):

```bash
docker compose up -d postgres kafka backend gateway
```

**Frontend dev server** ที่ <http://localhost:4200> ต้องใช้ Node 22.22.3 ขึ้นไป ถ้า container frontend รันอยู่
ให้หยุดก่อนด้วย `docker compose stop frontend`

```bash
cd frontend && npm install && npm start
```

ถ้าไม่มีไฟล์ `public/iroh.json` คำขอ API จะไปที่ `/api` ตรงๆ แล้ว `ng serve` จะส่งต่อไปที่ `localhost:8080`
([proxy.conf.json](frontend/proxy.conf.json)) วิธีนี้ใช้ได้เฉพาะตอนเปิดพอร์ตของ backend หรือรัน backend บนเครื่องเอง
(`cd backend && ./mvnw spring-boot:run` ต้องใช้ JDK 21) ถ้าจะให้วิ่งผ่าน iroh ให้สร้าง `frontend/public/iroh.json`
ตาม[ตัวอย่างด้านบน](#วาง-frontend-ไว้ที่อื่น) (ไฟล์นี้ถูก git ignore ไว้)

## ทดสอบ

| ชุดทดสอบ | คำสั่ง | ต้องมี |
|---|---|---|
| unit test ของ backend (15 ข้อ) | `cd backend && ./mvnw test` | JDK 21 (ไม่ต้องมี Docker ฐานข้อมูล หรือ Kafka) |
| type check และ production build ของ frontend | `cd frontend && npm run build` | Node 22.22.3 ขึ้นไป |
| **e2e ผ่าน iroh** (20 ข้อ) | `cd e2e && npm ci && npm test` | stack ที่รันอยู่ (`docker compose up --build -d`) และ Node 22 ขึ้นไป |

ถ้าไม่มี JDK ในเครื่อง รัน test ของ backend ใน container ได้:

```bash
docker run --rm -v "$PWD/backend":/w -w /w eclipse-temurin:21-jdk ./mvnw -B test
```

### e2e ผ่าน iroh

[e2e/claimops.test.js](e2e/claimops.test.js) เรียก API ของ ClaimOps ด้วย `iroh-fetch` ทุกคำขอวิ่งผ่าน relay และ
gateway เส้นทางเดียวกับเบราว์เซอร์ จึงทดสอบทั้งแอปและการเชื่อมต่อผ่าน iroh ไปพร้อมกัน ครอบคลุม:

- **วงจรชีวิตของเคลม:** ผู้เคลมส่งเคลม → พนักงานรับเรื่อง → ขอข้อมูลเพิ่ม → ผู้เคลมตอบ → อนุมัติจ่าย
  และเคลมอีกตัวที่ถูกปฏิเสธ
- **กฎทางธุรกิจ:** อนุมัติก่อนรับเรื่องไม่ได้ (409), จ่ายเกินยอดที่เคลมไม่ได้ (422), สกุลเงินต้องตรงกับตลาดหรือเป็น USD (422),
  เคลมรถต้องมีรายละเอียดครบ (422), ข้อมูลไม่ถูกต้อง (400), เคลมที่ปิดแล้วเปลี่ยนไม่ได้ (409)
- **สิทธิ์:** ไม่มี header ตัวตนได้ 401, ผู้เคลมเรียก API ของพนักงานไม่ได้ (403), พนักงานที่ไม่ได้รับเรื่องทำรายการไม่ได้ (403),
  ผู้เคลมคนอื่นมองไม่เห็นเคลมของเรา (404)
- **Kafka:** audit trail บันทึกครบทุกขั้นตอน (รอจนกว่า event จะถูก project เสร็จ)
- **gateway:** ปล่อยผ่านเฉพาะ `/api` ส่วน Swagger UI และ actuator ได้ 403

test อ่าน endpoint id ของ gateway จาก `http://localhost:4200/iroh.json` หรือกำหนดเองด้วย `IROH_ENDPOINT_ID`
แต่ละรอบใช้ชื่อผู้ใช้ที่สุ่มขึ้นใหม่ จึงรันซ้ำบนฐานข้อมูลเดิมได้

### CI

[.github/workflows/ci.yml](.github/workflows/ci.yml) รันทั้งสามชุดทุกครั้งที่ push เข้า `main` และทุก pull request
ชุด e2e จะสร้าง stack ด้วย Docker บน runner แล้วทดสอบผ่าน relay สาธารณะของ n0

## ความปลอดภัย

- **backend เชื่อ header `X-User-Role` / `X-User-Name` ตามที่ส่งมาทุกอย่าง** ต้นฉบับตั้งใจทำแบบนี้เพื่อความเร็ว
  เพราะตอนนั้น API อยู่แค่บน localhost แต่เมื่อผ่าน iroh ใครที่รู้ endpoint id ของ gateway (ซึ่งเปิดเผยใน `/iroh.json`)
  ก็เรียก API โดยอ้างตัวเป็นพนักงานคนไหนก็ได้ เหมือนกับ URL สาธารณะทุกประการ ก่อนใช้งานจริงต้องเปลี่ยน
  [HeaderAuthInterceptor](backend/src/main/java/com/chubb/claimops/common/auth/HeaderAuthInterceptor.java)
  เป็นระบบยืนยันตัวตนจริง เช่น OAuth2/JWT (iroh-fetch ส่ง header เหล่านี้ผ่านไปตามเดิม) นอกจากนี้ backend
  ยังได้รับ `X-Iroh-Remote-Id` ซึ่งเป็น endpoint id ของผู้เรียกที่ยืนยันแล้ว ใช้ทำ rate limit หรือ audit log ได้
- gateway ส่งต่อเฉพาะ `/api` (`IROH_ALLOW_PATHS`) Swagger UI และ actuator จึงยังอยู่ภายใน
- เก็บ volume `gateway-data` เป็นความลับ ใครได้กุญแจไปจะปลอมตัวเป็น backend ได้

## สิ่งที่แก้เพื่อใช้ iroh

เทียบกับ [claimops-platform](https://github.com/konrawitAEK/claimops-platform):

| ไฟล์ | สิ่งที่เปลี่ยน |
|---|---|
| [frontend/src/app/core/iroh.ts](frontend/src/app/core/iroh.ts) | **ใหม่** อ่าน `/iroh.json` เชื่อมต่อด้วย `createIrohFetch` แล้ว `install()` ให้ดักเฉพาะ `/api/**` บน origin ของหน้าเว็บ HttpClient ใช้ `fetch` อยู่แล้ว (`withFetch()`) service และหน้าต่างๆ จึงไม่ต้องแก้ |
| [frontend/src/main.ts](frontend/src/main.ts) | เชื่อมต่อให้เสร็จก่อนเริ่ม Angular ถ้าต่อ gateway ไม่ได้จะแสดง error บนหน้าเว็บ |
| [frontend/angular.json](frontend/angular.json) | copy ไฟล์ `.wasm` ของ iroh-fetch ไปไว้ที่ `/wasm/` |
| [frontend/package.json](frontend/package.json) | เพิ่ม `iroh-fetch` |
| [frontend/nginx.conf](frontend/nginx.conf) | เลิก proxy `/api` ไปหา backend แล้ว เสิร์ฟ `/iroh.json` (ไม่ cache) และไฟล์ `.wasm` แทน |
| [frontend/Dockerfile](frontend/Dockerfile), [40-iroh-config.sh](frontend/docker-entrypoint.d/40-iroh-config.sh) | เขียน `/iroh.json` ตอน container เริ่ม จาก environment หรือจาก `/status` ของ gateway |
| [frontend/src/index.html](frontend/src/index.html), [styles.scss](frontend/src/styles.scss) | ข้อความ "Connecting…" และสไตล์ของ error ระหว่างเชื่อมต่อ |
| [docker-compose.yml](docker-compose.yml) | เพิ่ม service `gateway` backend ไม่เปิดพอร์ต 8080 แล้ว และ frontend รอ gateway ก่อน |
| backend | ไม่ได้แก้ |
| [e2e/](e2e/claimops.test.js) | **ใหม่** e2e test ที่เรียก API ผ่าน iroh |
| [.github/workflows/ci.yml](.github/workflows/ci.yml) | **ใหม่** รัน test ของ backend, build frontend และ e2e |
| [deploy/relay/](deploy/relay/README.md) | **ใหม่** config สำหรับตั้ง relay ของตัวเอง (มาจาก iroh-bridge) |
