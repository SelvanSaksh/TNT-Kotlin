# Local Storage Implementation

## Overview
This implementation provides a cross-platform local storage solution for storing user authentication data in the Sakksh Asset application.

## Architecture

### Files Created:
1. **Common Module:**
   - `core/storage/LocalStorage.kt` - Main interface and SessionManager
   - `core/storage/LocalStorageProvider.kt` - Expect function for platform-specific instances
   - `core/storage/LocalSessionManager.kt` - Composition local provider

2. **Android Module:**
   - `core/storage/LocalStorage.android.kt` - Android implementation using SharedPreferences
   - `core/storage/LocalStorageProvider.android.kt` - Android-specific provider

3. **iOS Module:**
   - `core/storage/LocalStorage.ios.kt` - iOS implementation using NSUserDefaults
   - `core/storage/LocalStorageProvider.ios.kt` - iOS-specific provider

## Usage

### Storing OTP Verification Response

The OTP verification response is automatically stored when the user successfully verifies their OTP. The following data is saved:

```kotlin
sessionManager.saveSession(
    accessToken = response.accessToken,
    userId = response.userId,
    userEmail = response.userEmail,
    userDetail = userDetailJson // JSON string of UserDetail object
)
```

### Retrieving Stored Data

```kotlin
val sessionManager = SessionManager(getLocalStorage())

// Get access token
val token = sessionManager.getAccessToken()

// Get user ID
val userId = sessionManager.getUserId()

// Get user email
val email = sessionManager.getUserEmail()

// Get user detail (as JSON string)
val userDetailJson = sessionManager.getUserDetail()

// Check if user is logged in
val isLoggedIn = sessionManager.isLoggedIn()
```

### Clearing Session (Logout)

```kotlin
sessionManager.clearSession()
```

## Stored Data Structure

### Storage Keys:
- `access_token` - JWT access token
- `user_id` - User ID (integer)
- `user_email` - User email address
- `user_detail` - Complete user detail object (JSON string)

### UserDetail Object:
```json
{
  "id": 1,
  "email": "user@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "role": 1,
  "created_at": "2025-12-02T17:55:57.389Z",
  "updated_at": "2025-12-02T17:55:57.389Z",
  "isAutoGen": 0,
  "companyid": 1,
  "phone": "+1234567890",
  "status": 1,
  "access_modules": null
}
```

## API Integration

### Updated Models:
- `VerifyOtpRequest` - Now only requires `otp` field
- `VerifyOtpResponse` - Updated to match actual API response with `message`, `userId`, `userEmail`, `userDetail`, and `accessToken`
- `UserDetail` - New model matching the API's user detail structure

### Updated Endpoint:
- Changed from `auth/verify-otp` to `auth/otp-verification`

## Platform-Specific Details

### Android
- Uses `SharedPreferences` with the name "SakkshAssetPrefs"
- Data is stored in app-private storage
- Automatically initialized in `MainActivity.onCreate()`

### iOS
- Uses `NSUserDefaults.standardUserDefaults`
- Data is persisted across app launches
- No initialization required

## Security Considerations

⚠️ **Important:** The current implementation stores sensitive data (access tokens) in plain text in local storage. For production use, consider:

1. Encrypting sensitive data before storage
2. Using Android Keystore / iOS Keychain for token storage
3. Implementing token refresh mechanism
4. Adding token expiration checks

## Next Steps

To use the stored access token in API requests:
1. Retrieve the token using `sessionManager.getAccessToken()`
2. Add it to the Authorization header in your HTTP requests
3. Implement automatic token refresh when expired
4. Add authentication state management throughout the app
