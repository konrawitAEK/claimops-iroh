# สถาปัตยกรรม (ฉบับภาษาไทย)

> ฉบับภาษาไทยไว้อ่านประกอบ ฉบับหลักคือ [ARCHITECTURE.md](ARCHITECTURE.md)

## ภาพรวม

ระบบยื่นและตรวจสอบเคลมสำหรับ Chubb APAC ทั้ง backend (`backend/`) และ frontend Angular (`frontend/`) รันด้วย `docker compose up --build`

**design เปลี่ยนจากแผนแรก:** แผนแรก (journal รายการ 11:17) ถูกปรับตอนเริ่ม build (รายการ 11:32)
- ตัดสถานะ `APPROVED` ออก
- เปลี่ยนจาก JWT เป็นระบุตัวตนผ่าน header
- เปลี่ยนชื่อ package และชื่อ topic

เอกสารนี้อธิบายระบบตามที่สร้างจริง

## Domain

| Entity | หน้าที่ |
|---|---|
| **Claim** | ตัวหลัก (aggregate root) เก็บประเภทเคลม, ประเทศ, สกุลเงิน, วันและรายละเอียดเหตุการณ์, ยอดที่ขอและยอดที่ settle, เจ้าหน้าที่ที่รับงาน, สถานะ และ `version` สำหรับ optimistic lock |
| **InfoRequest** | คำถามที่เจ้าหน้าที่ส่งถึงลูกค้า และคำตอบของลูกค้า อยู่ใต้ Claim และแก้ได้ผ่าน Claim เท่านั้น |
| **Incident details** | ฟิลด์ที่ต่างกันตามประเภทเคลม เก็บเป็น JSONB<br>• MOTOR: `licensePlate`, `vehicleModel`<br>• PROPERTY: `propertyAddress`, `damageCategory`<br>รายชื่อฟิลด์ที่ต้องมีอยู่ใน `ClaimType` ถ้าจะเพิ่มฟิลด์ แก้แค่ enum กับฟอร์ม ไม่ต้องแก้ schema |
| **ClaimEvent** | ข้อมูลที่ส่งเข้า Kafka (`chubb.claims.events`) มีข้อมูลที่ผู้รับต้องใช้ครบในตัว ผู้รับจึงไม่ต้องอ่านตารางของ claim |

**ประเทศและสกุลเงิน:** SG, TH, MY, HK, AU, JP ยื่นเคลมเป็นสกุลเงินท้องถิ่น (ค่าเริ่มต้น) หรือ USD ก็ได้ ระบบไม่แปลงค่าเงิน

**เลขกรมธรรม์:** หน้าเว็บกรอกให้อัตโนมัติและแก้ไม่ได้ (`utils/policy.util.ts`)
- คำนวณจากชื่อลูกค้า ประเทศ และประเภทเคลม ใส่ค่าเดิมได้เลขเดิมทุกครั้ง จึงดูเหมือนกรมธรรม์ที่มีอยู่แล้ว
- ใช้แทนการค้นจากระบบจัดการกรมธรรม์จริงที่ไม่ได้ทำ
- ไม่สร้างเลขใหม่ทุกเคลม เพราะกรมธรรม์ต้องมีก่อนเคลม

### สถานะของเคลม

```
SUBMITTED ──▶ IN_REVIEW ──▶ SETTLED
                │  ▲    └──▶ REJECTED
                ▼  │
           INFO_REQUESTED
```

| คำสั่ง | ทำได้จากสถานะ | ไปสถานะ | ใครทำได้ |
|---|---|---|---|
| pickUp (รับงาน) | SUBMITTED | IN_REVIEW | เจ้าหน้าที่คนไหนก็ได้ คนที่กดจะกลายเป็นผู้รับผิดชอบ |
| requestInfo (ขอข้อมูลเพิ่ม) | IN_REVIEW | INFO_REQUESTED | คนที่รับงานเท่านั้น |
| provideInfo (ลูกค้าตอบ) | INFO_REQUESTED | IN_REVIEW | เจ้าของเคลมเท่านั้น |
| settle | IN_REVIEW | SETTLED | คนที่รับงาน ยอดต้องมากกว่า 0 และไม่เกินยอดที่ขอ |
| reject | IN_REVIEW | REJECTED | คนที่รับงาน ต้องใส่เหตุผล |

- **ตารางสถานะอยู่ที่เดียว:** `ClaimStatus.allowedNext()` เก็บทุกเส้นที่เปลี่ยนสถานะได้ `Claim` เช็กตารางนี้ก่อนเปลี่ยนสถานะทุกครั้ง และไม่มี setter โค้ดส่วนอื่นจึงข้ามการเช็กไม่ได้
- **แต่ละคำสั่งมีสถานะต้นทางเดียว:** ถ้าเช็กแค่ตาราง เจ้าหน้าที่อีกคนจะกดรับงานเคลมที่รอลูกค้าตอบได้ เพราะ `INFO_REQUESTED → IN_REVIEW` เป็นเส้นที่อนุญาต test ที่ลองทุกคำสั่งจากทุกสถานะจับบั๊กนี้ได้ ตอนนี้แก้แล้วและมี regression test คุม

