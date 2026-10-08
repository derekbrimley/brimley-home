package home.brimley

import android.app.admin.DeviceAdminReceiver

// Exists so the tablet can be made device owner over adb, which lets
// startLockTask() pin the dashboard without a confirmation prompt.
class DeviceAdmin : DeviceAdminReceiver()
