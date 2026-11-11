#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <stdarg.h>
#include "opus/include/opus.h"

// Android 4.1.2 Jelly Bean compatibility stubs (libc in Jelly Bean lacks _chk symbols)
void *__memcpy_chk(void *dest, const void *src, size_t len, size_t destlen) {
    return memcpy(dest, src, len);
}

void *__memset_chk(void *dest, int c, size_t len, size_t destlen) {
    return memset(dest, c, len);
}

void *__memmove_chk(void *dest, const void *src, size_t len, size_t destlen) {
    return memmove(dest, src, len);
}

int __vsnprintf_chk(char *s, size_t maxlen, int flag, size_t slen, const char *format, va_list args) {
    return vsnprintf(s, maxlen, format, args);
}

int __snprintf_chk(char *s, size_t maxlen, int flag, size_t slen, const char *format, ...) {
    va_list args;
    va_start(args, format);
    int res = vsnprintf(s, maxlen, format, args);
    va_end(args);
    return res;
}

JNIEXPORT jlong JNICALL
Java_com_example_receiver_codec_NativeOpus_createDecoder(
        JNIEnv *env,
        jobject thiz,
        jint sample_rate,
        jint channels) {
    int error = 0;
    OpusDecoder *decoder = opus_decoder_create(sample_rate, channels, &error);
    if (error != OPUS_OK || decoder == NULL) {
        return 0;
    }
    return (jlong) decoder;
}

JNIEXPORT jint JNICALL
Java_com_example_receiver_codec_NativeOpus_decode(
        JNIEnv *env,
        jobject thiz,
        jlong decoder_ptr,
        jbyteArray in_data,
        jint in_offset,
        jint in_len,
        jshortArray out_pcm,
        jint out_offset,
        jint frame_size,
        jboolean decode_fec) {

    OpusDecoder *decoder = (OpusDecoder *) decoder_ptr;
    if (decoder == NULL) {
        return -1;
    }

    // Buffer output lokal di stack (hingga 5760 samples untuk 48kHz stereo 60ms)
    opus_int16 pcm_stack_buffer[5760];
    int decoded_samples = 0;

    if (in_data == NULL || in_len <= 0) {
        // Mode PLC (Packet Loss Concealment): pass NULL ke opus_decode
        decoded_samples = opus_decode(
                decoder,
                NULL,
                0,
                pcm_stack_buffer,
                frame_size,
                decode_fec ? 1 : 0
        );
    } else {
        unsigned char in_stack_buffer[2048];
        if (in_len <= (jint) sizeof(in_stack_buffer)) {
            (*env)->GetByteArrayRegion(env, in_data, in_offset, in_len, (jbyte *) in_stack_buffer);
            decoded_samples = opus_decode(
                    decoder,
                    in_stack_buffer,
                    in_len,
                    pcm_stack_buffer,
                    frame_size,
                    decode_fec ? 1 : 0
            );
        } else {
            jbyte *bytes = (*env)->GetByteArrayElements(env, in_data, NULL);
            if (bytes == NULL) {
                return -2;
            }
            decoded_samples = opus_decode(
                    decoder,
                    (const unsigned char *) (bytes + in_offset),
                    in_len,
                    pcm_stack_buffer,
                    frame_size,
                    decode_fec ? 1 : 0
            );
            (*env)->ReleaseByteArrayElements(env, in_data, bytes, JNI_ABORT);
        }
    }

    if (decoded_samples > 0) {
        // Jumlah sampel total untuk stereo = decoded_samples * 2
        int total_shorts = decoded_samples * 2;
        if (total_shorts > 5760) total_shorts = 5760;
        (*env)->SetShortArrayRegion(env, out_pcm, out_offset, total_shorts, pcm_stack_buffer);
    }

    return decoded_samples;
}

JNIEXPORT void JNICALL
Java_com_example_receiver_codec_NativeOpus_resetDecoder(
        JNIEnv *env,
        jobject thiz,
        jlong decoder_ptr) {
    OpusDecoder *decoder = (OpusDecoder *) decoder_ptr;
    if (decoder != NULL) {
        opus_decoder_ctl(decoder, OPUS_RESET_STATE);
    }
}

JNIEXPORT void JNICALL
Java_com_example_receiver_codec_NativeOpus_destroyDecoder(
        JNIEnv *env,
        jobject thiz,
        jlong decoder_ptr) {
    OpusDecoder *decoder = (OpusDecoder *) decoder_ptr;
    if (decoder != NULL) {
        opus_decoder_destroy(decoder);
    }
}


JNIEXPORT jlong JNICALL
Java_com_example_musik_codec_NativeOpus_createDecoder(
        JNIEnv *env, jobject thiz, jint sr, jint ch) {
    return Java_com_example_receiver_codec_NativeOpus_createDecoder(env, thiz, sr, ch);
}

