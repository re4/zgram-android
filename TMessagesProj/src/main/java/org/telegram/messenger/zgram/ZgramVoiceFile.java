package org.telegram.messenger.zgram;

import android.text.TextUtils;

import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;

import java.io.File;

public final class ZgramVoiceFile {

    private ZgramVoiceFile() {
    }

    public static MessageObject prepare(int account, MessageObject source) {
        if (source == null || TextUtils.isEmpty(source.messageOwner.attachPath)) {
            return null;
        }
        TLRPC.Document original = source.getDocument();
        File file = new File(source.messageOwner.attachPath);
        if (original == null || original.id != 0 || !file.isFile() || !file.canRead() || file.length() == 0) {
            return null;
        }
        double duration = 0;
        for (TLRPC.DocumentAttribute attribute : original.attributes) {
            if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                duration = attribute.duration;
                break;
            }
        }
        if (Double.isNaN(duration) || Double.isInfinite(duration) || duration <= 0) {
            return null;
        }

        TLRPC.TL_document document = new TLRPC.TL_document();
        document.file_reference = new byte[0];
        document.mime_type = original.mime_type;
        document.size = file.length();
        document.date = original.date;
        TLRPC.TL_documentAttributeAudio audio = new TLRPC.TL_documentAttributeAudio();
        audio.voice = true;
        audio.duration = duration;
        document.attributes.add(audio);
        TLRPC.TL_documentAttributeFilename name = new TLRPC.TL_documentAttributeFilename();
        name.file_name = file.getName();
        document.attributes.add(name);

        TLRPC.TL_message message = new TLRPC.TL_message();
        message.id = source.messageOwner.id;
        message.out = true;
        message.date = source.messageOwner.date;
        message.peer_id = source.messageOwner.peer_id;
        message.from_id = source.messageOwner.from_id;
        message.message = "";
        message.attachPath = file.getAbsolutePath();
        message.flags = TLRPC.MESSAGE_FLAG_HAS_MEDIA | TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
        message.media = new TLRPC.TL_messageMediaDocument();
        message.media.flags |= 3;
        message.media.document = document;
        return new MessageObject(account, message, false, true);
    }
}
