package com.sahidcode404.camera.processing.nativebridge

object NativeProcessingBridge {
    init { System.loadLibrary("camera_processing") }
    external fun nativeVersion(): String
    external fun hasNeon(): Boolean
}