## Backend: modular monolith

Spring Boot 3.5 (Java 21) เป็น service เดียว แบ่งเป็น 3 package

| Package | หน้าที่ |
|---|---|
| `claim` | ฝั่งเขียน (write model) มี REST API, state machine, JPA entity และส่ง event |
| `exposure` | ฝั่งอ่าน (read model) รับ event จาก Kafka มาอัปเดต `liability_exposures` กับ `claim_audit_log` และให้ข้อมูล dashboard |
| `common` | ระบุตัวตนจาก header, แปลง error เป็น Problem Details, config ของ Kafka และนาฬิกา |

dependency ไปทางเดียว คือ `exposure` → `claim` ฝั่ง claim ไม่รู้จักฝั่งรายงานเลย

### ทำไมใช้ REST กับ Kafka แบบนี้

| ช่องทาง | ใช้กับ | เหตุผล |
|---|---|---|
| **REST** | ยื่นเคลม, รับงาน, ขอข้อมูล, ตอบ, settle, reject และการอ่านทั้งหมด | ผู้ใช้ต้องรู้ผลทันที เช่น ได้เลขเคลม หรือได้ 409 |
| **Kafka** `chubb.claims.events` | `CLAIM_SUBMITTED`, `CLAIM_STATUS_CHANGED`, `CLAIM_DECIDED` ส่งไปอัปเดตยอด exposure และ audit trail | งานต่อเนื่องไม่ควรทำให้ request ของเจ้าหน้าที่ช้าหรือล้มตาม และถ้าภายหลังจะเพิ่มระบบแจ้งเตือนหรือตรวจ fraud แค่เพิ่ม consumer ไม่ต้องแก้ฝั่งเขียน |

### ความถูกต้องของข้อมูล

1. **Optimistic locking:** `Claim` มี `@Version` ถ้าเจ้าหน้าที่ 2 คนกดรับงานเคลมเดียวกันพร้อมกัน คนแรกสำเร็จ อีกคนได้ **409 Concurrent update** ทดสอบกับระบบจริงแล้ว
2. **ส่ง event หลัง commit:** ใช้ `@TransactionalEventListener(AFTER_COMMIT)` ถ้า transaction ถูก rollback จะไม่มี event ออกไป
   - **ช่องโหว่ที่รู้อยู่:** การ commit กับการส่ง event ไม่ได้เกิดพร้อมกัน ถ้าระบบล่มตรงกลาง event จะหาย วิธีแก้คือ transactional outbox
3. **ลำดับ event:** ใช้ claim id เป็น key ใน Kafka event ของเคลมเดียวกันจึงถูกประมวลผลตามลำดับ
4. **กันนับ event ซ้ำ (idempotent consumer):** บันทึก `eventId` ลง `processed_events` ใน transaction เดียวกับการอัปเดตยอด ถ้า event เดิมมาซ้ำจะถูกข้าม ทดสอบโดยส่ง event ซ้ำเข้า Kafka แล้ว
5. **อัปเดตยอดแบบ atomic:** ใช้ `INSERT … ON CONFLICT DO UPDATE` ถ้ามี consumer หลายตัวอัปเดตแถวเดียวกัน ยอดจะไม่หาย
6. **แยกกติกาออกจาก SQL:** `ExposureProjector` เก็บกติกา ส่วน `ExposureStore` / `JdbcExposureStore` เก็บ SQL จึงเทสต์กติกาได้โดยไม่ต้องมีฐานข้อมูล
7. **ถ้าประมวลผลไม่สำเร็จ:** ลองใหม่ 3 ครั้งแล้วข้ามไป ของจริงควรส่งไป dead-letter topic

**Liability exposure** คือยอดที่ลูกค้าขอรวมจากเคลมที่ยังไม่ปิด แยกตามประเทศ ประเภท และสกุลเงิน ข้อมูลอัปเดตตามหลังเล็กน้อย (eventually consistent) ปกติไม่กี่มิลลิวินาที ส่วนตัวเลขปริมาณงานใน dashboard อ่านจาก write model โดยตรง จึงตรงทันที

**Performance ของทีม** คำนวณจาก write model แยกตามเจ้าหน้าที่ มีจำนวนงานที่ค้าง, จำนวนที่ settle และ reject, และเวลาเฉลี่ยตั้งแต่**ยื่นเคลม**จนตัดสิน นับจากตอนยื่นเพราะเป็นเวลาที่ลูกค้ารอจริง

### ระบุตัวตน (แบบง่าย)

