# Serialization Error Fix

## Problem
Error: "No Suitable converter found for TypeInfo in SendOtpResponse - kotlin reflection not available"

This error occurred when trying to deserialize the API response from the send-otp endpoint.

## Root Cause
The Kotlin serialization library (`kotlinx-serialization-json`) was not explicitly added as a dependency, even though:
- The `@Serializable` annotations were present on the data classes
- The `kotlinx-serialization` plugin was configured in build.gradle.kts
- Ktor's `ktor-serialization-kotlinx-json` was included

## Solution

### 1. Added Missing Dependency
**File:** `composeApp/build.gradle.kts`

Added to `commonMain.dependencies`:
```kotlin
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
```

### 2. Enhanced JSON Configuration
**File:** `composeApp/src/commonMain/kotlin/core/network/HttpClientFactory.kt`

Updated the JSON serializer configuration:
```kotlin
json(
    Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        isLenient = true
        explicitNulls = false      // ← Added
        encodeDefaults = false     // ← Added
    }
)
```

**Why these settings:**
- `explicitNulls = false`: Handles optional/nullable fields better by not requiring explicit null values
- `encodeDefaults = false`: Doesn't encode default values, making the JSON more compact

## Changes Made

### Modified Files:
1. ✅ `composeApp/build.gradle.kts` - Added kotlinx-serialization-json dependency
2. ✅ `composeApp/src/commonMain/kotlin/core/network/HttpClientFactory.kt` - Enhanced JSON config

## Testing
1. ✅ Build successful
2. ✅ APK installed on device
3. Ready to test the login flow

## Next Steps
1. Open the app on your device
2. Enter an email or phone number
3. Click "Continue"
4. The API call should now work without serialization errors

## Technical Details

The error occurred because:
- Ktor's `ContentNegotiation` plugin needs the actual serialization library to convert JSON to Kotlin objects
- While `ktor-serialization-kotlinx-json` provides the integration, it requires `kotlinx-serialization-json` as the underlying engine
- Without this dependency, Ktor couldn't find a suitable converter for the `SendOtpResponse` type

The fix ensures that:
- The JSON serializer is properly available at runtime
- Nullable fields in the response models are handled correctly
- The serialization/deserialization process works seamlessly