JNIEXPORT jint JNICALL
Java_com_example_musik_codec_NativeOpus_decode(
        JNIEnv *env, jobject thiz, jlong ptr, jbyteArray in_data, jint in_off, jint in_len, jshortArray out_pcm, jint out_off, jint frame_size, jboolean fec) {
    return Java_com_example_receiver_codec_NativeOpus_decode(env, thiz, ptr, in_data, in_off, in_len, out_pcm, out_off, frame_size, fec);
}

JNIEXPORT void JNICALL
Java_com_example_musik_codec_NativeOpus_resetDecoder(
        JNIEnv *env, jobject thiz, jlong ptr) {
    Java_com_example_receiver_codec_NativeOpus_resetDecoder(env, thiz, ptr);
}

JNIEXPORT void JNICALL
Java_com_example_musik_codec_NativeOpus_destroyDecoder(
        JNIEnv *env, jobject thiz, jlong ptr) {
    Java_com_example_receiver_codec_NativeOpus_destroyDecoder(env, thiz, ptr);
}

JNIEXPORT jlong JNICALL
Java_com_example_amplifierpro_codec_NativeOpusEncoder_createEncoder(
        JNIEnv *env,
        jobject thiz,
        jint sample_rate,
        jint channels,
        jint application) {
    int error = 0;
    OpusEncoder *encoder = opus_encoder_create(sample_rate, channels, application, &error);
    if (error != OPUS_OK || encoder == NULL) {
        return 0;
    }
    return (jlong) encoder;
}

JNIEXPORT void JNICALL
Java_com_example_amplifierpro_codec_NativeOpusEncoder_configureEncoder(
        JNIEnv *env,
        jobject thiz,
        jlong encoder_ptr,
        jint bitrate,
        jint complexity,
        jboolean signal_music,
        jboolean use_inband_fec,
        jint packet_loss_perc) {
    OpusEncoder *encoder = (OpusEncoder *) encoder_ptr;
    if (encoder == NULL) return;

    opus_encoder_ctl(encoder, OPUS_SET_BITRATE(bitrate));
    opus_encoder_ctl(encoder, OPUS_SET_COMPLEXITY(complexity));
    opus_encoder_ctl(encoder, OPUS_SET_SIGNAL(signal_music ? OPUS_SIGNAL_MUSIC : OPUS_SIGNAL_VOICE));
    opus_encoder_ctl(encoder, OPUS_SET_VBR(1));
    opus_encoder_ctl(encoder, OPUS_SET_VBR_CONSTRAINT(1));
    opus_encoder_ctl(encoder, OPUS_SET_INBAND_FEC(use_inband_fec ? 1 : 0));
    opus_encoder_ctl(encoder, OPUS_SET_PACKET_LOSS_PERC(packet_loss_perc));
}

JNIEXPORT jint JNICALL
Java_com_example_amplifierpro_codec_NativeOpusEncoder_encode(
        JNIEnv *env,
        jobject thiz,
        jlong encoder_ptr,
        jshortArray in_pcm,
        jint in_offset,
        jint frame_size,
        jbyteArray out_data,
        jint max_data_bytes) {
    OpusEncoder *encoder = (OpusEncoder *) encoder_ptr;
    if (encoder == NULL || in_pcm == NULL || out_data == NULL) {
        return -1;
    }

    int total_shorts = frame_size * 2;
    if (total_shorts <= 0 || total_shorts > 5760) {
        return -2;
    }

    opus_int16 pcm_in_stack[5760];
    unsigned char out_stack[1500];
    int max_bytes = max_data_bytes > 1500 ? 1500 : max_data_bytes;

    (*env)->GetShortArrayRegion(env, in_pcm, in_offset, total_shorts, pcm_in_stack);

    int encoded_bytes = opus_encode(
            encoder,
            pcm_in_stack,
            frame_size,
            out_stack,
            max_bytes
    );

    if (encoded_bytes > 0) {
        (*env)->SetByteArrayRegion(env, out_data, 0, encoded_bytes, (const jbyte *) out_stack);
    }
    return encoded_bytes;
}

JNIEXPORT void JNICALL
Java_com_example_amplifierpro_codec_NativeOpusEncoder_resetEncoder(
        JNIEnv *env,
        jobject thiz,
        jlong encoder_ptr) {
    OpusEncoder *encoder = (OpusEncoder *) encoder_ptr;
    if (encoder != NULL) {
        opus_encoder_ctl(encoder, OPUS_RESET_STATE);
    }
}

JNIEXPORT void JNICALL
Java_com_example_amplifierpro_codec_NativeOpusEncoder_destroyEncoder(
        JNIEnv *env,
        jobject thiz,
        jlong encoder_ptr) {
    OpusEncoder *encoder = (OpusEncoder *) encoder_ptr;
    if (encoder != NULL) {
        opus_encoder_destroy(encoder);
    }
}
