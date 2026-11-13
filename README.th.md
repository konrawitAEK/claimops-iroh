<h1>
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="assets/brand/iroh-bridge-logo-dark.svg">
    <img alt="iroh-bridge" src="assets/brand/iroh-bridge-logo-light.svg" width="440">
  </picture>
</h1>

[![npm](https://img.shields.io/npm/v/iroh-fetch)](https://www.npmjs.com/package/iroh-fetch)
[![ci](https://github.com/irohkit/iroh-bridge/actions/workflows/ci.yml/badge.svg)](https://github.com/irohkit/iroh-bridge/actions/workflows/ci.yml)

[English](README.md) · **ภาษาไทย**

ใช้งาน backend ที่อยู่หลัง NAT ได้เหมือนมี URL สาธารณะ frontend ของคุณ (Next.js บน Vercel, static host ใดก็ได้ หรือ
Node.js) ยังเรียก `fetch("https://api.example.com/...")`, `new EventSource(...)` และ `new WebSocket(...)` ได้ตามเดิม
แต่ทราฟฟิกจะวิ่งผ่าน [iroh](https://www.iroh.computer/) ไปยัง gateway ตัวเล็กๆ ที่อยู่ข้าง backend ไม่ต้องเปิดพอร์ต
ไม่ต้องมี IP สาธารณะ ไม่ต้องตั้ง DNS ไม่ต้องมี TLS certificate และไม่ต้องรัน tunnel service ใดๆ

```
Browser / Node.js                      iroh relay                    backend machine (behind NAT)
┌───────────────────┐   WebSocket   ┌──────────────┐   QUIC/UDP   ┌───────────────┐  HTTP  ┌──────────┐
│ iroh-fetch (WASM) │ ────────────▶ │  n0 or yours │ ◀─────────── │ iroh-gateway  │ ─────▶ │ backends │
└───────────────────┘               └──────────────┘  (outbound)  └───────────────┘        └──────────┘
```

**เดโมออนไลน์:** <https://iroh-bridge.vercel.app> กดปุ่ม **Run self-test** หน้าเว็บจะคุยกับ backend สองตัวที่อยู่หลัง NAT
โดยตัวหนึ่งผ่าน gateway แบบ HA สองตัว และใช้แค่ `fetch` / `EventSource` / `WebSocket` มาตรฐานเท่านั้น

> 🇹🇭 มือใหม่ที่ยังไม่รู้จัก NAT, relay หรือ iroh เลย เริ่มอ่านที่ [คู่มือสำหรับมือใหม่](BEGINNERS.md) ก่อนได้

<a id="contents"></a>
## สารบัญ

- [คู่มือสำหรับมือใหม่ (BEGINNERS.md)](BEGINNERS.md)
- [ความสามารถ](#features)
- [เริ่มต้นใช้งานอย่างรวดเร็ว](#quick-start)
- [Frontend: `iroh-fetch`](#frontend-iroh-fetch)
  - [ใช้ fetch, EventSource และ WebSocket แบบเดิม (`install`)](#use-the-normal-fetch-eventsource-and-websocket-install)
  - [React / Next.js](#react--nextjs)
  - [XMLHttpRequest และ axios](#xmlhttprequest-and-axios)
  - [รูปภาพ วิดีโอ และการดาวน์โหลด (service worker)](#images-video-and-downloads-service-worker)
  - [ตัวตนของ client แบบคงที่](#stable-client-identity)
  - [หลาย backend](#several-backends)
  - [High availability](#high-availability)
  - [พฤติกรรมของ fetch](#fetch-behaviour)
  - [Streaming, การอัปโหลด และ timeout](#streaming-uploads-timeouts)
  - [WebSocket และ EventSource](#websocket-and-eventsource)
  - [การยืนยันตัวตน (JWT, session)](#authentication-jwt-sessions)
  - [Node.js](#nodejs)
  - [ตัวเลือกทั้งหมด](#options-reference)
- [Backend: `iroh-gateway`](#backend-iroh-gateway)
  - [การติดตั้ง](#install)
  - [การตั้งค่า](#configuration)
  - [หลาย upstream และการ route ตาม host และ path](#several-upstreams-routing-by-host-and-path)
  - [การควบคุมการเข้าถึง](#access-control)
  - [เปลี่ยน route และ allow list โดยไม่ต้อง restart](#changing-routes-and-allow-lists-without-a-restart)
  - [สิ่งที่ backend ของคุณเห็น](#what-your-backend-sees)
  - [Monitoring](#monitoring)
- [พอร์ตในเครื่อง: `iroh-proxy`](#local-port-iroh-proxy)
  - [Postman, Insomnia, curl](#postman-insomnia-curl)
- [รัน relay ของคุณเอง](#run-your-own-relay)
- [ผลการทดสอบ](#tested)
- [ข้อควรรู้สำหรับ production](#production-notes)
- [การแก้ปัญหา](#troubleshooting)
- [การพัฒนา](#development)

<a id="features"></a>
## ความสามารถ

| | |
|---|---|
| **ใช้ได้เหมือน URL สาธารณะ** | `install()` จะ route `fetch`, `XMLHttpRequest`, `EventSource` และ `WebSocket` ระดับ global ของ origin ที่เลือกไว้ โค้ดเดิม รวมถึง axios, SWR, React Query, jQuery และไลบรารีอื่นๆ จึงใช้ได้โดยไม่ต้องแก้ |
| **รูปภาพ วิดีโอ การดาวน์โหลด** | service worker (ใช้หรือไม่ก็ได้) ทำให้ `<img>`, `<video>`, CSS `url()`, ฟอนต์ และการดาวน์โหลดจาก URL ของ backend ใช้ได้ด้วย |
| **รองรับ fetch ครบถ้วน** | URL แบบเต็มหรือแบบ relative, ทุก method และ header, `res.url` / `res.redirected`, redirect (`follow` / `manual` / `error`), cookie (`credentials`), `AbortSignal` (รวมถึง `AbortSignal.timeout`) |
| **Cache ฝั่ง client** | HTTP cache แบบเปิดเอง (`cache: true`): `Cache-Control` / `Expires`, revalidate ด้วย ETag และ Last-Modified, `Vary`, โหมด `Request.cache` เก็บในหน่วยความจำหรือใน Cache API |
| **Streaming ทั้งสองทาง** | response เป็น stream ได้ (SSE, NDJSON, การดาวน์โหลด) ส่วนการอัปโหลด `File`, `Blob`, `FormData` หรือ `ReadableStream` ก็ stream โดยไม่ต้อง buffer |
| **WebSocket** | API มาตรฐาน รองรับ text และ binary, subprotocol, close code ที่ถูกต้อง และกำหนด header ตอน handshake เองได้ (เช่น `Authorization`) |
| **EventSource** | API มาตรฐาน รองรับ named event, reconnect อัตโนมัติ และ `Last-Event-ID` |
| **ยืนยันตัวตนแบบเดิม** | JWT / bearer token บน fetch, WebSocket และ EventSource รวมถึง cookie session โดยมี cookie jar แยกต่อ backend |
| **หลาย backend** | frontend เดียวใช้ได้กับกี่ backend ก็ได้ route ตาม origin ผ่าน connection เดียวกัน |
| **High availability** | มีหลาย gateway ต่อ backend แบบ `failover` หรือ `round-robin` และมีหลาย upstream ต่อ gateway พร้อม health check |
| **การ route ที่ gateway** | gateway ตัวเดียวให้บริการ service ภายในได้หลายตัว ตาม host และ path prefix |
| **ควบคุมการเข้าถึง** | allow-list ตาม endpoint id ของ client และตาม path และ rate limit แต่ละ client ที่ gateway |
| **แข็งแรงปลอดภัย** | ตรวจ path และ header อย่างเข้มงวด, มี limit และ timeout กัน client ที่ช้าหรือยิงถล่ม, ไม่รั่วรายละเอียด error, รันเป็น service แบบ sandbox และทดสอบด้วยการโจมตี gateway ทดสอบจริง |
| **ตัวตนผู้เรียกที่เชื่อถือได้** | backend ได้รับ `X-Iroh-Remote-Id` ซึ่งเป็น endpoint id ของผู้เรียกที่ยืนยันด้วยการเข้ารหัสแล้ว |
| **ตัวตนของ client ที่คงที่** | กำหนด endpoint id คงที่ต่ออุปกรณ์หรือต่อเซิร์ฟเวอร์ได้ (`persistIdentity`, `secretKey`) สำหรับใช้กับ allow-list และ rate limit |
| **Monitoring** | gateway มี `/healthz`, `/readyz`, `/status` และ Prometheus `/metrics` บน admin port ภายในเครื่อง |
| **React bindings** | `iroh-fetch/react`: `<IrohProvider>`, `useIroh()`, `useIrohFetch()`, `<IrohImage>`, `useIrohObjectUrl()` |
| **รันได้ทุกที่** | เบราว์เซอร์และ Node.js ≥ 22, มี gateway binary สำหรับ Linux x86_64/arm64 และ macOS, Docker image และ systemd unit |
| **พอร์ตบนเครื่อง** | `iroh-proxy` เปิด gateway ให้เป็น `http://127.0.0.1:PORT` สำหรับ curl, Postman หรือ HTTP client ใดก็ได้ |

<a id="quick-start"></a>
## เริ่มต้นใช้งานอย่างรวดเร็ว

**1. ที่เครื่อง backend** ติดตั้งและเปิด gateway ตัว gateway จะพิมพ์ endpoint id ออกมา ค่านี้คือที่อยู่ของ backend คุณ:

```bash
curl -fsSL https://raw.githubusercontent.com/irohkit/iroh-bridge/main/scripts/install-gateway.sh | sh   # Linux, macOS
BACKEND_URL=http://127.0.0.1:8080 iroh-gateway
# IROH_ENDPOINT_ID=acc0c8d6bc20700875b5fe1da1a3379f77f28dd16f8a66e3368856473ecc7a53
```

Windows: `irm https://raw.githubusercontent.com/irohkit/iroh-bridge/main/scripts/install-gateway.ps1 | iex` ส่วน Docker:

```bash
docker run -d --name iroh-gateway -e BACKEND_URL=http://host.docker.internal:8080 \
  --add-host=host.docker.internal:host-gateway -v iroh-gateway:/data ghcr.io/irohkit/iroh-gateway
docker logs iroh-gateway   # แสดง IROH_ENDPOINT_ID=...
```

ถ้าต้องการให้รันเป็น service ค้างไว้ ดูที่ [การติดตั้ง](#install)

**2. ที่ frontend** จับคู่ origin ที่คุณตั้งเองเข้ากับ id นั้น แล้วเขียนโค้ดตามปกติต่อไปได้เลย:

```bash
npm install iroh-fetch
```

```ts
import { createIrohFetch } from "iroh-fetch";

const iroh = await createIrohFetch({
  backends: { "https://api.example.com": "acc0c8d6bc20700875b5fe1da1a3379f77f28dd16f8a66e3368856473ecc7a53" },
});
iroh.install(); // ตั้งแต่ตรงนี้ คำขอเหล่านี้จะวิ่งผ่าน iroh:

const users = await (await fetch("https://api.example.com/users")).json();
const events = new EventSource("https://api.example.com/events");
const socket = new WebSocket("wss://api.example.com/ws");
```

origin (`https://api.example.com`) เป็นแค่ชื่อที่โค้ดของคุณใช้เรียก ไม่จำเป็นต้องมีอยู่จริงใน DNS และจะมีแค่ path กับ query
เท่านั้นที่ส่งไปถึง backend

<a id="frontend-iroh-fetch"></a>
## Frontend: `iroh-fetch`

<a id="use-the-normal-fetch-eventsource-and-websocket-install"></a>
### ใช้ fetch, EventSource และ WebSocket แบบเดิม (`install`)

```ts
const uninstall = iroh.install();                                   // origin ที่กำหนดใน `backends`
const uninstall = iroh.install({ origins: ["https://api.example.com"] }); // หรือระบุ origin เอง
const uninstall = iroh.install({ match: (url) => url.pathname.startsWith("/api/") }); // หรือใช้กฎใดก็ได้
```

Origin ต้องเป็น URL เต็ม (`https://...`) request ไปยัง origin อื่นจะใช้ `fetch` / `WebSocket` /
`EventSource` ตัวจริงตามเดิม ถ้าไม่ต้องการให้แทนที่ global พวกนั้น ให้ส่ง `websocket: false` หรือ
`eventSource: false` และเรียก `uninstall()` เพื่อคืนค่าเดิม

ถ้าไม่ใช้ `install` ก็เรียก client ตรงๆ ได้ ซึ่งมี API เหมือนกัน:

```ts
await iroh.fetch("/users");                      // URL ที่มีแค่ path จะไปที่ backend หลัก (default)
await iroh.fetch("https://api.example.com/users");
new iroh.EventSource("https://api.example.com/events");
new iroh.WebSocket("wss://api.example.com/ws", ["chat.v1"]);
```

<a id="react--nextjs"></a>
### React / Next.js

```tsx
"use client";
import { IrohProvider, useIroh, useIrohFetch } from "iroh-fetch/react";

export function Providers({ children }) {
  return (
    <IrohProvider backends={{ "https://api.example.com": "<endpoint id>" }} install>
      {children}
    </IrohProvider>
  );
}

function Users() {
  const iroh = useIroh(); // { status: "connecting" | "ready" | "error", client, error }
  const fetchIroh = useIrohFetch(); // รอให้เชื่อมต่อเสร็จเอง เรียกใช้ได้ทันที
  // เมื่อใช้ `install` แล้ว fetch("https://api.example.com/...") ธรรมดาก็ใช้ได้เมื่อ status เป็น "ready"
}
```

WASM โหลดเฉพาะในเบราว์เซอร์ (ภายใน effect) จึงไม่กระทบ server rendering ตัวอย่างเต็มอยู่ที่
[`examples/nextjs`](examples/nextjs): [`iroh.config.ts`](examples/nextjs/iroh.config.ts) จับคู่ origin กับ gateway และ
หน้าเว็บใช้แค่ API มาตรฐาน endpoint id เป็นที่อยู่สาธารณะ จึงเก็บไว้ใน repo ได้ และยัง
override ได้ด้วย `NEXT_PUBLIC_IROH_BACKENDS` (JSON รูปแบบเดียวกัน)

<a id="xmlhttprequest-and-axios"></a>
### XMLHttpRequest และ axios

`install()` แทนที่ `XMLHttpRequest` แบบ global ด้วย (ปิดได้ด้วย `xhr: false`) โค้ดที่ใช้ XHR รวมถึง
**axios ที่ใช้ adapter เริ่มต้นของเบราว์เซอร์**, jQuery และ widget อัปโหลดต่างๆ จึงไม่ต้องแก้อะไร event แสดงความคืบหน้า
ของการอัปโหลดและดาวน์โหลดก็ใช้ได้:

```ts
iroh.install();
const res = await axios.post("https://api.example.com/files", file, {
  onUploadProgress: (e) => setProgress(e.loaded / e.total),
});
```

URL จะรู้ได้ตอน `open()` เท่านั้น class ที่ติดตั้งไว้จึงตัดสินใจตอนนั้น: URL ที่ตรงเงื่อนไขจะวิ่งผ่าน iroh ส่วนที่เหลือใช้
XHR ของเบราว์เซอร์เอง ถ้าไม่ใช้ `install` ให้ใช้ `new iroh.XMLHttpRequest()` ไม่รองรับ XHR แบบ synchronous

<a id="images-video-and-downloads-service-worker"></a>
### รูปภาพ วิดีโอ และการดาวน์โหลด (service worker)

เบราว์เซอร์โหลด `<img src>`, `<video>`, `<audio>`, CSS `url()` และฟอนต์เอง โดยไม่ผ่าน `fetch` ถ้าต้องการ
ให้สิ่งเหล่านี้ใช้กับ URL ของ backend ได้ ให้ลงทะเบียน service worker ที่มากับแพ็กเกจ:

1. เสิร์ฟ `node_modules/iroh-fetch/src/sw.js` จาก origin ของเว็บคุณ เช่น copy ไปไว้ที่ `public/iroh-sw.js` ในสคริปต์
   `prebuild` (ตัวอย่าง Next.js ทำแบบนี้)
2. ลงทะเบียนหลังเชื่อมต่อแล้ว: `await iroh.serviceWorker()` (หรือ `<IrohProvider serviceWorker>`) ซึ่งจะ resolve เมื่อ
   worker ควบคุมหน้าเว็บแล้ว

```tsx
<img src="https://api.example.com/avatars/42.png" />          {/* ใช้ได้เลย */}
<video src="https://files.example.com/intro.mp4" controls />   {/* Range request ผ่านไปได้ */}
<a href={iroh.proxyUrl("https://api.example.com/report.pdf")} download>Download</a>
```

Worker ส่งต่อแต่ละ request ให้หน้าเว็บของแอปที่เปิดอยู่ ซึ่งจะส่งผ่าน iroh-fetch แล้ว stream response
กลับมา เบราว์เซอร์ไม่ยอมให้ service worker ดัก navigation ไปยัง origin อื่น ดังนั้นสำหรับลิงก์ดาวน์โหลดและ "เปิดในแท็บใหม่"
ให้ใช้ `iroh.proxyUrl(url)` ซึ่งจะให้ URL แบบ same-origin `/__iroh__/<host>/...` ที่ worker route ต่อให้ service worker
ต้องใช้ HTTPS (หรือ localhost)

บางเบราว์เซอร์ใช้ service worker ไม่ได้: in-app browser บน iOS (LINE, Facebook, Instagram) และ private
mode บางแบบ ในกรณีนี้ `serviceWorker()` จะเลิกรอการควบคุมหลัง 5 วินาทีแล้วคืน `controlled: false` ส่วน
`<IrohProvider serviceWorker>` จะ log คำเตือนแล้วทำงานต่อ `fetch`, XHR, `WebSocket` และ `EventSource` ยัง
ใช้ได้ปกติ มีแค่การโหลดที่เบราว์เซอร์เริ่มเอง (`<img>`, `<video>`, CSS, ดาวน์โหลด) จาก URL ของ backend ที่ใช้ไม่ได้ด้วยตัวเอง สำหรับ
รูปภาพและสื่อ ให้ใช้ทางเลือกสำรองด้านล่าง ซึ่งโหลดข้อมูลผ่าน iroh มาเป็น URL แบบ `blob:`

ใน React `<IrohImage>` รับ props แบบเดียวกับ `<img>` ถ้ามี service worker ควบคุมหน้าอยู่ ก็ให้ worker โหลดรูป
ถ้าไม่มี จะ fetch ผ่าน iroh มาเป็น URL แบบ `blob:` แล้ว revoke ให้เองเมื่อ unmount หรือเมื่อ `src` เปลี่ยน
`useIrohObjectUrl(url)` ทำเฉพาะส่วนการ fetch สำหรับอย่างอื่น เช่น `<video>` หรือภาพพื้นหลัง:

```tsx
import { IrohImage, useIrohObjectUrl } from "iroh-fetch/react";

<IrohImage src="https://api.example.com/avatars/42.png" alt="" />  {/* worker หรือ URL แบบ blob: แล้วแต่ว่าอันไหนใช้ได้ */}

function Clip({ src }: { src: string }) {
  const { url, loading, error } = useIrohObjectUrl(src); // revoke เมื่อ unmount หรือ src เปลี่ยน
  if (error) return <p>Couldn't load the video: {error.message}</p>;
  return loading ? <p>Loading…</p> : <video src={url} controls />;
}
```

ถ้าไม่ใช้ React ให้ใช้ `iroh.objectUrl(url)` ซึ่งคืน URL แบบ `blob:` (และ reject ถ้า response ไม่ใช่ 2xx) ใช้เสร็จแล้วให้ revoke:

```ts
const url = await iroh.objectUrl("https://api.example.com/avatars/42.png");
img.src = url;
img.onload = () => URL.revokeObjectURL(url);
```

ทางเลือกสำรองนี้ดาวน์โหลดไฟล์ทั้งไฟล์ก่อนแสดงผล `<video>` จึงเลื่อน (seek) ไม่ได้จนกว่าจะโหลดเสร็จ และไฟล์ใหญ่
จะค้างอยู่ในหน่วยความจำ ส่วนเมื่อมี service worker จะใช้ Range request แบบ stream แทน source แบบ relative, `data:` และ `blob:` รวมถึง URL
ของ origin ที่ไม่ใช่ backend จะผ่าน `<IrohImage>` ไปโดยไม่เปลี่ยนแปลง

<a id="stable-client-identity"></a>
### ตัวตนของ client แบบคงที่

Client แต่ละตัวจะได้ endpoint id แบบสุ่มต่อ session ซึ่ง backend เห็นเป็น `X-Iroh-Remote-Id` ถ้าต้องการจำอุปกรณ์
หรือเซิร์ฟเวอร์ได้แม้รีสตาร์ท (สำหรับ `IROH_ALLOW_CLIENTS`, rate limit หรือ audit log) ให้กำหนด id แบบคงที่:

```ts
createIrohFetch({ persistIdentity: true, ... });           // เบราว์เซอร์: เก็บไว้ใน localStorage
createIrohFetch({ secretKey: process.env.IROH_CLIENT_KEY }); // เซิร์ฟเวอร์: จาก secret (iroh.exportSecretKey())
```

Secret key เหมือนรหัสผ่าน: ใครมีก็สวมรอยเป็น client นั้นได้ ในเบราว์เซอร์ สคริปต์บนหน้าเว็บอ่าน
localStorage ได้ บั๊ก XSS จึงขโมยมันได้ ควรใช้กับเซิร์ฟเวอร์และแอปที่เชื่อถือได้มากกว่า

<a id="several-backends"></a>
### หลาย backend

```ts
const iroh = await createIrohFetch({
  backends: {
    "https://api.example.com": "<gateway A>",
    "https://files.example.com": { endpointId: "<gateway B>" },
    "https://admin.example.com": { endpointId: "<gateway C>", default: true }, // รับ URL ที่มีแค่ path
  },
});

await iroh.fetch("https://files.example.com/report.pdf");
await iroh.backend("https://files.example.com").fetch("/report.pdf"); // มุมมองเฉพาะ backend นั้น ได้ผลเหมือนกัน
iroh.backends; // [{ origin, replicas: [{ endpointId, relayUrl, healthy, connected }], connected }]
```

ทุก backend ใช้ iroh endpoint ฝั่ง local ตัวเดียวกัน (การเชื่อมต่อ relay เดียว) และได้การเชื่อมต่อ QUIC ของตัวเองคนละหนึ่งเส้น แต่ละ backend มี
cookie jar แยกกัน backend ที่ล่มไม่กระทบตัวอื่น: `createIrohFetch` จะล้มเหลวก็ต่อเมื่อติดต่อไม่ได้เลยสักตัว
และ request ไปยังตัวที่ติดต่อไม่ได้จะล้มเหลว (แล้วลองใหม่ภายหลัง) แยกกันไป

<a id="high-availability"></a>
### High availability

รันหลาย gateway ให้ backend เดียวกัน (บนเครื่องต่างกัน แต่ละตัวมี key ของตัวเอง) แล้วระบุทั้งหมด:

```ts
backends: {
  "https://api.example.com": {
    endpointIds: ["<gateway 1>", "<gateway 2>", "<gateway 3>"],
    strategy: "round-robin", // หรือ "failover" (ค่าเริ่มต้น): ใช้ตามลำดับ
  },
}
// แบบย่อสำหรับ backend เดียว: createIrohFetch({ endpointIds: [...], strategy })
```

- Replica ที่ติดต่อไม่ได้จะถูกข้ามเป็นเวลา `failoverCooldown` ms (ค่าเริ่มต้น 10 วินาที) ระหว่างนั้นจะเชื่อมต่อใหม่อยู่เบื้องหลัง
  และถ้าใช้ `"failover"` ก็จะกลับมาเป็น replica ที่ใช้ก่อนเมื่อมันกลับมา
- Client รู้ว่าการเชื่อมต่อตายภายใน ~5 วินาที (ส่ง keep-alive ทุกวินาที) request ที่ไม่มี body ซึ่งค้างอยู่จึงย้ายไป
  replica ถัดไปแทนที่จะค้าง
- วาง replica ไว้บน **relay ต่างกัน** (เช่น ตัวหนึ่งบน relay ของคุณ อีกตัวบน public relay) เพื่อให้รอดเมื่อ relay ล่ม ดู
  [Redundancy](deploy/relay/README.md#7-redundancy-surviving-a-relay-outage)
- Replica ระบุ relay ที่มันอยู่ได้: `{ endpointId: "<gateway 2>", relayUrl: "https://aps1-1.relay.n0.iroh.link" }`
  แล้ว client จะ dial ผ่าน relay นั้นแทน relay ใน `relayUrl` ของคุณ standby ที่อยู่บน relay อื่นจึงเชื่อมต่อได้
  ในเสี้ยววินาที แทนที่จะต้องรอ address lookup (มัธยฐาน 0.2 วินาที เทียบกับ 1.1 วินาที และบางครั้ง lookup นาน
  15 วินาทีขึ้นไป) นี่เป็นแค่ hint: ถ้า gateway ย้ายไป relay อื่น การ dial ก็ยังหาเจอผ่าน lookup
- Request ที่ไม่มี body จะถูกลองใหม่กับ replica ถัดไปถ้าล้มเหลวกลางทาง ส่วน request ที่มี body (ซึ่ง stream
  และเล่นซ้ำไม่ได้) จะ failover ได้เฉพาะตอนกำลังเชื่อมต่อ ก่อนส่งอะไรออกไป
- WebSocket และ EventSource เชื่อมต่อกับ replica ที่ healthy ส่วน EventSource จะเชื่อมต่อใหม่ไปยังตัวถัดไปถ้า stream หลุด
- ทุก response มี `X-Iroh-Gateway-Id` ให้ดูได้ว่า replica ไหนตอบ

ฝั่ง backend นั้น gateway ตัวเดียวก็กระจายโหลดไปหลาย upstream process ได้ ดู
[หลาย upstream](#several-upstreams-routing-by-host-and-path)

<a id="fetch-behaviour"></a>
### พฤติกรรมของ fetch

`iroh.fetch` (และ `fetch` แบบ global ที่ติดตั้งไว้) ทำตามมาตรฐาน Fetch:

- **URL**: path (`/x?y=1`), URL `http(s)` แบบเต็ม, object `URL` หรือ `Request` โดย `res.url` คือ URL ที่ขอ
  (หลัง redirect) และ `res.redirected` จะถูกตั้งค่าเมื่อมีการตาม redirect
- **Redirect**: `redirect: "follow"` (ค่าเริ่มต้น สูงสุด 20 ครั้ง 301/302 เปลี่ยน POST เป็น GET และ 303 เปลี่ยนเสมอ),
  `"manual"` (คืน 3xx กลับมา) หรือ `"error"` redirect ไปยัง origin อื่นจะคืนกลับมาตามที่ได้ ไม่ตามไป
- **Cookie**: `Set-Cookie` จาก backend จะถูกเก็บไว้ (รองรับ Path, Max-Age และ Expires) แล้วส่งกลับไป ใช้
  `credentials: "omit"` เพื่อข้าม, `cookies: false` เพื่อปิด หรือ `persistCookies: true` เพื่อเก็บไว้ข้ามการ reload
  (ใน localStorage ซึ่งสคริปต์บนหน้าเว็บอ่านได้ ต่างจาก cookie แบบ HttpOnly จริง) ดู cookie ได้ด้วย
  `iroh.cookies.get(name)` และใช้ `iroh.cookies.clear()` เพื่อ log out ฝั่ง local
- **Header**: ส่งผ่านทั้งหมด ยกเว้น hop-by-hop header เบราว์เซอร์อ่าน `Set-Cookie` จาก
  `Response` ไม่ได้ แต่ cookie jar อ่านได้
- **Caching**: request ที่ผ่าน iroh ไม่ผ่าน HTTP cache ของเบราว์เซอร์ โดยค่าเริ่มต้น GET ทุกครั้งจึงวิ่งผ่าน relay ใหม่
  ส่ง `cache: true` เพื่อใช้ HTTP cache ฝั่ง client (ดูด้านล่าง)

<a id="http-cache-cache-option"></a>
#### HTTP cache (ตัวเลือก `cache`)

```ts
const iroh = await createIrohFetch({ backends: { ... }, cache: true });
// หรือ: cache: { maxEntries: 500, maxBytes: 50_000_000, storage: "memory" | "cache-api" }
```

เมื่อเปิดตัวเลือกนี้ response ของ `GET` และ `HEAD` จะถูก cache เหมือน private HTTP cache ของเบราว์เซอร์ (สำหรับ `iroh.fetch`,
`fetch` แบบ global ที่ติดตั้งไว้ และ `XMLHttpRequest` แต่ไม่ใช้กับ EventSource) ถ้าเจอ entry ที่ยังสดอยู่จะไม่มี network round trip เลย
ส่วน entry ที่หมดอายุแต่มี validator จะเสียแค่ request เล็กๆ หนึ่งครั้งที่ได้ 304 body ว่างกลับมา ถ้าปิดตัวเลือกนี้ก็ไม่มีอะไรเปลี่ยน

- **ความสด (freshness)**: `Cache-Control: max-age` (ถ้าไม่มีใช้ `Expires` ถ้าไม่มีอีกถือว่าไม่สด) ลบด้วยอายุของ response `s-maxage` ใช้กับ
  shared cache จึงถูกเพิกเฉย ไม่มีการเดาความสดแบบ heuristic: response ที่ไม่มี `max-age` / `Expires` จะเก็บไว้ก็ต่อเมื่อ
  มี `ETag` หรือ `Last-Modified` และจะ revalidate ทุกครั้งที่ใช้
- **Directive**: `no-store` ไม่ถูกเก็บเลย `no-cache` ถูกเก็บแต่ revalidate ทุกครั้ง `private` ใช้ได้ (เพราะนี่คือ
  private cache) `public` อนุญาตให้ cache request ที่มี credential (ดูข้อถัดไป)
- **Credential**: request ที่มี `Authorization` หรือ cookie (header `Cookie` ของตัวเอง หรือ cookie จาก jar)
  จะถูกเก็บและตอบจาก cache ก็ต่อเมื่อ response ระบุ `public` เมื่อ backend ตั้ง session
  cookie แล้ว ให้ใช้ `credentials: "omit"` กับ resource สาธารณะที่ต้องการให้ cache
- **Revalidation**: entry ที่หมดอายุ (หรือ `no-cache`) จะ revalidate ด้วย `If-None-Match` / `If-Modified-Since` เมื่อได้ `304`
  จะอัปเดต header ที่เก็บไว้ และคุณได้ body จาก cache เป็น `200` ตามปกติ request ที่แอปตั้ง header แบบ
  conditional หรือ `Range` เองจะถูกส่งไปตามนั้น
- **`Request.cache`**: `default`, `no-store` (ข้าม cache), `reload` (fetch แล้วเก็บ), `no-cache` (revalidate เสมอ),
  `force-cache` (ใช้ entry ใดก็ได้ แม้หมดอายุ) และ `only-if-cached` (entry ใดก็ได้ ถ้าไม่มีจะได้ `TypeError` โดยมาตรฐาน Fetch
  กำหนดให้ใช้คู่กับ `mode: "same-origin"`) header `Cache-Control: no-cache` / `no-store` และ `Pragma: no-cache` ใน request ก็ใช้ได้เช่นกัน
- **Key**: แยกตาม backend และ URL (รวม query) หนึ่ง entry ต่อหนึ่ง variant ของ `Vary` โดย `Vary: *` จะไม่ถูกเก็บ เก็บเฉพาะ
  response `200`, `203`, `204`, `404`, `405`, `410`, `414` และ `501` และเก็บเมื่ออ่าน body
  จนจบแล้วเท่านั้น `POST` / `PUT` / `PATCH` / `DELETE` ที่สำเร็จจะลบ entry ของ URL นั้น (และของ
  `Location` / `Content-Location`)
- **ที่เก็บ**: `"memory"` (ค่าเริ่มต้น) อยู่แค่ต่อการโหลดหน้าหนึ่งครั้ง `"cache-api"` เก็บ entry ใน Cache API ของเบราว์เซอร์ข้ามการ
  reload (ถ้าไม่มีจะใช้ memory แทน เช่นใน Node หรือหน้าเว็บที่เป็น http ธรรมดา) entry ที่ใช้ล่าสุดนานที่สุด
  จะถูกลบเมื่อเกิน `maxEntries` (500) หรือ `maxBytes` (50 MB) `iroh.clearCache()` ล้าง cache ทั้งหมด เช่น ตอน logout

Route `/cache/resource?id=…&max-age=60&etag=1` ของ backend ตัวอย่างส่ง header เหล่านี้ และ `/cache/hits?id=…` นับ
request ที่ไปถึงจริง [`test/cache-e2e.test.js`](packages/iroh-fetch/test/cache-e2e.test.js) ใช้สอง route นี้

<a id="streaming-uploads-timeouts"></a>
### Streaming, การอัปโหลด และ timeout

```ts
// Response แบบ stream (SSE, NDJSON, ดาวน์โหลดไฟล์ใหญ่) การ abort จะหยุดการส่งข้อมูลฝั่ง backend ด้วย
const res = await fetch("https://api.example.com/export", { signal: AbortSignal.timeout(60_000) });
for await (const chunk of res.body) process(chunk);

// อัปโหลดแบบ stream: ไฟล์ไม่ถูกอ่านเข้าหน่วยความจำทั้งก้อน
await fetch("https://api.example.com/files", { method: "POST", body: input.files[0] });

// ReadableStream ใดๆ ก็ใช้ได้
await fetch("https://api.example.com/ingest", { method: "POST", body: someReadableStream });
```

`createIrohFetch` จะ reject ด้วย `TimeoutError` หลัง `connectTimeout` (ค่าเริ่มต้น 15 วินาที) เมื่อติดต่อ gateway ไม่ได้
แอปจึงไม่ค้างอยู่ที่ "connecting"

<a id="websocket-and-eventsource"></a>
### WebSocket และ EventSource

ทั้งสองทำตาม API ของเบราว์เซอร์ (`readyState`, `onopen` / `onmessage` / `onerror` / `onclose`, `addEventListener`,
`binaryType`, `protocol`, close code และ reason) และมีส่วนขยายสองอย่างที่เวอร์ชันของเบราว์เซอร์ไม่มี:

```ts
// กำหนด header ของ handshake เอง
iroh.websocket("wss://api.example.com/ws", { protocols: ["chat.v1"], headers: { authorization: `Bearer ${token}` } });
// EventSource แบบมี header
new iroh.EventSource("https://api.example.com/events", { headers: { authorization: `Bearer ${token}` } });
```

Cookie ที่เก็บไว้จะถูกส่งไปกับ WebSocket handshake เหมือนที่เบราว์เซอร์ทำ

<a id="authentication-jwt-sessions"></a>
### การยืนยันตัวตน (JWT, session)

Auth ทำงานเหมือนกับการเรียก URL สาธารณะทุกประการ เพราะ backend ได้รับ header ชุดเดียวกัน:

```ts
// JWT / OAuth bearer token
const { token } = await (await fetch("https://api.example.com/auth/login", {
  method: "POST",
  headers: { "content-type": "application/json" },
  body: JSON.stringify({ username, password }),
})).json();
const me = await fetch("https://api.example.com/auth/me", { headers: { Authorization: `Bearer ${token}` } });

// WebSocket: ใช้ header (ส่วนขยายของ iroh) หรือ ?access_token= แบบที่ใช้กันทั่วไป
iroh.websocket("wss://api.example.com/ws", { headers: { Authorization: `Bearer ${token}` } });
new WebSocket(`wss://api.example.com/ws?access_token=${token}`);

// EventSource แบบมี header (ส่วนขยายของ iroh)
new iroh.EventSource("https://api.example.com/events", { headers: { Authorization: `Bearer ${token}` } });
```

Session แบบ cookie ก็ใช้ได้ (ดู [พฤติกรรมของ fetch](#fetch-behaviour)) backend ตัวอย่าง
([`examples/backend`](examples/backend/server.mjs)) มี login ด้วย HS256 JWT, route ที่ป้องกันไว้, SSE และ WebSocket ส่วน
[`test/jwt.test.js`](packages/iroh-fetch/test/jwt.test.js) ตรวจว่า token ที่ถูกดัดแปลง, ใช้ `alg: none` หรือหมดอายุแล้วจะถูก
ปฏิเสธ

<a id="nodejs"></a>
### Node.js

แพ็กเกจเดียวกันใช้ได้ใน Node.js ≥ 22 (`import { createIrohFetch } from "iroh-fetch"` จะเลือก build สำหรับ Node ให้เอง) ใน
serverless function ให้เก็บ client ไว้ในตัวแปรระดับ module เพื่อให้ invocation ที่ยัง warm อยู่ใช้การเชื่อมต่อเดิม (cold start
เชื่อมต่อได้ในราวหนึ่งวินาที)

**Native engine: การเชื่อมต่อตรง** เบราว์เซอร์ส่ง UDP ไม่ได้ client แบบ WASM จึงต้องผ่าน relay เสมอ ใน
Node.js นั้น iroh-fetch จะโหลด native addon (client Rust ตัวเดียวกัน build ด้วย napi-rs) แทน เมื่อมี prebuild สำหรับ
แพลตฟอร์มนั้นติดตั้งไว้: linux x64/arm64 (glibc), linux x64 (musl เช่น Alpine), macOS x64/arm64 และ Windows x64 npm
จะติดตั้งแพ็กเกจ `iroh-fetch-<platform>` ที่ตรงกันให้อัตโนมัติในฐานะ optional dependency native client มี
UDP socket จริง จึงเริ่มต้นผ่าน relay เหมือนเบราว์เซอร์ จากนั้นทำ hole punching เพื่อเปิดเส้นทางตรงไปยัง gateway แล้ว
ย้ายการเชื่อมต่อไปทางนั้นภายในราวหนึ่งวินาที ถ้า hole punching ไม่สำเร็จ (เช่น ทั้งสองฝั่งอยู่หลัง symmetric NAT) ก็
อยู่บน relay ต่อไป บนแพลตฟอร์มอื่น หรือถ้าโหลด addon ไม่ได้ จะใช้ WASM engine โดยไม่แจ้งอะไร

```ts
const iroh = await createIrohFetch({ endpointId, engine: "native" }); // "auto" (default) | "native" | "wasm"
iroh.engine;           // "native" หรือ "wasm": ตัวที่ใช้อยู่
iroh.connectionInfo(); // { kind: "direct", paths: [{ kind: "direct", addr: "203.0.113.7:51820", rttMs: 3.1, ... }, ...] }
```

`engine: "native"` จะล้มเหลวถ้าโหลด addon ไม่ได้ แทนที่จะ fallback ตัวแปร environment `IROH_FETCH_BACKEND=native|wasm`
ให้ผลเหมือนกันโดยไม่ต้องแก้โค้ด (ถ้ากำหนดทั้งคู่ option ชนะ)

วัดกับ gateway ตัวอย่างหนึ่งตัวด้วย [`scripts/bench-engines.mjs`](scripts/bench-engines.mjs) (Node 24, `GET /hello`
ต่อเนื่อง 500 ครั้ง, `POST /upload` แบบ stream 30 MB, relay ของตัวเองบน VPS ที่ห่างไป ~20 ms) **หมายเหตุ:** gateway รันบน
เครื่องเดียวกับ client เส้นทางตรงของ native engine ในที่นี้จึงเป็นการวิ่งภายในเครื่อง (RTT ~0.2 ms) ถ้าข้ามอินเทอร์เน็ต
ให้คาดว่าจะได้ RTT ของเครือข่ายแทนการอ้อมผ่าน relay ไม่ใช่ตัวเลขเหล่านี้ตรงๆ

| Engine | เส้นทาง | Response แรก | Latency p50 | p99 | อัปโหลด |
|---|---|---|---|---|---|
| native | ตรง (ภายในเครื่อง) | 75 ms | 0.56 ms | 1.5–2.1 ms | 210–230 MB/s |
| WASM | relay | 90–165 ms | 14 ms | 16 ms | 9.2 MB/s |

<a id="options-reference"></a>
### ตัวเลือกทั้งหมด

| ตัวเลือก | ค่าเริ่มต้น | |
|---|---|---|
| `endpointId` / `endpointIds` | | backend เดียว: id ของ gateway หรือหลาย replica |
| `backends` | | `{ [origin]: id \| replica[] \| { endpointId?, relayUrl?, endpointIds?, strategy?, default? } }` โดย replica คือ id หรือ `{ endpointId, relayUrl }` |
| `strategy` | `"failover"` | `"failover"` หรือ `"round-robin"` ระหว่าง replica |
| `failoverCooldown` | `10000` | จำนวน ms ที่ข้าม replica ที่ติดต่อไม่ได้ |
| `connect` | `true` | dial ทุก gateway ตั้งแต่แรก (ไม่เช่นนั้นจะ dial ตอนใช้ครั้งแรก) |
| `connectTimeout` | `15000` | จำนวน ms ที่รอ gateway ก่อนเกิด `TimeoutError` (`0` = รอตลอดไป) |
| `cookies` | `true` | เก็บและส่ง cookie |
| `persistCookies` | `false` | `true` (localStorage) หรือ `Storage` |
| `cache` | `false` | HTTP cache ฝั่ง client: `true` หรือ `{ maxEntries?: 500, maxBytes?: 50e6, storage?: "memory" \| "cache-api" }` ([รายละเอียด](#http-cache-cache-option)) |
| `relayUrl` | public relay ของ n0 | relay ของคุณเอง ([คู่มือ](deploy/relay/README.md)) หรือรายการ เช่น `["https://relay-a…", "https://relay-b…", "n0"]` (relay ของคุณทุกตัวได้ `relayToken` เดียวกัน ส่วนของ n0 ไม่ได้เลย) |
| `relayToken` | | token สำหรับ relay ที่ต้องใช้: string หรือ function ที่คืนค่า (sync หรือ async) ควรให้ backend ของคุณเป็นผู้ออกให้ ([รายละเอียด](#relay-token-from-your-backend)) |
| `relayTokenRefreshMs` | `0` | เมื่อ `relayToken` เป็น function ให้เรียกทุกๆ จำนวน ms นี้ด้วย (ค่าเริ่มต้น: เรียกเฉพาะเมื่อ relay ปฏิเสธ client) |
| `secretKey` | สุ่ม | ตัวตนของ client แบบคงที่ (hex 64 ตัว) |
| `persistIdentity` | `false` | เก็บตัวตนไว้ใน localStorage (`true`) หรือ `Storage` |
| `logLevel` | ปิด | `"info"`, `"debug"`, ... แสดง log ของ iroh ใน console |
| `engine` | `"auto"` | เฉพาะ Node.js: `"native"` (เส้นทาง UDP ตรง) หรือ `"wasm"` (ผ่าน relay เสมอ) ดู [Node.js](#nodejs) |

Object ของ client มี: `fetch`, `XMLHttpRequest`, `EventSource`, `WebSocket`, `websocket(url, { protocols, headers })`,
`install(opts)`, `serviceWorker(opts)`, `proxyUrl(url)`, `objectUrl(url, init)`, `backend(origin)`, `backends`, `cookies`, `endpointId` (id ของ
client นี้ ซึ่ง backend เห็นเป็น `X-Iroh-Remote-Id`), `exportSecretKey()`, `clearCache()`, `refreshRelayToken()`, `close()` และใน
Node.js มี `engine` กับ `connectionInfo(endpointId?)`
<a id="backend-iroh-gateway"></a>
## Backend: `iroh-gateway`

<a id="install"></a>
### การติดตั้ง

- **Linux / macOS** ด้วยคำสั่งเดียว (ติดตั้งลง `/usr/local/bin` เมื่อรันเป็น root หรือเมื่อเขียนลงไปได้ ไม่เช่นนั้นจะลง `~/.local/bin`):
  ```bash
  curl -fsSL https://raw.githubusercontent.com/irohkit/iroh-bridge/main/scripts/install-gateway.sh | sh
  ```
  สคริปต์จะเลือก build ที่ตรงกับ OS และ CPU ของคุณ ตรวจ SHA-256 แล้วพิมพ์ขั้นตอนถัดไปให้ ถ้าต้องการล็อกเวอร์ชันให้ใช้
  `IROH_GATEWAY_VERSION=v0.5.1` หรือเลือกไดเรกทอรีด้วย `IROH_GATEWAY_INSTALL_DIR=...` (ตั้งค่าไว้ฝั่ง `sh` ของ
  pipe) ต้องการแค่ `curl` หรือ `wget`, `tar` และ `sha256sum` หรือ `shasum`
- **Windows** (PowerShell ติดตั้งลง `%LOCALAPPDATA%\iroh-gateway` และเพิ่มเข้า `PATH` ของผู้ใช้ ไม่ต้องใช้สิทธิ์ admin):
  ```powershell
  irm https://raw.githubusercontent.com/irohkit/iroh-bridge/main/scripts/install-gateway.ps1 | iex
  ```
- **systemd** (เริ่มทำงานตอนบูต รันด้วย user ของตัวเอง เก็บ key ไว้ที่ `/var/lib/iroh-gateway`):
  ```bash
  curl -fsSL https://raw.githubusercontent.com/irohkit/iroh-bridge/main/scripts/install-gateway.sh | sudo sh -s -- --systemd http://127.0.0.1:8080
  # หรือจาก checkout ของ repo โดยใช้ binary ที่ build เองหรือดาวน์โหลดมา:
  sudo ./deploy/install.sh http://127.0.0.1:8080 [path/to/iroh-gateway]
  ```
- **ดาวน์โหลดเอง** จาก [releases](https://github.com/irohkit/iroh-bridge/releases) (ทุก archive มีไฟล์
  `.sha256` วางคู่กัน) หรือ build เองด้วย `cargo build --release -p iroh-gateway`:

  | แพลตฟอร์ม | ไฟล์ใน release |
  |---|---|
  | Linux x86_64 / arm64 แบบ static (ใช้ได้ทุก distro รวมถึง Alpine และ glibc รุ่นเก่า) | `iroh-gateway-<tag>-{x86_64,aarch64}-unknown-linux-musl.tar.gz` |
  | Linux x86_64 / arm64, glibc ≥ 2.35 | `iroh-gateway-<tag>-{x86_64,aarch64}-unknown-linux-gnu.tar.gz` |
  | macOS Apple silicon / Intel | `iroh-gateway-<tag>-{aarch64,x86_64}-apple-darwin.tar.gz` |
  | Windows x64 (รันบน Windows 11 on ARM ได้ด้วย) | `iroh-gateway-<tag>-x86_64-pc-windows-msvc.zip` |

  บน Windows ไม่มีการ reload config ด้วย SIGHUP (`systemctl reload`) ให้ restart gateway แทน กด Ctrl-C เพื่อหยุด
- **Docker**: `ghcr.io/irohkit/iroh-gateway` ดูคำสั่ง `docker run` และไฟล์ compose ได้ที่ [`deploy/docker`](deploy/docker)

ตอนเริ่มทำงาน gateway จะพิมพ์ `IROH_ENDPOINT_ID=...` ออกมา (`journalctl -u iroh-gateway | grep IROH_ENDPOINT_ID`) id นี้จะคง
เดิมตราบใดที่ยังเก็บไฟล์ key ไว้ ต้องเก็บไฟล์นี้เป็นความลับ เพราะใครที่ได้ไฟล์ไปจะปลอมตัวเป็น backend ของคุณได้ gateway
ต้องการแค่ UDP/HTTPS ขาออก ไม่ต้องเปิดพอร์ตขาเข้าเลย

<a id="configuration"></a>
### การตั้งค่า

| ตัวแปร / flag | ค่าเริ่มต้น | |
|---|---|---|
| `BACKEND_URL` / `--backend` | `http://127.0.0.1:8080` | upstream (หลายตัวคั่นด้วยจุลภาค) |
| `IROH_ROUTES` / `--route` | | route ตาม host/path ดูด้านล่าง |
| `IROH_ALLOW_CLIENTS` / `--allow-client` | ทุกคน | endpoint id ของ client ที่อนุญาต |
| `IROH_ALLOW_PATHS` / `--allow-path` | ทุก path | path prefix ที่อนุญาต |
| `IROH_RATE_LIMIT` / `--rate-limit` | ปิด | จำนวน request ต่อวินาทีต่อ endpoint id ของ client เกินนี้ได้ 429 ([ด้านล่าง](#rate-limits-per-client)) |
| `IROH_RATE_BURST` / `--rate-burst` | 2 × rate อย่างน้อย 1 | จำนวน request ที่ client ส่งพร้อมกันได้ก่อนเริ่มคุม rate |
| `IROH_MAX_CONNECTIONS_PER_CLIENT` / `--max-connections-per-client` | ปิด | จำนวน connection ที่เปิดได้ต่อ endpoint id ของ client |
| `IROH_RATE_LIMIT_EXEMPT` / `--rate-limit-exempt` | | endpoint id ที่ไม่ต้องโดน limit ราย client |
| `IROH_KEY_FILE` / `--key-file` | `iroh-gateway.key` | secret key (สร้างให้ตอนรันครั้งแรก) |
| `IROH_RELAY_URL` / `--relay-url` | relay สาธารณะของ n0 | relay ของคุณเอง ([คู่มือ](deploy/relay/README.md)) |
| `IROH_RELAY_TOKEN` / `--relay-token` | | token สำหรับ relay ที่ต้องใช้ token |
| `MAX_BODY_BYTES` / `--max-body` | 32 MiB | ขนาด request body สูงสุด |
| `UPSTREAM_COOLDOWN_SECS` | `10` | ระยะเวลาที่ข้าม upstream ที่ติดต่อไม่ได้ |
| `RUST_LOG` | `iroh_gateway=info` | ระดับ log ทั้งหมดออกทาง stdout ผ่าน buffer ถ้าฝั่งอ่าน log ค้าง บรรทัด log จะถูกทิ้งแทนที่จะไปบล็อก request |
| `IROH_ADMIN_ADDR` / `--admin-addr` | ปิด | พอร์ต admin ในเครื่องสำหรับ health และ metrics ([Monitoring](#monitoring)) |
| `IROH_CONFIG_FILE` / `--config-file` | | ไฟล์ค่าตั้งที่ reload ได้โดยไม่ต้อง restart ([ด้านล่าง](#changing-routes-and-allow-lists-without-a-restart)) |

ขีดจำกัดและ timeout ทุกตัวมี flag ชื่อเดียวกัน (`--max-head`, `--head-timeout-secs`, ...) ค่าเริ่มต้นเผื่อไว้มากพอ
สำหรับการใช้งานปกติ และกันไม่ให้ client รายเดียวยึด gateway ไว้ได้:

| ตัวแปร | ค่าเริ่มต้น | |
|---|---|---|
| `MAX_HEAD_BYTES` | 64 KiB | request line + header |
| `MAX_WS_MESSAGE_BYTES` | 16 MiB | WebSocket message หนึ่งข้อความ (backend จะได้ 1009 เมื่อเกิน) |
| `IROH_MAX_CONNECTIONS` | 4096 | จำนวน connection ของ client ที่เปิดพร้อมกัน |
| `IROH_MAX_STREAMS_PER_CONNECTION` | 100 | จำนวน request พร้อมกันต่อ client |
| `IROH_MAX_REQUESTS` | 8192 | จำนวน request พร้อมกันทั้งหมด (เกินนี้ได้ 503) |
| `HEAD_TIMEOUT_SECS` | 10 | เวลาที่ใช้ส่ง request head ได้ (408) |
| `BODY_IDLE_TIMEOUT_SECS` | 60 | upload ที่ค้างนานเท่านี้จะถูกตัด (408) |
| `UPSTREAM_CONNECT_TIMEOUT_SECS` | 10 | เวลาเชื่อมต่อ upstream |
| `UPSTREAM_TIMEOUT_SECS` | 300 | เวลารอ response head จาก upstream (504; `0` = ปิด) response แบบ streaming เช่น SSE จะไม่ถูกตัดเมื่อเริ่มส่งแล้ว |

<a id="several-upstreams-routing-by-host-and-path"></a>
### หลาย upstream และการ route ตาม host และ path

```bash
# กระจายไปหลาย backend process ตัวที่ปฏิเสธการเชื่อมต่อจะถูกข้ามไปชั่วคราว
BACKEND_URL=http://10.0.0.5:8080,http://10.0.0.6:8080 iroh-gateway

# gateway ตัวเดียวอยู่หน้าหลาย service ตัวที่ตรงเจาะจงที่สุดชนะ BACKEND_URL เป็นตัวสำรองสุดท้าย
IROH_ROUTES='api.example.com=http://127.0.0.1:8080;files.example.com=http://10.0.0.7:9000,http://10.0.0.8:9000;/admin=http://127.0.0.1:9100' iroh-gateway
```

host คือ host ที่ frontend ใช้ใน URL (`iroh-fetch` ส่งมาเป็น `x-iroh-host`) gateway id เดียวจึงรองรับหลาย origin
ใน `backends` ได้ path prefix จะจับคู่ทั้ง segment และไม่ถูกตัดออกจาก path

<a id="access-control"></a>
### การควบคุมการเข้าถึง

```bash
IROH_ALLOW_CLIENTS=<endpoint id>,<endpoint id>   # ปฏิเสธ client อื่นตั้งแต่ตอนเชื่อมต่อ
IROH_ALLOW_PATHS=/api,/ws                        # path อื่นทั้งหมดได้ 403
```

endpoint id ของ client จะสุ่มใหม่ทุก session ของเบราว์เซอร์ เว้นแต่จะกำหนด key ตายตัวให้ client ดังนั้น allow list ของ client
จึงเหมาะกับการใช้งานแบบ server-to-server เป็นหลัก (Node, `iroh-proxy`) สำหรับผู้ใช้บนเบราว์เซอร์ ให้ยืนยันตัวตนใน backend
แบบเดียวกับที่ทำบน URL สาธารณะ (session, token) ส่วน `X-Iroh-Remote-Id` ให้ id ที่ยืนยันแล้วไว้ใช้ทำ rate limit หรือ audit

<a id="rate-limits-per-client"></a>
#### Rate limit ราย client

ใครก็ตามที่รู้ endpoint id ของ gateway จะเรียกมันได้ เหมือน URL สาธารณะ นอกจากขีดจำกัดรวมแล้ว gateway ยังจำกัด
client แต่ละรายได้ โดยใช้ endpoint id ที่ยืนยันแล้วตัวเดียวกับที่ backend เห็นใน `X-Iroh-Remote-Id`:

```bash
IROH_RATE_LIMIT=10                     # จำนวน request ต่อวินาทีต่อ client ในระยะยาว (ใช้เศษส่วนอย่าง 0.5 ได้)
IROH_RATE_BURST=20                     # จำนวนที่ส่งมาพร้อมกันได้ (ค่าเริ่มต้น: สองเท่าของ rate อย่างน้อย 1)
IROH_MAX_CONNECTIONS_PER_CLIENT=4      # connection เพิ่มเติมจาก id เดียวกันจะถูกปฏิเสธ
IROH_RATE_LIMIT_EXEMPT=<endpoint id>   # เช่น server ของคุณเอง คั่นด้วยจุลภาค
```

- เป็น token bucket ต่อ client: client ส่งได้ `IROH_RATE_BURST` request พร้อมกัน จากนั้นได้ `IROH_RATE_LIMIT` ต่อ
  วินาที การ upgrade เป็น WebSocket นับเป็นหนึ่ง request ส่วนข้อความบน WebSocket ที่เปิดอยู่ไม่นับ
- request ที่เกิน limit จะได้ `429 Too Many Requests` พร้อม `retry-after` (วินาที) จาก gateway โดยตรง ตั้งแต่ก่อน
  อ่าน head ด้วยซ้ำ จึงไม่กินช่อง request และไม่ไปถึง backend เลย นับไว้ใน
  `iroh_gateway_rejected_total{reason="rate_limited"}` และ connection ที่ถูกปฏิเสธนับเป็น `reason="client_connection_limit"`
  log จะแสดงไม่เกินหนึ่งบรรทัดต่อ 10 วินาทีสำหรับแต่ละกรณี เพื่อไม่ให้การยิงถล่มล้น log ไปด้วย
- หน่วยความจำมีขอบเขตเสมอ bucket ที่เติมเต็มแล้วไม่มีข้อมูลอะไรให้เก็บ จึงถูกทิ้ง (กวาดทุก
  นาที) และติดตาม client ได้สูงสุด 16384 ราย (`iroh_gateway_rate_limit_tracked_clients`) เมื่อตารางเต็มไปด้วย
  client ที่ยังโดนจำกัดอยู่ทั้งหมด client ใหม่จะใช้ overflow bucket ร่วมกันหนึ่งอัน แทนที่จะเบียดใครออก
  เพราะการสร้าง endpoint id ใหม่ไม่มีต้นทุน ถ้าเบียดออก การยิงด้วย id ใหม่จำนวนมากจะรีเซ็ต limit ของ client ที่
  กำลังโดนจำกัดได้ การยิงด้วย id ใหม่จึงได้โควตารวมเท่า client เดียว และ client ที่ติดตามอยู่แล้วไม่ได้รับผลกระทบ
  แลกกับการที่ client ใหม่จริงๆ ต้องโดนจำกัดรวมกันไปจนกว่าจะมีที่ว่าง
- limit ราย id หยุด client ที่ทำงานผิดปกติรายเดียวได้ (retry loop ที่มีบั๊ก, scraper) แต่หยุดคนที่สร้าง id ใหม่ไม่ได้
  สำหรับกรณีนั้นยังต้องพึ่งขีดจำกัดรวม (`IROH_MAX_CONNECTIONS`, `IROH_MAX_REQUESTS`) และ `IROH_ALLOW_CLIENTS`
  client บนเบราว์เซอร์จะได้ id ใหม่ทุก session เว้นแต่จะใช้ key ตายตัว
- ค่าทั้งสี่ตัว reload ได้พร้อมไฟล์ config ด้านล่าง limit ของ connection ที่ลดลงจะมีผลกับ connection ใหม่ ส่วนที่เปิดอยู่
  ยังคงอยู่

<a id="changing-routes-and-allow-lists-without-a-restart"></a>
### เปลี่ยน route และ allow list โดยไม่ต้อง restart

ใส่ค่าที่ต้องการเปลี่ยนระหว่างรันไว้ในไฟล์ แล้วตั้ง `IROH_CONFIG_FILE` ให้ชี้ไปที่ไฟล์นั้น ไฟล์ใช้รูปแบบ
`EnvironmentFile` ของ systemd จึงใส่ตัวแปรอื่นของ gateway ไว้ในไฟล์เดียวกันได้ด้วย (แต่จะ reload เฉพาะ key เหล่านี้และ
[rate limit ราย client](#rate-limits-per-client)):

```bash
# /etc/iroh-gateway.env
BACKEND_URL=http://127.0.0.1:8080
IROH_ROUTES="api.example.com=http://127.0.0.1:8080;/admin=http://127.0.0.1:9100"
IROH_ALLOW_CLIENTS=
IROH_ALLOW_PATHS=
IROH_RATE_LIMIT=10
IROH_RATE_BURST=
IROH_MAX_CONNECTIONS_PER_CLIENT=
IROH_RATE_LIMIT_EXEMPT=
```

แก้ไฟล์แล้วส่ง `SIGHUP`: `systemctl reload iroh-gateway` (unit ที่มากับโปรเจกต์มี `ExecReload` อยู่แล้ว) หรือ
`docker kill -s HUP <container>` connection ที่เปิดอยู่ยังคงอยู่ request ที่กำลังทำงานจะจบด้วย config ที่ใช้ตอนเริ่ม
และ request ใหม่จะใช้ config ใหม่ client ที่ถูกลบออกจาก `IROH_ALLOW_CLIENTS` จะถูกตัดการเชื่อมต่อใน request ถัดไป
ไฟล์ที่มีข้อผิดพลาดจะถูกปฏิเสธทั้งไฟล์ gateway จะ log สาเหตุไว้และรันต่อด้วย config เดิม ค่าในไฟล์
มีลำดับเหนือกว่า flag และ environment (ค่าว่างหมายถึงไม่ได้ตั้ง) ส่วน key ที่ไม่ได้ใส่ไว้ในไฟล์จะกลับไปใช้ค่าจากสองแหล่งนั้น สถานะ health ของ upstream และ
ตัวนับราย route จะเริ่มใหม่สำหรับ route ที่ reload `iroh_gateway_config_reloads_total{result="ok"|"error"}` นับจำนวนการ reload

<a id="monitoring"></a>
### Monitoring

เริ่ม gateway พร้อมพอร์ต admin ในเครื่อง:

```bash
IROH_ADMIN_ADDR=127.0.0.1:9180 iroh-gateway
```

| Endpoint | |
|---|---|
| `GET /healthz` | `200 ok` เมื่อ gateway ออนไลน์บน relay แล้ว (สำหรับ health check ของ systemd/Docker/Kubernetes) |
| `GET /readyz` | `200` เมื่อทุก route มี upstream ที่ healthy ไม่เช่นนั้น `503` พร้อมรายการตัวที่ไม่ healthy |
| `GET /status` | JSON: endpoint id, เวอร์ชัน, uptime, สถานะ relay, connection, request ที่กำลังทำงาน, rate limit, health ของ upstream |
| `GET /metrics` | Prometheus: request แยกตาม route และกลุ่ม status, การ upgrade เป็น WebSocket, ความล้มเหลวและ health ของ upstream, การปฏิเสธแยกตามเหตุผล (รวมถึง `rate_limited`), connection, request ที่กำลังทำงาน, จำนวน client ที่ติดตาม rate limit อยู่, histogram ของระยะเวลา request |

พอร์ตนี้ bind เฉพาะ loopback address เว้นแต่จะใส่ `--admin-allow-public` (เพราะไม่มีการยืนยันตัวตน) label ไม่มี
path หรือ query ของ request อยู่เลย

<a id="security"></a>
### ความปลอดภัย

endpoint id ของ gateway เป็นข้อมูลสาธารณะ gateway จึงถือว่า client ทุกรายไว้ใจไม่ได้:

- path ต้องสะอาด เป็น ASCII ที่พิมพ์ได้และขึ้นต้นด้วย `/` dot segment ในทุกรูปแบบการเข้ารหัส (`..`, `%2e%2e`, `..;`, หรือมี tab
  หรือขึ้นบรรทัดใหม่แทรกอยู่) จะถูกปฏิเสธ การ route และ `--allow-path` จึงถูกเลี่ยงไม่ได้ และ request ออกนอก
  upstream ที่ตั้งไว้ไม่ได้
- ชื่อและค่าของ header ที่มี control character รวมถึง `CONNECT` และ `TRACE` จะถูกปฏิเสธ hop-by-hop header และทุก
  header ที่ gateway ตั้งเอง (`X-Iroh-*`, `X-Forwarded-*`, `X-Real-IP`) จะถูกตัดทิ้งเมื่อมาจาก client
- client จะเห็นแค่ error แบบทั่วไป URL ของ upstream และ error ของ OS ไปอยู่ใน log ของ gateway เท่านั้น
- request ที่ช้าหรือใหญ่เกินจะถูกตัดด้วยขีดจำกัดด้านบน client รายเดียวจึงแย่งทรัพยากรจนคนอื่นไม่ได้ใช้ไม่ได้
- ไฟล์ key ถูกสร้างด้วย mode `0600` และ gateway จะไม่ยอมเริ่มทำงานถ้า key อ่านได้โดยทุกคน
- systemd unit รัน gateway โดยไม่มี capability ใดๆ และอยู่ใน sandbox ที่รัดกุม (ดู [`deploy/iroh-gateway.service`](deploy/iroh-gateway.service))

ทั้งหมดนี้ตรวจสอบแล้วโดยการโจมตี gateway ทดสอบด้วย iroh client แบบ raw (smuggling, เล่นกับ path, slow-loris, การยิงถล่ม,
WebSocket frame ที่ใหญ่เกิน, การรั่วของ error)

<a id="what-your-backend-sees"></a>
### สิ่งที่ backend ของคุณเห็น

HTTP request ปกติ: method, path, query, header และ body เหมือนเดิม พร้อมกับ:

- `X-Iroh-Remote-Id`: endpoint id ของผู้เรียก ยืนยันแล้วด้วย QUIC handshake (client ปลอมไม่ได้)
- `X-Forwarded-Proto: https` เพราะ tunnel เข้ารหัสแบบ end-to-end
- `X-Forwarded-Host`: host ที่ frontend ใช้ (เช่น `api.example.com`)

WebSocket จะถูก proxy ไปที่ `ws://` (หรือ `wss://`) บน upstream ตัวเดียวกัน

<a id="local-port-iroh-proxy"></a>
## พอร์ตในเครื่อง: `iroh-proxy`

เปิด gateway ให้เป็น URL ในเครื่อง คล้าย `ssh -L` สำหรับ curl, Postman, สคริปต์ หรือค่า proxy ของ dev server:

```bash
npx -p iroh-fetch iroh-proxy <endpoint id>[,<replica id>...] --port 8080
curl http://127.0.0.1:8080/users
```

HTTP (streaming ได้ทั้งสองทาง), redirect และ cookie ถูกส่งผ่านโดยไม่แตะต้อง และ WebSocket ก็ใช้ได้
(`ws://127.0.0.1:8080/ws`) ค่าเริ่มต้นจะ listen แค่ที่ `127.0.0.1` และเพื่อกันไม่ให้เว็บไซต์อื่นใช้งานมันผ่าน
เบราว์เซอร์ของคุณ (DNS rebinding, cross-site request) จะรับเฉพาะ `Host: localhost`/IP และ header `Origin` ที่เป็น same-origin
เพิ่มข้อยกเว้นได้ด้วย `--allow-host` และ `--allow-origin`

<a id="postman-insomnia-curl"></a>
### Postman, Insomnia, curl

เครื่องมือเหล่านี้คุยด้วย HTTP ธรรมดา จึงให้ชี้ไปที่ `iroh-proxy` ถ้าใช้ relay ที่ป้องกันด้วย token ให้เพิ่ม
`--relay-url https://relay.example.com --relay-token ...` (หรือ `IROH_RELAY_TOKEN`)

```bash
npx -y -p iroh-fetch iroh-proxy <endpoint id> --port 8787
```

จากนั้นใช้ `http://127.0.0.1:8787` เป็น base URL ทั้ง cookie, redirect, header `Authorization: Bearer`, upload,
response แบบ stream, SSE, conditional request (`If-None-Match` / 304) และ WebSocket ทำงานเหมือนคุยกับ backend
โดยตรง [`examples/postman/`](examples/postman/iroh-bridge-demo.postman_collection.json) เป็น collection ที่มี 22
request และ 28 test สำหรับ backend ตัวเดโม import เข้า Postman หรือรันจาก command line ก็ได้:

```bash
npx -y newman run examples/postman/iroh-bridge-demo.postman_collection.json
```

<a id="run-your-own-relay"></a>
## รัน relay ของคุณเอง

**ค่าเริ่มต้นคือ relay สาธารณะของ n0** ถ้าไม่ได้ตั้ง relay ไว้ที่ไหนเลย ทั้ง gateway, client และ `iroh-proxy` จะใช้ relay
เหล่านี้ และทุกอย่างใช้งานได้ทันที

เบราว์เซอร์ส่ง UDP ไม่ได้ ทุก request จากเบราว์เซอร์จึงต้องผ่าน relay ดังนั้นการมี relay ของตัวเองที่อยู่ใกล้ผู้ใช้คือวิธีเพิ่มความเร็ว
ได้มากที่สุด ในเดโม relay ของตัวเองลด latency ต่อ request จาก 69 ms เหลือ 14 ms และเพิ่มความเร็ว upload จาก 1.3 เป็น
11.5 MB/s นอกจากนี้ยังจำกัดให้ใช้ได้เฉพาะ client ที่มี token ได้ด้วย

**[คู่มือฉบับเต็ม: deploy/relay/README.md](deploy/relay/README.md)** ครอบคลุมการติดตั้งบนเซิร์ฟเวอร์ใหม่
(`sudo ./deploy/relay/install.sh relay.example.com you@example.com`) หรือติดตั้งข้าง Caddy/nginx/Traefik ที่มีอยู่แล้ว
การสร้างและหมุนเวียน (rotate) token และการแก้ปัญหา สรุปสั้นๆ คือ เมื่อ relay รันแล้ว:

```bash
IROH_RELAY_URL=https://relay.example.com IROH_RELAY_TOKEN=<token, if required> iroh-gateway
```

```ts
createIrohFetch({ relayUrl: "https://relay.example.com", relayToken: getRelayToken, backends: { ... } });
```

ถ้าไม่ตั้งทั้งสองค่า ก็จะใช้ relay สาธารณะต่อไป

<a id="relay-token-from-your-backend"></a>
### รับ relay token จาก backend ของคุณ

token ที่เป็น string ในเว็บแอปจะติดไปกับ bundle ใครก็อ่านได้ และถ้าจะ rotate ก็ต้อง redeploy ใหม่ ทางที่ดีกว่าคือให้
`relayToken` เป็นฟังก์ชันที่ไปขอ token จาก backend ของคุณเอง ซึ่งจะให้ token เฉพาะผู้ใช้ที่ล็อกอินแล้ว ตัวอย่างใน Next.js:

```ts
// app/api/relay-token/route.ts (IROH_RELAY_TOKEN เป็น env var ฝั่ง server เท่านั้น ไม่มี NEXT_PUBLIC_)
export const dynamic = "force-dynamic";

export async function GET() {
  if (!(await getSession())) return new Response(null, { status: 401 }); // ระบบ auth ของคุณ
  return new Response(process.env.IROH_RELAY_TOKEN, { headers: { "cache-control": "no-store" } });
}
```

```tsx
const getRelayToken = async () => {
  const res = await fetch("/api/relay-token");
  if (!res.ok) throw new Error(`relay token: ${res.status}`);
  return res.text();
};

<IrohProvider relayUrl="https://relay.example.com" relayToken={getRelayToken} backends={...}>
```

iroh-fetch จะเรียกฟังก์ชันนี้ก่อนสร้าง client และเรียกอีกครั้งทุกครั้งที่ relay ปฏิเสธ client (เช่น หลังจากคุณ rotate token)
แล้วลอง dial ใหม่อีกหนึ่งครั้ง ถ้าอยากได้ token ใหม่เร็วกว่านั้น ให้เรียก `iroh.refreshRelayToken()` เอง (เช่น หลังล็อกอิน) หรือตั้ง
`relayTokenRefreshMs` ถ้า token ที่ได้ต่างจากตัวปัจจุบัน iroh-fetch จะสร้าง client เบื้องหลังตัวใหม่แทนตัวเดิม โดย endpoint id
ยังเหมือนเดิม (secret key ถูกส่งต่อไป ไม่ว่าจะใช้ `secretKey` / `persistIdentity` หรือไม่) cookie และ HTTP cache ก็ยังอยู่ แต่
WebSocket, stream และ request ที่ค้างอยู่บน client ตัวเก่าจะถูกตัด และจะ dial gateway ใหม่ใน request ถัดไป ถ้าได้ token
เดิมกลับมาจะไม่มีอะไรเปลี่ยน ถ้าฟังก์ชัน throw error `createIrohFetch` (หรือการ refresh) จะล้มเหลวด้วย error นั้น
[`examples/nextjs`](examples/nextjs) ทำแบบนี้ไว้ และจะ fallback ไปใช้ `NEXT_PUBLIC_IROH_RELAY_TOKEN` ถ้าฝั่ง server ไม่มี
`IROH_RELAY_TOKEN`

<a id="tested"></a>
## ผลการทดสอบ

- **เบราว์เซอร์:** self-test 17 ข้อของเดโมผ่านในทุกตัวต่อไปนี้ ([`scripts/browser-test.py`](scripts/browser-test.py),
  Playwright):

  | | |
  |---|---|
  | Desktop | Google Chrome 155, Microsoft Edge 155, Chromium 156 (engine ของ Brave, Opera, Vivaldi), Firefox 157, WebKit 27 (engine ของ Safari) |
  | Mobile (จำลอง) | iPhone 15 และ iPad Pro 11 (Safari), Pixel 7 (Chrome), Galaxy (Samsung Internet), Facebook in-app บน Android |
  | ไม่มี service worker | LINE in-app บน iPhone, Firefox ที่บล็อก service worker: ผ่าน 15/15 โดยข้ามการตรวจ service worker 2 ข้อ |

  บั๊กเฉพาะเบราว์เซอร์ที่เจอและแก้ไปด้วยวิธีนี้: Firefox ไม่มี stream `Request.body`; WebKit ปฏิเสธชื่อโฮสต์ของ relay ที่ลงท้าย
  ด้วยจุด ทั้งกับ WebSocket และกับการวัด latency ของ relay; และแอปไม่เคยพร้อมใช้งานในที่ที่ service worker ควบคุมหน้าไม่ได้
  การรันชุดทดสอบนี้ซ้ำๆ ยังพบว่า gateway สำรอง (standby) ที่อยู่บน relay อื่นบางครั้งใช้เวลา dial นานกว่า 15 วินาที ตอนนี้
  client จึงเพิ่มการ dial แบบ address lookup ธรรมดาหลังผ่านไป 1.5 วินาที replica ที่ล้มเหลวจะลองใหม่เรื่อยๆ เบื้องหลังแบบ backoff
  และเดโมระบุ relay ของ gateway สำรองไว้ ทำให้ standby เชื่อมต่อไว้ก่อนที่จะต้องใช้
- **Load** ([`scripts/loadtest.mjs`](scripts/loadtest.mjs), relay บน VPS 2 core): ไม่มี error เลยที่ 1,000 req/s จาก 100
  client, WebSocket พร้อมกัน 100 ตัว และ 50 client ที่ส่งทราฟฟิกผสม โดย latency อยู่ที่ราว 13 ms ตลอด gateway แต่ละตัวใช้
  CPU ราว 15% ของหนึ่ง core ที่ 500 req/s ส่วนที่อิ่มตัวก่อนคือ TLS front end ของ relay (Caddy) (CPU ราว 58% ที่ 1,000 req/s)
  การเปิดการเชื่อมต่อ (ตั้งแต่โหลดหน้าจนได้ response แรก) ใช้ราว 85 ms ที่ p50 เมื่อตั้งค่า relay ของตัวเองไว้
- **Relay ล่ม:** ดู [Redundancy](deploy/relay/README.md#7-redundancy-surviving-a-relay-outage)
- **ความปลอดภัย:** ดู [Security](#security)

<a id="production-notes"></a>
## ข้อควรรู้สำหรับ production

- **รัน relay ของตัวเอง**: relay สาธารณะของ n0 ใช้ตอนพัฒนาได้ แต่ไม่มี SLA และอาจถูกจำกัด rate ดู
  [รัน relay ของคุณเอง](#run-your-own-relay) หรือใช้บริการ relay แบบ hosted ของ n0
- **ความปลอดภัย**: ทราฟฟิกถูกเข้ารหัสแบบ end-to-end ด้วย QUIC/TLS และ relay อ่านไม่ได้ ใครก็ตามที่รู้ endpoint id
  สามารถเข้าถึง gateway นั้นได้ เหมือนกับ URL สาธารณะ ดังนั้นยังต้องทำ authentication ใน backend เหมือนเดิม และใช้
  allow-list ของ gateway ในกรณีที่เหมาะสม
- **Latency และ throughput**: เบราว์เซอร์ส่ง UDP ไม่ได้ ฝั่งเบราว์เซอร์จึงผ่าน relay เสมอ เมื่อผ่าน relay สาธารณะของ n0
  เราวัดได้ราว 70–120 ms ต่อ request และ upload ได้ 1–3.5 MB/s ถ้ามี relay ใกล้ผู้ใช้จะได้ผลดีกว่านี้
  Node.js บนแพลตฟอร์มที่มี prebuild จะใช้ [native engine](#nodejs) และมักเชื่อมต่อตรงได้ (ต้องอนุญาต UDP ขาออก)
  โดยใช้ relay แค่ตอนตั้งค่าการเชื่อมต่อ
- **ขนาด WASM**: ราว 3.4 MB (ราว 1.4 MB เมื่อ gzip) และถูก cache หลังโหลดครั้งแรก ควรโหลดแบบ lazy (React provider ทำให้แล้ว)

<a id="troubleshooting"></a>
## การแก้ปัญหา

- **`TimeoutError: could not reach gateway`**: gateway ออฟไลน์อยู่ หรือ endpoint id ผิดหรือเป็นค่าเก่า ดู log ของ gateway
  เพื่อหา `IROH_ENDPOINT_ID` ปัจจุบัน
- **`no backend configured for https://...`**: origin ของ URL ไม่ได้อยู่ใน `backends`
- **Node.js: `address lookup ... fetch failed` / `ETIMEDOUT`**: มักเกิดกับเครื่องที่ตั้งค่า IPv6 ไว้แต่ใช้งานไม่ได้จริง ทำให้
  `fetch` ของ Node ค้างที่ IPv6 ให้รัน Node ด้วย `NODE_OPTIONS=--dns-result-order=ipv4first`

<a id="development"></a>
## การพัฒนา

```
crates/gateway     iroh-gateway (Rust)
crates/client-core ตัว iroh client เอง (การเชื่อมต่อ, การ dial, streaming) ใช้ร่วมกันโดย:
crates/web-client  build แบบ WASM (เบราว์เซอร์ และ Node.js ที่ไม่มี native addon)
crates/node-client native addon สำหรับ Node.js (napi-rs)
crates/proto       wire format: หนึ่ง request หรือหนึ่ง WebSocket ต่อหนึ่ง QUIC stream
packages/iroh-fetch แพ็กเกจ npm (JS API, React bindings, iroh-proxy CLI, tests)
examples/nextjs    แอปเดโมพร้อม self-test (deploy บน Vercel)
examples/backend   backend จำลองที่ใช้ในเดโมและการทดสอบ
deploy/            systemd unit, สคริปต์ติดตั้ง, Docker, การตั้งค่า relay
```

Build WASM (ต้องมี Rust พร้อม `wasm32-unknown-unknown`, `clang`, `wasm-bindgen-cli` ที่ตรงกับ `Cargo.lock` และ `wasm-opt`
ถ้าต้องการ):

```bash
./scripts/build-wasm.sh
```

Build native addon สำหรับเครื่องนี้ลงใน `packages/iroh-fetch/native/` ซึ่ง iroh-fetch จะมองหาก่อนแพ็กเกจ prebuild
(ใช้ `cargo` ธรรมดา ไม่ต้องใช้ `@napi-rs/cli`; ส่ง Rust target และ platform tag เพื่อ cross-build):

```bash
./scripts/build-native.sh
```

รัน end-to-end test กับ gateway ที่รันอยู่หน้า `examples/backend` (A อยู่หลัง gateway สองตัว B อยู่หลังหนึ่งตัว) รวมถึง
browser self-test และ load test:

```bash
cd packages/iroh-fetch
IROH_ENDPOINT_ID=<A1> IROH_ENDPOINT_ID_A2=<A2> IROH_ENDPOINT_ID_B=<B> npm test
# เพิ่ม IROH_RELAY_URL / IROH_RELAY_TOKEN เมื่อ gateway ใช้ relay ของคุณเอง;
# IROH_FETCH_BACKEND=native หรือ =wasm จะรันชุดทดสอบบน engine เดียว (CI รันทั้งสองแบบ)
python3 scripts/browser-test.py https://your-demo.example.com/
node scripts/loadtest.mjs --help
```

**การ release**: เพิ่มเวอร์ชันใน `packages/iroh-fetch/package.json`, commit แล้ว push tag ที่ตรงกัน (`git tag v0.2.0 && git push origin v0.2.0`)
จากนั้น CI จะ publish ขึ้น npm (trusted publishing ไม่มี token อยู่ใน repo): เริ่มจากแพ็กเกจ native addon `iroh-fetch-<platform>`
ทั้งหกตัวที่ build โดย `native-prebuilds.yml` แล้วจึงเป็น `iroh-fetch` ที่ pin แพ็กเกจเหล่านั้นไว้เป็น optional dependencies
นอกจากนี้ยังแนบ binary ของ gateway ไปกับ GitHub release และ push Docker image ด้วย


<a id="keeping-up-with-iroh"></a>
### ตามเวอร์ชัน iroh ให้ทัน

[`iroh-update.yml`](.github/workflows/iroh-update.yml) ตรวจ crates.io ทุกวันว่ามี iroh / iroh-relay ออกเวอร์ชันใหม่หรือไม่
ถ้ามี จะอัปเดต workspace, build gateway, WASM web client, native Node addon และ iroh-relay แล้วรันชุดทดสอบของ iroh-fetch
ทั้งหมดแบบ end to end ด้วย [`scripts/e2e-local.sh`](scripts/e2e-local.sh): relay ที่ป้องกันด้วย token, backend เดโมทั้งสองตัว
และ gateway สามตัวบน runner เดียว โดยใช้ client engine ทั้งสองแบบ

- ถ้าผ่านทั้งหมด: merge อัปเดตเข้า `main`, เพิ่ม patch version ของ iroh-fetch และ iroh-gateway, ติด tag และเริ่ม
  workflow สำหรับ release (npm, binary ของ gateway, Docker) ถ้า workflow ใดล้มเหลว จะเปิด issue
- ถ้ามีอะไรล้มเหลว: push branch `bot/iroh-<version>` และเปิด issue ที่ระบุขั้นตอนที่พัง พร้อมตัดตอน log มาให้ ถ้ารอบต่อไป
  ผ่าน issue นั้นจะถูกปิดเอง

รันเองได้จากแท็บ Actions ("Run workflow" ระบุเวอร์ชันได้ถ้าต้องการ) `scripts/e2e-local.sh` ก็รันในเครื่องได้เช่นกัน
เมื่อ build gateway, WASM และ native addon แล้ว และติดตั้ง `iroh-relay` ไว้

<a id="license"></a>
## License

MIT

iroh-bridge เป็นโปรเจกต์ของชุมชนที่สร้างบน [iroh](https://github.com/n0-computer/iroh) ไม่ได้มีส่วนเกี่ยวข้องหรือได้รับการรับรอง
จาก n0 บริษัทผู้พัฒนา iroh โลโก้ใน [`assets/brand/`](assets/brand) เป็นของโปรเจกต์นี้เอง
