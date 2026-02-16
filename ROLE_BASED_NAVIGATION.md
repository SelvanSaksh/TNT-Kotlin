# Role-Based Navigation Feature

## ✅ Implementation Complete

I've successfully implemented **role-based navigation** for auto-login functionality.

## 🎯 How It Works

### On App Startup (with existing token):

1. **App checks if user is logged in** (token exists)
2. **Retrieves user details** from local storage
3. **Checks the user's role**:
   - **Role 4 or 5** → Navigate to **Assets screen**
   - **Other roles** → Navigate to **Home screen**
4. **If no token** → Navigate to **Login screen**

### On OTP Verification (Login Flow):

After successful OTP verification:
- **Role 4 or 5** → Navigate to **Assets screen**
- **Other roles** → Navigate to **Home screen**

## 📝 Changes Made

### File: `App.kt`

**Updated the LaunchedEffect:**
```kotlin
LaunchedEffect(Unit) {
    delay(1000)
    // Check if user is already logged in
    if (sessionManager.isLoggedIn()) {
        // Get user detail and check role
        val userDetailJson = sessionManager.getUserDetail()
        if (userDetailJson != null) {
            try {
                val userDetail = json.decodeFromString<network.models.UserDetail>(userDetailJson)
                val role = userDetail.role
                
                // Navigate based on role
                currentScreen = if (role == 4 || role == 5) {
                    AppScreen.Assets
                } else {
                    AppScreen.Home
                }
            } catch (e: Exception) {
                // If parsing fails, default to Home
                currentScreen = AppScreen.Home
            }
        } else {
            // If no user detail, default to Home
            currentScreen = AppScreen.Home
        }
    } else {
        currentScreen = AppScreen.Login
    }
}
```

**Updated OTP Verification Logic:**
```kotlin
result.onSuccess { response ->
    val userDetailJson = json.encodeToString(response.userDetail)
    
    sessionManager.saveSession(
        accessToken = response.accessToken,
        userId = response.userId,
        userEmail = response.userEmail,
        userDetail = userDetailJson
    )
    
    val role = response.userDetail.role
    
    // Navigate based on role
    currentScreen = if (role == 4 || role == 5) {
        AppScreen.Assets
    } else {
        AppScreen.Home
    }
}
```

## 🔄 User Flow

### Scenario 1: Role 4/5 User
1. Opens app → Initial screen (1 second)
2. Auto-navigates to **Assets screen**
3. User can logout
4. After logout, login again
5. After OTP verification → **Assets screen**

### Scenario 2: Other Role User (e.g., Role 1, 2, 3)
1. Opens app → Initial screen (1 second)
2. Auto-navigates to **Home screen**
3. User can logout
4. After logout, login again
5. After OTP verification → **Home screen**

### Scenario 3: No Token (First Time/After Logout)
1. Opens app → Initial screen (1 second)
2. Shows **Login screen**
3. User enters credentials and OTP
4. Navigates based on role (Assets or Home)

## 🛡️ Error Handling

- If user detail parsing fails → Defaults to Home screen
- If user detail is null → Defaults to Home screen
- Ensures app doesn't crash on invalid data

## ✅ Build Status

**BUILD SUCCESSFUL** ✓

The app compiled successfully with only deprecation warnings (not errors).

## 🧪 Testing Instructions

### Test Role-Based Navigation:

1. **Test with Role 4 or 5 account:**
   ```bash
   # Login with role 4/5 credentials
   # Close and reopen app
   # Should land on Assets screen
   ```

2. **Test with other role account:**
   ```bash
   # Login with role 1/2/3 credentials
   # Close and reopen app
   # Should land on Home screen
   ```

3. **Test logout flow:**
   ```bash
   # Logout from either screen
   # Login again
   # Should navigate to correct screen based on role
   ```

## 📱 Deploy to Device

```bash
# First, check connected devices
adb devices

# Then deploy (replace YOUR_DEVICE_ID with actual device ID)
./gradlew :composeApp:assembleDebug && adb -s YOUR_DEVICE_ID install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk && adb -s YOUR_DEVICE_ID shell am start -n com.app.sakkshasset/.MainActivity
```

---

## 🎉 Summary

✅ Role-based auto-navigation implemented
✅ Works on app startup with existing token
✅ Works after OTP verification
✅ Proper error handling
✅ Build successful
✅ Ready for testing!
