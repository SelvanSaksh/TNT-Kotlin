# Project Analysis: SakkshAsset (TNT-Kotlin)

## Overview
**SakkshAsset** is a modern **Kotlin Multiplatform (KMP)** mobile application targeting **Android** and **iOS**. The project utilizes **Compose Multiplatform** for a shared UI layer, enabling a consistent user experience across platforms while leveraging platform-specific capabilities when necessary.

## Technology Stack
- **UI Framework:** Jetpack Compose Multiplatform (Material 3).
- **Language:** Kotlin (100% logic and UI).
- **Networking:** [Ktor 3.1.3](https://ktor.io/) with Content Negotiation (JSON).
- **Serialization:** [Kotlinx Serialization](https://kotlinlang.org/docs/serialization.html).
- **Dependency Management:** Gradle Version Catalog (`libs.versions.toml`).
- **Image Loading:** [Coil 3](https://coil-kt.github.io/coil/).
- **Navigation:** [Jetpack Navigation Compose](https://developer.android.com/jetpack/compose/navigation).
- **Concurrency:** Kotlin Coroutines.
- **Date/Time:** Kotlinx Datetime.
- **Android Integrations:**
    - **ML Kit:** Barcode scanning.
    - **CameraX:** Camera lifecycle and preview.
    - **Google Play Services:** Location services.
    - **Razorpay:** Payment integration for subscriptions.

## Project Structure
The project follows a feature-based architecture within the `commonMain` source set:

- `core/`: Fundamental infrastructure components.
    - `di/`: Manual dependency injection/Singletons.
    - `network/`: Ktor client config, API client, and repositories.
    - `session/`: Role-based access and session logic.
    - `storage/`: Local persistence (Settings/Preferences).
    - `location/`: Shared location services.
- `features/`: Business logic and UI for specific app areas.
    - `auth/`: Login and OTP verification flow.
    - `app/`: Post-authentication features (Home, History, Scans, Warehouse).
- `navigation/`: Centralized navigation routing and `NavHost` implementation.
- `theme/`: Global styling and Material 3 theme definition.
- `components/`: Reusable UI widgets.

## Key Features & Business Logic
1. **Authentication:**
    - OTP-based login system with guest access support.
    - Session persistence and automatic re-authentication logic.
2. **Barcode Ecosystem:**
    - **Generation:** Supports GS1 2D, GS1 Digital, and Multi-Link barcodes.
    - **Scanning:** Integrated ML Kit and CameraX for robust barcode recognition.
3. **Warehouse Management:**
    - Role-specific workflows for **Picking**, **Packing**, and **Receiving**.
    - Dynamic UI switching between regular user and warehouse staff modes.
4. **Subscription Model:**
    - Feature gating based on subscription status.
    - Integrated Razorpay checkout flow.
5. **Asset & History Tracking:**
    - Detailed scan history and asset management.
    - Real-time tracking and tracing of barcodes.

## Architectural Patterns
- **Shared UI:** Most UI code resides in `commonMain`, with `androidMain` and `iosMain` providing platform-specific implementations (e.g., Camera, Location).
- **Single Source of Truth:** `SessionManager` and `WarehouseAccess` act as the primary state providers for user identity and authorization.
- **Network Resilience:** Includes a custom `NetworkMonitor` to handle connectivity issues gracefully with a global `NoInternetConnectionView`.

## Observations & Recommendations
- **Large Files:** Some files like `Home.kt` and `App.kt` are relatively large. Extracting sub-components or moving complex logic to ViewModels (using `androidx.lifecycle.viewmodelCompose`) could improve maintainability.
- **Manual DI:** The project uses a mix of singletons (`object ApiClient`) and manual injection. For a project of this scale, adopting a DI framework like **Koin** might streamline dependency management.
- **Documentation:** The root directory contains numerous `.md` files detailing specific bug fixes, indicating a highly iterative development process. Keeping these summarized in a central `CHANGELOG.md` could be beneficial.

---
*Analysis performed on 2026-09-18*
