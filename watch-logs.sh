#!/bin/bash

# Clear previous logs
adb logcat -c

echo "=========================================="
echo "Starting logcat for AssetRepository..."
echo "=========================================="
echo ""
echo "Please now:"
echo "1. Open the app on your device"
echo "2. Navigate to the Assets screen"
echo "3. Watch the logs below"
echo ""
echo "=========================================="
echo ""

# Monitor logs
adb logcat | grep -E "(AssetRepository|System.out)"
