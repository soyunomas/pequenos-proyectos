package com.soyunomas.pdflimpio;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.pdf.PdfWriteHandle;

import java.util.concurrent.atomic.AtomicBoolean;

import kotlin.ResultKt;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlin.coroutines.intrinsics.IntrinsicsKt;

/** Bridges PdfWriteHandle's Kotlin suspend API into a callback safe to use from Java. */
final class PdfWriteBridge {
    interface Callback {
        void onComplete(Throwable error);
    }

    private PdfWriteBridge() {
    }

    static void write(@NonNull ContentResolver resolver,
                      @NonNull Uri destination,
                      @NonNull PdfWriteHandle handle,
                      @NonNull Callback callback) {
        final ParcelFileDescriptor descriptor;
        try {
            descriptor = resolver.openFileDescriptor(destination, "rwt");
            if (descriptor == null) {
                finish(null, handle, callback,
                        new IllegalStateException("No se pudo abrir el destino de guardado"),
                        new AtomicBoolean(false));
                return;
            }
        } catch (Throwable error) {
            finish(null, handle, callback, error, new AtomicBoolean(false));
            return;
        }

        AtomicBoolean completed = new AtomicBoolean(false);
        Continuation<Unit> continuation = new Continuation<Unit>() {
            @NonNull
            @Override
            public CoroutineContext getContext() {
                return EmptyCoroutineContext.INSTANCE;
            }

            @Override
            public void resumeWith(@NonNull Object result) {
                finish(descriptor, handle, callback, failureFromResult(result), completed);
            }
        };

        try {
            Object result = handle.writeTo(descriptor, continuation);
            if (result != IntrinsicsKt.getCOROUTINE_SUSPENDED()) {
                finish(descriptor, handle, callback, failureFromResult(result), completed);
            }
        } catch (Throwable error) {
            finish(descriptor, handle, callback, error, completed);
        }
    }

    private static Throwable failureFromResult(Object result) {
        try {
            ResultKt.throwOnFailure(result);
            return null;
        } catch (Throwable error) {
            return error;
        }
    }

    private static void finish(ParcelFileDescriptor descriptor,
                               PdfWriteHandle handle,
                               Callback callback,
                               Throwable error,
                               AtomicBoolean completed) {
        if (!completed.compareAndSet(false, true)) return;

        Throwable finalError = error;
        if (descriptor != null) {
            try {
                descriptor.close();
            } catch (Throwable closeError) {
                if (finalError == null) finalError = closeError;
            }
        }
        try {
            handle.close();
        } catch (Throwable closeError) {
            if (finalError == null) finalError = closeError;
        }
        callback.onComplete(finalError);
    }
}
