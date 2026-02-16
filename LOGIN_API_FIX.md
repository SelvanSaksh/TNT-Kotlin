# LoginScreen API Integration Update

## Changes Made

### ✅ Updated: `LoginScreen.kt`

**What Changed:**
- **API call now happens directly in LoginScreen** instead of in App.kt
- Added `AuthRepository.sendOtp()` call inside the Continue button's `onClick` handler
- Added proper error handling and loading states
- Added Snackbar for user feedback

**Key Features:**
1. **Direct API Call**: When user clicks "Continue", the screen calls `AuthRepository.sendOtp()`
2. **Loading State**: Button shows "Please wait..." and is disabled during API call
3. **Error Handling**: 
   - Network errors shown in Snackbar
   - API errors shown below the input field
4. **Success Flow**: On successful OTP send, navigates to OTP screen with success message

**Code Flow:**
```kotlin
onClick = {
    // 1. Validate input
    if (userInput.isBlank()) {
        inputError = "Please enter Email or Phone"
        return
    }

    // 2. Set loading state
    isLoading = true
    inputError = null
    
    // 3. Call API
    scope.launch {
        val result = AuthRepository.sendOtp(userInput.trim())
        isLoading = false
        
        result.onSuccess { response ->
            if (response.success) {
                // Navigate to OTP screen
                snackbarHostState.showSnackbar("OTP sent successfully!")
                onNavigateToOtp(userInput.trim())
            } else {
                // Show API error
                inputError = response.message ?: "Failed to send OTP"
            }
        }.onFailure { error ->
            // Show network error
            inputError = error.message ?: "Network error"
            snackbarHostState.showSnackbar(error.message)
        }
    }
}
```

### ✅ Updated: `App.kt`

**What Changed:**
- Simplified the LoginScreen callback from `onContinueClick` to `onNavigateToOtp`
- Removed duplicate API call logic (now handled in LoginScreen)
- Just handles navigation when LoginScreen succeeds

**Before:**
```kotlin
AppScreen.Login -> LoginScreen(
    onContinueClick = { identifier ->
        // Had to handle API call here
        scope.launch {
            val result = AuthRepository.sendOtp(identifier)
            // ... handle response
        }
    }
)
```

**After:**
```kotlin
AppScreen.Login -> LoginScreen(
    onNavigateToOtp = { identifier ->
        userIdentifier = identifier
        currentScreen = AppScreen.Otp
    }
)
```

## Benefits

1. ✅ **Better Separation of Concerns**: LoginScreen handles its own API logic
2. ✅ **Reusability**: LoginScreen can be used independently with its own error handling
3. ✅ **Better UX**: Immediate feedback with Snackbar and error messages
4. ✅ **Cleaner Code**: App.kt is simpler and just handles navigation

## Testing

The app has been rebuilt and installed on your device. You can now:

1. Open the app on your Motorola Edge 50 Pro
2. Enter an email or phone number
3. Click "Continue"
4. Watch the API call happen (visible in logcat)
5. See success message and navigate to OTP screen

## API Endpoint Called

**POST** `https://api.tnt.sakksh.com/auth/send-otp`

**Request:**
```json
{
  "identifier": "user@example.com"
}
```

**Expected Response:**
```json
{
  "success": true,
  "message": "OTP sent successfully",
  "data": {
    "otpSent": true,
    "expiresIn": 300
  }
}
```

## Monitoring

Your logcat is already running and filtering for API calls:
```bash
adb logcat | grep -E "(API|STATUS|BODY)"
```

You should see output like:
```
API → https://api.tnt.sakksh.com/auth/send-otp
STATUS → 200 OK
BODY → {"success":true,"message":"OTP sent successfully",...}
```
