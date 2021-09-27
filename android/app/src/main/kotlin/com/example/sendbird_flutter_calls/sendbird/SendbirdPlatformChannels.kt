package com.example.sendbird_flutter_calls.sendbird

import android.content.Context
import com.sendbird.calls.*
import com.sendbird.calls.handler.*
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel
import java.util.*

class SendbirdPlatformChannels {

    companion object {
        private const val METHOD_CHANNEL_NAME = "com.sendbird.calls/method"
        private const val ERROR_CODE = "Sendbird Calls"
        private var methodChannel: MethodChannel? = null
        private var directCall: DirectCall? = null

        fun setupChannels(context: Context, messenger: BinaryMessenger) {
            methodChannel = MethodChannel(messenger, METHOD_CHANNEL_NAME)
            methodChannel!!.setMethodCallHandler { call, result ->
                when(call.method) {
                    "init" -> {
                        val appId: String? = call.argument("app_id")
                        val userId: String? = call.argument("user_id")
                        when {
                            appId == null -> {
                                result.error(ERROR_CODE, "Failed Init", "Missing app_id")
                            }
                            userId == null -> {
                                result.error(ERROR_CODE, "Failed Init", "Missing user_id")
                            }
                            else -> {
                                initSendbird(context, appId!!, userId!!) { successful ->
                                    if (!successful) {
                                        result.error(ERROR_CODE, "Failed init", "Problem initializing Sendbird. Check for valid app_id")
                                    } else {
                                        result.success(true)
                                    }
                                }
                            }
                        }
                    }
                    "start_direct_call" -> {
                        val calleeId: String? = call.argument("callee_id")
                        if (calleeId == null) {
                            result.error(ERROR_CODE, "Failed call", "Missing callee_id")
                        }
                        var params = DialParams(calleeId!!)
                        params.setCallOptions(CallOptions())
                        directCall = SendBirdCall.dial(params, object : DialHandler {
                            override fun onResult(call: DirectCall?, e: SendBirdException?) {
                                if (e != null) {
                                    result.error(ERROR_CODE, "Failed call", e.message)
                                    return
                                }
                                result.success(true)
                            }
                        })
                        directCall?.setListener(object : DirectCallListener() {
                            override fun onEstablished(call: DirectCall) {}
                            override fun onConnected(call: DirectCall) {}
                            override fun onEnded(call: DirectCall) {}
                        })
                    }
                    "answer_direct_call"->{
                        directCall?.accept(AcceptParams())
                        result.success(true)
                    }
                    "end_direct_call" -> {
                        directCall?.end();
                        result.success(true)
                    }
                    else -> {
                        result.notImplemented()
                    }
                }
            }
        }

        private fun initSendbird(context: Context, appId: String, userId: String, callback: (Boolean)->Unit){
            // Initialize SendBirdCall instance to use APIs in your app.
            if(SendBirdCall.init(context, appId)){
                // Initialization successful

                SendBirdCall.registerPushToken("AAAAlZmkNq8:APA91bGDF2KKMjGaUBn1785XHTpXP5fequWw9QQNRurQY3QNmpTE2uOiqQvKuB9anPXD9EAB6mL3Anj3oUBww6pE_cQiXTW5ZEyLo3LuicCZTpslqLNSgh9lzr_Ka_EEjGpXf5yVgvlw", true, object :
                    CompletionHandler {
                    override fun onResult(e: SendBirdException?) {
                        if (e == null) {
                            // The push token is registered successfully.
                        }
                    }
                })

                // Add event listeners
                SendBirdCall.addListener(UUID.randomUUID().toString(), object: SendBirdCallListener() {
                    override fun onRinging(call: DirectCall) {

                        // Assign so we we can accept or decline from Flutter side
                        directCall = call

                        // Let Flutter side know we're receiving a call
                        methodChannel?.invokeMethod("direct_call_received"){
                        }

                        val ongoingCallCount = SendBirdCall.ongoingCallCount
                        if (ongoingCallCount >= 2) {
                            call.end()
                            return
                        }

                        call.setListener(object : DirectCallListener() {
                            override fun onEstablished(call: DirectCall) {}

                            override fun onConnected(call: DirectCall) {
                                methodChannel?.invokeMethod("direct_call_connected"){
                                }
                            }

                            override fun onEnded(call: DirectCall) {
                                val ongoingCallCount = SendBirdCall.ongoingCallCount
                                if (ongoingCallCount == 0) {
//                                CallService.stopService(context)
                                }
                                methodChannel?.invokeMethod("direct_call_ended"){
                                }
                            }
                            override fun onRemoteAudioSettingsChanged(call: DirectCall) {}

                        })
                    }
                })
            }

            // The USER_ID below should be unique to your Sendbird application.
            var params = AuthenticateParams(userId)

            SendBirdCall.authenticate(params, object : AuthenticateHandler {
                override fun onResult(user: User?, e: SendBirdException?) {
                    if (e == null) {
                        // The user has been authenticated successfully and is connected to Sendbird server.
                        callback(true)
                    } else {
                        callback(false)
                    }
                }
            })
        }

        fun disposeChannels(){
            methodChannel!!.setMethodCallHandler(null)
        }
    }
}