package com.almaraama.files.domain;

/** Cleaning of the names given by browsers (ADR-038): display only, never part of a path. */
public final class FileNames {

    private FileNames() {
    }

    /**
     * Keeps the last path segment, removes control characters, quotes and separators, and cuts to 120 characters;
     * {@code null} when nothing is left.
     */
    public static String clean(String name) {
        if (name == null) {
            return null;
        }
        String last = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        StringBuilder out = new StringBuilder();
        last.codePoints()
                .filter(c -> !Character.isISOControl(c) && c != '"' && c != '\'' && c != ';' && c != '<' && c != '>')
                .forEach(out::appendCodePoint);
        String cleaned = out.toString().strip();
        if (cleaned.isEmpty() || cleaned.equals(".") || cleaned.equals("..")) {
            return null;
        }
        return cleaned.length() > 120 ? cleaned.substring(0, 120) : cleaned;
    }
}
