# Track & Trace Android (SakkshAsset)

Kotlin Multiplatform warehouse + pharma traceability app. Talks to **TrackandTrace-backend** REST API (same contracts as iOS).

## Tech stack (this repo)

| Spec suggestion | This project |
|-----------------|--------------|
| Retrofit + OkHttp | **Ktor 3** + OkHttp (Android engine) |
| Hilt / Koin | Manual singletons (`SessionManager`, repositories) |
| DataStore | `LocalStorage` (expect/actual) |
| Room offline queue | Not yet — planned via WorkManager |
| Jetpack Compose | **Compose Multiplatform** |

## API base URL

Production only — all requests go to:

`https://api.tnt.sakksh.com`

Configured in `composeApp/src/commonMain/kotlin/core/network/Config.kt` (`Config.BASE_URL`).

## Build APK

```bash
./gradlew :composeApp:assembleDebug
```

Output: `composeApp/build/outputs/apk/debug/composeApp-debug.apk`

Install on device:

```bash
adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

## Authentication

- **Mobile (current):** OTP via `POST /auth/login` + `POST /auth/otp-verification` → JWT
- **Admin-style (spec):** `POST /auth/login` with `{ email, password }` — not wired in UI yet; extend `AuthRepository` if needed
- Token: `AuthInterceptor` adds `Authorization: Bearer <token>`
- 401: `UnauthorizedInterceptor` clears session

## GS1 utilities

| File | Purpose |
|------|---------|
| `utils/Gs1Parser.kt` | Parse `(01)GTIN(10)BATCH(17)EXP(21)SERIAL`, AI 00 SSCC, digital link |
| `utils/EpcUriBuilder.kt` | `buildSgtinEpcUri`, `buildSsccEpcUri`, path encoding |

Unit tests: `./gradlew :composeApp:cleanAllTests :composeApp:allTests` (or `:composeApp:testDebugUnitTest` on Android)

## API modules (Ktor repositories)

| Repository | Endpoints |
|------------|-----------|
| `AuthRepository` | `/auth/login`, `/auth/otp-verification` |
| `WmsRepository` | Picking, packing, receiving WMS |
| `SerializationRepository` | VRS verify, serial lookup, event lineage, barcode generation, **EPCIS write** (commission, aggregate, ship/receive, dispense, recall) |
| `AppRepository` | Scan/generation audit logs, barcode lookup |

### WMS (implemented UI + API)

- **Picker:** `PickerWmsFlow`, `PickerPickScreens` — scan tote + product/batch
- **Packer:** `PackingWmsFlow`, `PackingBoxScreens` — create box, add qty, seal
- **Receiver:** `ReceivingWmsFlow`, `ReceivingScreens` — order tree, verify lines, complete

### Receiving scan-pack (API ready)

```kotlin
WmsRepository().scanPackingReceiverPack(
    orderId = orderId,
    request = WmsPackingReceiverScanPackRequest(
        companyId = companyId,
        receiverId = userId,
        sscc = sscc,
        epcUri = EpcUriBuilder.buildSsccEpcUri(sscc),
        deviceType = "android",
    ),
)
```

Wire to `WarehouseMlKitScanner` on receive detail (SSCC scan) before `complete`.

### Pharma verify flow (API ready)

```kotlin
val epc = EpcUriBuilder.sgtinEpcUriFromScan(scanPayload)!!
SerializationRepository().verifySerial(
    VrsVerifyRequest(epcUri = epc, companyId = companyId),
)
```

## Architecture map

```
composeApp/src/commonMain/kotlin/
├── core/network/          # Ktor client, Config, repositories, DTOs
├── core/storage/          # JWT session
├── features/auth/         # Login + OTP
├── features/app/warehouse/wms/  # Picker / Packer / Receiver screens
├── features/app/scans/    # Auth product scan (Android SDK)
├── utils/                 # Gs1Parser, EpcUriBuilder
```

## EPCIS integration

`EpcisFlowService` orchestrates serialization write APIs:

| Trigger | Level | When (Android) |
|---------|-------|----------------|
| Unit commissioned | L1 | Any GS1 / Digital Link generation **with GTIN** (serial, batch, or GTIN used as commission key) |
| Units → case | L2 | Carton seal (when child EPC URIs are available) |
| Cases → pallet | L3 | Carton linked to pallet; pallet seal |
| Goods shipped | L3/L4 | Dispatch invoice submitted |
| Goods received | L3/L4 | Shipped picklist delivered; receiver order completed |
| Unit dispensed | L5 | `EpcisFlowService.dispenseUnit()` (API ready — wire to dispense UI) |
| Batch recalled | L4 | `EpcisFlowService.recallBatch()` (API ready — wire to recall UI) |
| Unpack / Repack | L3/L4 | `EpcisFlowService.unpackContainer()` / `repackProduct()` |

- **With GTIN** → commission stored (`POST /serialization/serials/commission`); serial key = AI 21, else batch, else expiry, else GTIN
- **No GTIN** → no EPCIS call

## Important rules (from backend)

- URL-encode EPC URIs in path segments (`EpcUriBuilder.encodeEpcUriForPath`)
- Set `device_type = "android"` on scan log payloads
- EPCIS failures are logged but do not block WMS / generation UI

## Networking

All API traffic uses the deployed backend at `https://api.tnt.sakksh.com` (HTTPS).
