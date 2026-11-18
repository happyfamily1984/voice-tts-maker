package com.happyfamily.voicetts

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.FileProvider
import com.happyfamily.voicetts.engine.Dsp
import com.happyfamily.voicetts.engine.Wav
import java.io.File

/** 만든 소리를 파일로 내보내고 휴대폰에 적용한다. */
object Exporter {
    enum class Kind(val label: String, val dir: String, val flag: String, val ringType: Int) {
        MUSIC("음악 폴더", Environment.DIRECTORY_MUSIC + "/내목소리TTS", MediaStore.Audio.Media.IS_MUSIC, 0),
        RINGTONE("벨소리", Environment.DIRECTORY_RINGTONES, MediaStore.Audio.Media.IS_RINGTONE, RingtoneManager.TYPE_RINGTONE),
        NOTIFICATION("알림음", Environment.DIRECTORY_NOTIFICATIONS, MediaStore.Audio.Media.IS_NOTIFICATION, RingtoneManager.TYPE_NOTIFICATION),
        ALARM("알람음", Environment.DIRECTORY_ALARMS, MediaStore.Audio.Media.IS_ALARM, RingtoneManager.TYPE_ALARM),
    }

    fun safeName(text: String): String {
        val t = text.trim().replace(Regex("[\\\\/:*?\"<>|\\n\\r]"), " ").trim().take(24)
        return (if (t.isEmpty()) "내목소리" else t)
    }

    /** 기기의 공용 폴더에 WAV로 저장하고 그 주소를 돌려준다. */
    fun saveToDevice(context: Context, audio: FloatArray, sampleRate: Int, name: String, kind: Kind): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "${safeName(name)}.wav")
            put(MediaStore.Audio.Media.TITLE, safeName(name))
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/x-wav")
            put(MediaStore.Audio.Media.RELATIVE_PATH, kind.dir)
            put(kind.flag, 1)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            ?: error("저장할 수 없습니다")
        try {
            resolver.openOutputStream(uri)!!.use { Wav.write(it, Dsp.toPcm(audio), sampleRate) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null); throw e
        }
        return uri
    }

    fun canChangeSettings(context: Context) = Settings.System.canWrite(context)

    fun openWriteSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** 저장한 소리를 기본 벨소리/알림음/알람음으로 지정한다. */
    fun applyAsDefault(context: Context, uri: Uri, kind: Kind) {
        RingtoneManager.setActualDefaultRingtoneUri(context, kind.ringType, uri)
    }

    /** 다른 앱(카카오톡, 메일, 드라이브 등)으로 WAV 파일을 보낸다. */
    fun share(context: Context, audio: FloatArray, sampleRate: Int, name: String) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, "${safeName(name)}.wav")
        Wav.write(f, Dsp.toPcm(audio), sampleRate)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
        val send = Intent(Intent.ACTION_SEND).setType("audio/x-wav")
            .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "소리 파일 보내기").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openTtsSettings(context: Context) {
        val i = Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(i) }.onFailure {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
