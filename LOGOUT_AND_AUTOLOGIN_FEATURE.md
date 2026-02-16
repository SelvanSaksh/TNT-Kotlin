# Logout and Auto-Login Feature Implementation

## Summary

I've successfully implemented both requested features for your Android app:

### 1. ✅ Logout Feature with Confirmation Dialog

**Location:** `composeApp/src/commonMain/kotlin/features/app/Home.kt`

**Changes Made:**
- Added a logout confirmation dialog that appears when the user clicks the logout button
- The dialog shows: "Are you sure you want to logout?"
- Two buttons:
  - **Cancel**: Closes the dialog without logging out
  - **Logout** (in red): Clears all session data and navigates to login screen
- When logout is confirmed, the following data is cleared from local storage:
  - Access token
  - User ID
  - User email
  - User detail

**How It Works:**
1. User clicks the logout icon button in the top-right corner of the Home screen
2. A modern Material Design 3 dialog appears asking for confirmation
3. If user clicks "Logout", the app:
   - Calls `sessionManager.clearSession()` to remove all stored credentials
   - Navigates back to the Login screen
4. If user clicks "Cancel", the dialog simply closes

### 2. ✅ Auto-Login Based on Token

**Location:** `composeApp/src/commonMain/kotlin/com/app/sakkshasset/App.kt`

**Changes Made:**
- Modified the app initialization logic to check for existing authentication tokens
- If a valid token exists in local storage, the app navigates directly to the Home screen
- If no token is found, the app shows the Login screen as usual

**How It Works:**
1. When the app starts, after showing the Initial/Splash screen for 1 second
2. The app checks if `sessionManager.isLoggedIn()` returns true
3. If logged in (token exists):
   - Navigates directly to **Home screen**
   - User doesn't need to login again
4. If not logged in (no token):
   - Navigates to **Login screen**
   - User must authenticate

## Files Modified

1. **Home.kt** - Added logout dialog UI and functionality
2. **App.kt** - Added auto-login check on app startup

## Technical Details

### Session Management
The implementation uses the existing `SessionManager` class which provides:
- `isLoggedIn()`: Checks if access token exists
- `clearSession()`: Removes all stored authentication data
- Storage keys managed: `ACCESS_TOKEN`, `USER_ID`, `USER_EMAIL`, `USER_DETAIL`

### UI/UX
- **Logout Dialog**: Clean Material Design 3 AlertDialog with rounded corners
- **Logout Button**: Red text to indicate a destructive action
- **Cancel Button**: Default text style for safe dismissal

## Testing Checklist

- [ ] Login with valid credentials
- [ ] Close and reopen the app (should go directly to Home, not Login)
- [ ] Click logout button on Home screen
- [ ] Verify confirmation dialog appears with correct message
- [ ] Click "Cancel" - dialog should close, user stays logged in
- [ ] Click "Logout" - user should be redirected to Login screen
- [ ] Try to navigate back - should not be able to return to Home
- [ ] Close and reopen app - should show Login screen (not Home)
- [ ] Login again and verify the flow works correctly

## Next Steps

You can now test the implementation on a physical device or emulator. The features are ready to use!

**To build and run:**
```bash
./gradlew build
# For Android
./gradlew :composeApp:installDebug
```

All changes maintain the existing code style and integrate seamlessly with your current authentication flow.
