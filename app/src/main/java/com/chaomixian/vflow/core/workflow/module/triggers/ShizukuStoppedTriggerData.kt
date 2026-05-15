package com.chaomixian.vflow.core.workflow.module.triggers

import android.os.Parcel
import android.os.Parcelable

data class ShizukuStoppedTriggerData(
    val reason: String,
    val checkedAt: Long
) : Parcelable {
    constructor(parcel: Parcel) : this(
        reason = parcel.readString() ?: REASON_UNKNOWN,
        checkedAt = parcel.readLong()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(reason)
        parcel.writeLong(checkedAt)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<ShizukuStoppedTriggerData> {
        const val REASON_BINDER_DEAD = "binder_dead"
        const val REASON_UNAVAILABLE = "unavailable"
        const val REASON_UNKNOWN = "unknown"

        override fun createFromParcel(parcel: Parcel): ShizukuStoppedTriggerData =
            ShizukuStoppedTriggerData(parcel)

        override fun newArray(size: Int): Array<ShizukuStoppedTriggerData?> = arrayOfNulls(size)
    }
}
