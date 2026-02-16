# Debugging Assets API JSON Error

## 🔍 Issue
Getting error: **"Illegal Input: unexpected JSON token at offset 0"**

This error means the API is returning something that's not valid JSON.

---

## ✅ What I Did

### 1. **Added Detailed Logging**
The AssetRepository now logs:
- ✅ Company ID being requested
- ✅ Whether authentication token exists
- ✅ HTTP response status code
- ✅ Raw response body (to see what's actually being returned)
- ✅ Detailed error information

### 2. **Enhanced Error Handling**
- Catches and logs the exact error type
- Prints stack trace for debugging
- Shows raw response before attempting to parse

---

## 🚀 How to Debug

### **Step 1: Deploy the Updated App**
```bash
./gradlew :composeApp:assembleDebug && adb -s YOUR_DEVICE_ID install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk && adb -s YOUR_DEVICE_ID shell am start -n com.app.sakkshasset/.MainActivity
```

### **Step 2: View Logs**
Open a new terminal and run:
```bash
adb logcat | grep "AssetRepository"
```

### **Step 3: Navigate to Assets Screen**
- Login with a role 4 or 5 account
- The app will automatically navigate to Assets
- Watch the logs in the terminal

### **Step 4: Check the Logs**
You should see logs like:
```
AssetRepository: Fetching assets for company 4
AssetRepository: Token exists: true
AssetRepository: Response status: 200 OK
AssetRepository: Raw response body: [{"id":15,"name":"Cold Storage..."}]
AssetRepository: Successfully parsed 4 assets
```

---

## 🔍 Common Issues & Solutions

### **Issue 1: Response status is not 200**
**Logs show:**
```
AssetRepository: Response status: 401 Unauthorized
```

**Solution:**
- The token might be invalid or expired
- Try logging out and logging in again

---

### **Issue 2: Response is HTML instead of JSON**
**Logs show:**
```
AssetRepository: Raw response body: <!DOCTYPE html>...
```

**Solution:**
- The API endpoint might be wrong
- The server might be returning an error page
- Check if the base URL is correct in `Config.kt`

---

### **Issue 3: Response is empty**
**Logs show:**
```
AssetRepository: Raw response body: 
```

**Solution:**
- The company might have no assets
- The API might not be returning data for this company ID

---

### **Issue 4: Token is null**
**Logs show:**
```
AssetRepository: Token exists: false
```

**Solution:**
- User might not be logged in properly
- Session data might be corrupted
- Try logging out and logging in again

---

## 🛠️ Quick Fixes to Try

### **Fix 1: Clear App Data**
```bash
adb shell pm clear com.app.sakkshasset
```
Then login again fresh.

### **Fix 2: Check API Endpoint**
The endpoint being called is:
```
https://api.tnt.sakksh.com/assets/getAssetByComp/{companyId}
```

Try accessing it directly in a browser or Postman with the Bearer token.

### **Fix 3: Verify Company ID**
The app gets the company ID from the user's profile. Make sure:
- User has a valid `companyid` field
- The company ID exists in the backend
- The company has assets

---

## 📊 What the Logs Tell You

### **Good Response (Success):**
```
AssetRepository: Fetching assets for company 4
AssetRepository: Token exists: true
AssetRepository: Response status: 200 OK
AssetRepository: Raw response body: [{"id":15,...}]
AssetRepository: Successfully parsed 4 assets
```
✅ Everything is working!

### **Bad Response (Error):**
```
AssetRepository: Fetching assets for company 4
AssetRepository: Token exists: true
AssetRepository: Response status: 500 Internal Server Error
AssetRepository: Raw response body: {"error":"Something went wrong"}
AssetRepository Error: Unexpected JSON token at offset 0
```
❌ Server error or wrong response format

---

## 🎯 Next Steps

1. **Deploy the updated app** with enhanced logging
2. **Run logcat** to see what's happening
3. **Navigate to Assets** screen
4. **Check the logs** and share them with me

Once we see the actual response body, I can fix the issue immediately!

---

## 📝 Command Reference

### Deploy App:
```bash
./gradlew :composeApp:assembleDebug && adb -s YOUR_DEVICE_ID install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

### View Logs:
```bash
adb logcat | grep "AssetRepository"
```

### Clear Logcat:
```bash
adb logcat -c
```

### Get Device ID:
```bash
adb devices
```

---

## 💡 Tip

If you can share the logcat output showing:
- Response status
- Raw response body
- Error message

I can provide an immediate fix! 🚀
