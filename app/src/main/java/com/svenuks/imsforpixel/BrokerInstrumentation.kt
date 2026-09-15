package com.svenuks.imsforpixel
 
import android.app.Instrumentation
import android.content.Context
import android.os.Bundle
import android.os.PersistableBundle
import android.system.Os
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Method
import android.os.Build
import android.app.UiAutomation
import android.content.Intent
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager

 
class BrokerInstrumentation : Instrumentation() {
    companion object {
        private const val TAG = "VoLTEBrokerInst"
    }
 
    private fun findMethod(obj: Any, name: String): Method? {
        var clazz: Class<*>? = obj.javaClass
        while (clazz != null) {
            try {
                val method = clazz.declaredMethods.firstOrNull { it.name == name }
                if (method != null) {
                    method.isAccessible = true
                    return method
                }
            } catch (e: Exception) {
                // Ignore
            }
            clazz = clazz.superclass
        }
        
        // Search interfaces
        for (iface in obj.javaClass.interfaces) {
            try {
                val method = iface.declaredMethods.firstOrNull { it.name == name }
                if (method != null) {
                    method.isAccessible = true
                    return method
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
        return null
    }
 
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        Log.d(TAG, "BrokerInstrumentation starting...")
        
        val queryOnly = arguments?.getString("query_only") == "true" || arguments?.getBoolean("query_only") == true
        Log.d(TAG, "queryOnly mode: $queryOnly")
        
        val clearArg = arguments?.getString("clear") == "true" || arguments?.getBoolean("clear") == true
        Log.d(TAG, "clearArg: $clearArg")

        Thread {
            try {
                HiddenApiBypass.addHiddenApiExemptions("L")
                Log.d(TAG, "Successfully applied HiddenApiBypass exemptions in instrumentation")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply HiddenApiBypass exemptions in instrumentation", e)
            }
            
            try {
                var uiAutomation: UiAutomation? = null
                var retries = 5
                while (retries > 0) {
                    try {
                        // Try to get standard UiAutomation first to avoid flags mismatch and disconnect() call on Android 15/16
                        uiAutomation = getUiAutomation()
                        if (uiAutomation != null) {
                            Log.d(TAG, "Successfully connected UiAutomation")
                            break
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to connect UiAutomation, retries left: ${retries - 1}", e)
                        retries--
                        if (retries > 0) {
                            try { Thread.sleep(500) } catch (ignored: Exception) {}
                        } else {
                            throw e
                        }
                    }
                }

                if (uiAutomation != null) {
                    uiAutomation.adoptShellPermissionIdentity()
                    Log.d(TAG, "Successfully adopted shell permission identity via UiAutomation")
                } else {
                    Log.e(TAG, "UiAutomation is null, cannot adopt shell permission identity")
                }
                
                try {
                    if (queryOnly) {
                        queryStatusOnly()
                    } else {
                        patchAllSimsAndPoll(arguments)
                        showImsStatusNotification(!clearArg)
                    }
                } finally {
                    if (uiAutomation != null) {
                        try {
                            uiAutomation.dropShellPermissionIdentity()
                            Log.d(TAG, "Released shell permission identity via UiAutomation")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to drop shell permission identity", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to run instrumentation patch", e)
            } finally {
                finish(0, Bundle())
            }
        }.start()
    }

    override fun finish(resultCode: Int, results: Bundle?) {
        Log.d(TAG, "Instrumentation finish() called")
        try {
            super.finish(resultCode, results)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Instrumentation.finish() ignored safely", e)
        }
    }

    private fun queryStatusOnly() {
        val subManager = context.getSystemService(SubscriptionManager::class.java) ?: return
        val activeSubscriptions = subManager.activeSubscriptionInfoList ?: emptyList()
        Log.d(TAG, "QueryStatusOnly: Found ${activeSubscriptions.size} active SIMs")

        for (subInfo in activeSubscriptions) {
            val subId = subInfo.subscriptionId
            val slotIndex = subInfo.simSlotIndex
            val isImsRegistered = checkImsRegistered(subId)
            Log.d(TAG, "QueryStatusOnly: SIM slot $slotIndex IMS Registered: $isImsRegistered")
            
            val sharedPrefs = context.getSharedPreferences("volte_settings", Context.MODE_PRIVATE)
            sharedPrefs.edit().putBoolean("ims_registered_slot_$slotIndex", isImsRegistered).commit()
            
            try {
                val statusFile = java.io.File(context.filesDir, "ims_status_$slotIndex.txt")
                statusFile.writeText(isImsRegistered.toString())
                Log.d(TAG, "QueryStatusOnly: Saved status file for slot $slotIndex")
            } catch (e: Exception) {
                Log.e(TAG, "QueryStatusOnly: Failed to save status file", e)
            }
        }
    }

    private fun checkImsRegistered(subId: Int): Boolean {
        return try {
            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = serviceManagerClass.getMethod("getService", String::class.java)
            val binder = getServiceMethod.invoke(null, Context.TELEPHONY_SERVICE) as android.os.IBinder
            val stubClass = Class.forName("com.android.internal.telephony.ITelephony\$Stub")
            val asInterfaceMethod = stubClass.getMethod("asInterface", android.os.IBinder::class.java)
            val telephonyService = asInterfaceMethod.invoke(null, binder)
            
            val iTelephonyClass = Class.forName("com.android.internal.telephony.ITelephony")
            val method = iTelephonyClass.getMethod("isImsRegistered", Int::class.javaPrimitiveType)
            method.isAccessible = true
            method.invoke(telephonyService, subId) as Boolean
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check IMS registration for subId=$subId reflectively", e)
            false
        }
    }

    private fun patchAllSimsAndPoll(arguments: Bundle?) {
        val sharedPrefs = context.getSharedPreferences("volte_settings", Context.MODE_PRIVATE)
        val subManager = context.getSystemService(SubscriptionManager::class.java) ?: return
        val carrierConfigManager = context.getSystemService(CarrierConfigManager::class.java) ?: return
        val telephonyManager = context.getSystemService(TelephonyManager::class.java) ?: return

        // Under shell permission identity, activeSubscriptionInfoList is accessible directly
        val activeSubscriptions = subManager.activeSubscriptionInfoList ?: emptyList()
        Log.d(TAG, "Found ${activeSubscriptions.size} active SIMs")

        val hasClearArg = arguments?.containsKey("clear") == true
        val clearArg = arguments?.getString("clear") == "true" || arguments?.getBoolean("clear") == true

        // Phase 1: Apply overrides or Clear overrides, and trigger IMS reset
        for (subInfo in activeSubscriptions) {
            val subId = subInfo.subscriptionId
            val slotIndex = subInfo.simSlotIndex
            Log.d(TAG, "Processing SIM slot $slotIndex (SubID $subId)")

            val clear = if (hasClearArg) {
                clearArg
            } else {
                sharedPrefs.getBoolean("clear_slot_$slotIndex", false)
            }

            if (clear) {
                Log.d(TAG, "Clearing config for slot $slotIndex")
                try {
                    val appliedFile = java.io.File(context.filesDir, "config_applied_$slotIndex.txt")
                    appliedFile.writeText("false")
                } catch (e: Exception) {}
                try {
                    val overrideMethod = findMethod(carrierConfigManager, "overrideConfig")
                    if (overrideMethod != null) {
                        val paramTypes = overrideMethod.parameterTypes
                        if (paramTypes.size == 3) {
                            overrideMethod.invoke(carrierConfigManager, subId, null, true)
                        } else {
                            overrideMethod.invoke(carrierConfigManager, subId, null)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to clear carrier config reflectively", e)
                }
            } else {
                val volte = sharedPrefs.getBoolean("volte_slot_$slotIndex", true)
                val vonr = sharedPrefs.getBoolean("vonr_slot_$slotIndex", true)
                val vt = sharedPrefs.getBoolean("vt_slot_$slotIndex", true)
                val vowifi = sharedPrefs.getBoolean("vowifi_slot_$slotIndex", true)
                val crossSim = sharedPrefs.getBoolean("cross_sim_slot_$slotIndex", false)
                val wfcRoaming = sharedPrefs.getBoolean("wfc_roaming_slot_$slotIndex", true)
                val ssUt = sharedPrefs.getBoolean("ss_ut_slot_$slotIndex", true)
                val showIms = sharedPrefs.getBoolean("show_ims_slot_$slotIndex", true)
                val allowApn = sharedPrefs.getBoolean("allow_apn_slot_$slotIndex", false)
                val bundle = PersistableBundle()
                // VoLTE enabling & provisioning overrides
                bundle.putBoolean("carrier_volte_available_bool", volte)
                bundle.putBoolean("enhanced_4g_lte_on_by_default_bool", volte)
                bundle.putBoolean("hide_enhanced_4g_lte_bool", !volte)
                bundle.putBoolean("editable_enhanced_4g_lte_bool", volte)
                bundle.putBoolean("carrier_volte_provisioned_bool", volte)
                bundle.putBoolean("carrier_volte_provisioning_required_bool", false)

                // VoNR (5G Calling) overrides
                bundle.putBoolean("vonr_enabled_bool", vonr)
                bundle.putBoolean("vonr_setting_visibility_bool", vonr)

                // Video Calling (VT / ViLTE / ViNR) overrides
                bundle.putBoolean("carrier_vt_available_bool", vt)
                if (vt) {
                    bundle.putBoolean("ignore_data_enabled_changed_for_video_calls", true)
                    bundle.putBoolean("allow_video_calling_fallback_bool", true)
                    bundle.putBoolean("support_pause_ims_video_calls_bool", true)
                    bundle.putIntArray("ims.capability_type_video_int_array", intArrayOf(0, 1, 3))
                }

                // VoWiFi (Wi-Fi Calling) overrides
                bundle.putBoolean("carrier_wfc_ims_available_bool", vowifi)
                bundle.putBoolean("carrier_default_wfc_ims_enabled_bool", vowifi)
                bundle.putBoolean("carrier_wfc_ims_provisioned_bool", vowifi)
                bundle.putBoolean("editable_wfc_mode_bool", vowifi)
                bundle.putBoolean("editable_wfc_roaming_mode_bool", vowifi)
                bundle.putBoolean("carrier_default_wfc_ims_roaming_enabled_bool", wfcRoaming)

                // Other settings
                bundle.putBoolean("carrier_cross_sim_ims_available_bool", crossSim)
                bundle.putBoolean("enable_cross_sim_calling_on_opportunistic_data_bool", crossSim)
                bundle.putBoolean("carrier_supports_ss_over_ut_bool", ssUt)
                bundle.putBoolean("show_ims_registration_status_bool", showIms)
                bundle.putBoolean("allow_adding_apns_bool", allowApn)

                Log.d(TAG, "Applying config for slot $slotIndex: VoLTE=$volte, VoNR=$vonr, VoWiFi=$vowifi")
                
                try {
                    val overrideMethod = findMethod(carrierConfigManager, "overrideConfig")
                    if (overrideMethod != null) {
                        val paramTypes = overrideMethod.parameterTypes
                        if (paramTypes.size == 3) {
                            overrideMethod.invoke(carrierConfigManager, subId, bundle, true)
                        } else {
                            overrideMethod.invoke(carrierConfigManager, subId, bundle)
                        }
                        Log.d(TAG, "Applied config reflectively")
                        try {
                            val appliedFile = java.io.File(context.filesDir, "config_applied_$slotIndex.txt")
                            appliedFile.writeText("true")
                        } catch (e: Exception) {}
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to apply config reflectively", e)
                }
            }

            // Reset IMS registration to force reload
            try {
                val resetImsMethod = findMethod(telephonyManager, "resetIms")
                resetImsMethod?.invoke(telephonyManager, slotIndex)
                Log.d(TAG, "IMS reset sent for slot $slotIndex")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reset IMS reflectively", e)
            }
        }

        // Do not auto-launch MainActivity early, user will see the prompt/notification

        // Phase 2: Poll and update status for up to 30 seconds
        Log.d(TAG, "Entering status polling loop...")
        var pollsLeft = 30
        val totalPolls = 30
        while (pollsLeft > 0) {
            var allRegistered = true
            for (subInfo in activeSubscriptions) {
                val subId = subInfo.subscriptionId
                val slotIndex = subInfo.simSlotIndex
                val isImsRegistered = checkImsRegistered(subId)
                Log.d(TAG, "Poll $pollsLeft: SIM slot $slotIndex IMS Registered: $isImsRegistered")
                
                sharedPrefs.edit().putBoolean("ims_registered_slot_$slotIndex", isImsRegistered).commit()
                try {
                    val statusFile = java.io.File(context.filesDir, "ims_status_$slotIndex.txt")
                    statusFile.writeText(isImsRegistered.toString())
                } catch (e: Exception) {}

                if (!isImsRegistered) {
                    allRegistered = false
                }
            }

            val secondsElapsed = totalPolls - pollsLeft
            if (hasClearArg && clearArg) {
                // When clearing/restoring, wait until it's unregistered
                if (!allRegistered) {
                    Log.d(TAG, "IMS successfully unregistered. Stopping poll.")
                    break
                }
            } else {
                // When applying/activating, wait until it's registered
                // To avoid stale true values right after reset, only exit early after at least 5 seconds
                if (allRegistered && secondsElapsed >= 5) {
                    Log.d(TAG, "IMS successfully registered. Stopping poll.")
                    break
                }
            }

            try {
                Thread.sleep(1000)
            } catch (ignored: Exception) {}
            pollsLeft--
        }
    }

    private fun showImsStatusNotification(isActivate: Boolean) {
        val channelId = "ims_status_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val lang = LanguageManager.getSavedLanguage(context)
        val localizedContext = LanguageManager.applyLocale(context, lang)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, localizedContext.getString(R.string.notification_ims_status_channel), NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(ch)
        }
        
        // Read actual IMS status from files
        val slot0 = try { java.io.File(context.filesDir, "ims_status_0.txt").readText().trim().toBoolean() } catch (e: Exception) { false }
        val slot1 = try { java.io.File(context.filesDir, "ims_status_1.txt").readText().trim().toBoolean() } catch (e: Exception) { false }
        val anyRegistered = slot0 || slot1
        val title = if (isActivate) {
            if (anyRegistered) localizedContext.getString(R.string.notification_activate_success_any) else localizedContext.getString(R.string.notification_activate_success_none)
        } else {
            localizedContext.getString(R.string.notification_restore_success)
        }
        val body = if (isActivate) {
            buildString {
                if (slot0) append(localizedContext.getString(R.string.notification_sim_1_registered) + "  ") else append(localizedContext.getString(R.string.notification_sim_1_unregistered) + "  ")
                if (slot1) append(localizedContext.getString(R.string.notification_sim_2_registered)) else append(localizedContext.getString(R.string.notification_sim_2_unregistered))
            }
        } else {
            localizedContext.getString(R.string.notification_restore_body)
        }
        
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        
        val notification = builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .build()
            
        notificationManager.notify(203, notification)
    }
}
