package com.soyunomas.horariolectivo;

import java.util.Locale;

final class SubjectCode {
    private SubjectCode() {}
    static String normalize(String raw) { return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT); }
    static boolean isValid(String raw) { return normalize(raw).matches("[A-Z0-9]{1,3}"); }
}
