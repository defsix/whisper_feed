package com.saulhdev.feeder.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * How many photos may be decoded at the same time.
 *
 * Coil decodes up to four at once. On a current phone that is fine. On the
 * Galaxy Tab S5e (Android 11, two fast cores and six slow ones) a report
 * showed 18% of frames slow while photos were decoding and under 1% while
 * none were, with single WebP decodes taking 100-240ms. Four decodes at once
 * leave the screen's own thread competing for the two fast cores.
 *
 * So an older device decodes two at a time. Photos arrive a little later on a
 * fast fling; scrolling itself stays smoother. Anything newer keeps Coil's
 * default, so the phone this was all tuned on behaves exactly as before.
 */
const val IMAGE_DECODES_DEFAULT = 4

/** The limit on an older or low-memory device. */
const val IMAGE_DECODES_OLDER_DEVICE = 2

/** Android 11 and below counts as older: the version the slow tablet runs. */
const val OLDER_DEVICE_MAX_SDK = Build.VERSION_CODES.R

fun imageDecodeLimit(sdk: Int, lowRam: Boolean): Int =
    if (sdk <= OLDER_DEVICE_MAX_SDK || lowRam) IMAGE_DECODES_OLDER_DEVICE else IMAGE_DECODES_DEFAULT

fun imageDecodeLimit(context: Context): Int = imageDecodeLimit(
    sdk = Build.VERSION.SDK_INT,
    lowRam = context.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true,
)

/** The diagnostics line, so a report says which limit was in force. */
fun imageDecodeSummary(limit: Int): String =
    if (limit < IMAGE_DECODES_DEFAULT) "$limit at once (older device)" else "$limit at once"