- `X-User-Role`: `CLAIMANT` หรือ `STAFF`
- `X-User-Name`: ชื่อผู้ใช้

สิทธิ์แบ่งตาม path: `/api/claimant/**` สำหรับลูกค้า และ `/api/staff/**` สำหรับเจ้าหน้าที่ ถ้าลูกค้าขอดูเคลมของคนอื่นจะได้ 404 ไม่ใช่ 403 เพื่อไม่บอกว่าเคลมนั้นมีอยู่จริง ถ้าจะเปลี่ยนเป็น OAuth2/JWT แก้แค่ `HeaderAuthInterceptor`

### API contract

- **ใครเป็นคนกำหนด:** backend เป็นคนกำหนดแบบ code-first โดย springdoc สร้าง OpenAPI จาก controller และ DTO ให้อัตโนมัติ
  - JSON อยู่ที่ `/v3/api-docs`
  - Swagger UI อยู่ที่ `/swagger-ui.html`
- **ไม่คลาดกับของจริง:** spec สร้างจากโค้ดโดยตรง
- **header ตัวตน:** ประกาศเป็น API-key จึงกด Authorize ใส่ครั้งเดียวแล้วใช้ได้ทุก endpoint
- **ฝั่ง Angular:** `models/` ตอนนี้เขียนตาม DTO ด้วยมือ ขั้นต่อไปคือ generate จาก spec

### Stack

- Java 21 กับ Spring Boot 3.5.16 (ตามที่ spec กำหนด ตอนนี้ Spring Initializr เป็น 4.x แล้ว)
- PostgreSQL 16 กับ Flyway
- Kafka 3.9.1 แบบ KRaft
- Lombok `@Getter` บน entity โดยไม่มี setter
- `docker compose up --build` รันได้ทั้งระบบ

## Frontend

Angular 22 ใช้ standalone component, signals, zoneless change detection และ Angular Material แอปเดียวรองรับผู้ใช้ทั้งสองแบบ

- **ระบุตัวตน:** แถบด้านบนเลือก role ผู้ใช้ และประเทศ แล้ว `identityInterceptor` ใส่ `X-User-Role` / `X-User-Name` ให้เฉพาะตอนเรียก API
- **Routes:** แต่ละ feature ประกาศ `const routes: Routes` ในไฟล์ `pages/<feature>/<feature>.routes.ts` แล้วโหลดด้วย `loadChildren` ส่วน `roleGuard` อยู่ที่จุด mount คือ `/claim` สำหรับลูกค้า และ `/staff` สำหรับเจ้าหน้าที่
- **หน้าลูกค้า:**
  - **ยื่นเคลม:** ฟอร์มที่ฟิลด์เปลี่ยนตามประเภทเคลม เลือกสกุลเงินได้ระหว่างท้องถิ่นกับ USD และเลขกรมธรรม์กรอกให้อัตโนมัติ
  - **ติดตามเคลม:** ค้นด้วยเลขเคลม มีป้ายสถานะ timeline และฟอร์มตอบคำถามที่แสดงเฉพาะตอนสถานะเป็น `INFO_REQUESTED`
- **หน้าเจ้าหน้าที่:**
  - **Exposure:** การ์ด **USD** รวมทุกประเทศ (บวกกันได้เพราะสกุลเดียวกัน) และการ์ดของแต่ละประเทศแสดงเฉพาะสกุลเงินท้องถิ่น ทุกการ์ดมี Open, Outstanding และ Settled
  - **ปริมาณงานและผลงาน:** ป้ายนับตามสถานะ และตารางผลงานรายคน
  - **คิวงาน:** กรองตามประเทศ สถานะ และ "Only mine" ได้ ปุ่มดำเนินการแสดงเฉพาะเมื่อทำได้ตามกติกา
  - **409:** แสดงข้อความว่ามีคนเปลี่ยนเคลมนี้ไปก่อน แล้วโหลดคิวใหม่
  - **Drawer รายละเอียด:** คลิกแถวในคิวงานเพื่อเปิด (`pages/staff/staff-workspace/components/claim-drawer/`)
- **ความสดของข้อมูล:** โหลดใหม่ทุก 10 วินาที ถ้าจะอัปเกรดใช้ SSE
- **เปลี่ยนผู้ใช้หรือประเทศ:** หน้าโหลดใหม่เองด้วย `effect()` ที่ตามดูตัวตน และทำงานเฉพาะตอนที่ role ตรงกับหน้า
- **Same origin:** หน้าเว็บเรียก `/api` แล้ว nginx ส่งต่อไป backend จึงไม่ต้องตั้ง CORS

## สิ่งที่ตัดออก

- อัปโหลดเอกสาร
- login จริง
- แปลงค่าเงิน
- reserve ที่เจ้าหน้าที่ประเมิน (ตอนนี้ใช้ยอดที่ลูกค้าขอแทน)
- transactional outbox
- dead-letter topic
