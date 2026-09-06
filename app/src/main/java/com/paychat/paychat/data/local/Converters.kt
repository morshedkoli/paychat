package com.paychat.paychat.data.local

import androidx.room.TypeConverter
import com.paychat.paychat.core.model.MessageType
import com.paychat.paychat.core.model.SyncState
import com.paychat.paychat.core.model.TxnDirection
import com.paychat.paychat.core.model.TxnStatus

class Converters {
    @TypeConverter fun toDirection(v: String) = TxnDirection.valueOf(v)
    @TypeConverter fun fromDirection(v: TxnDirection) = v.name

    @TypeConverter fun toStatus(v: String) = TxnStatus.valueOf(v)
    @TypeConverter fun fromStatus(v: TxnStatus) = v.name

    @TypeConverter fun toMessageType(v: String) = MessageType.valueOf(v)
    @TypeConverter fun fromMessageType(v: MessageType) = v.name

    @TypeConverter fun toSyncState(v: String) = SyncState.valueOf(v)
    @TypeConverter fun fromSyncState(v: SyncState) = v.name
}
