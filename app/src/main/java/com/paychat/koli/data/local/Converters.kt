package com.paychat.koli.data.local

import androidx.room.TypeConverter
import com.paychat.koli.core.model.MessageType
import com.paychat.koli.core.model.SyncState
import com.paychat.koli.core.model.TxnDirection
import com.paychat.koli.core.model.TxnStatus

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
