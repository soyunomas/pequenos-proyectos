package com.soyunomas.foliopdf;

import android.content.Context;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.database.Cursor;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.io.MemoryUsageSetting;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm;
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility;
import java.io.*;
import java.util.*;

final class PdfTools {
    private PdfTools() {}
    static String name(Context c, Uri uri) {
        try (Cursor cursor = c.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && cursor.getString(0) != null) return cursor.getString(0);
        } catch (Exception ignored) {}
        return "documento.pdf";
    }
    static String stem(String name) {
        return name.replaceFirst("(?i)\\.pdf$", "");
    }
    static File local(Context c, Uri uri) throws IOException {
        File f = File.createTempFile("folio-source-", ".pdf", c.getCacheDir());
        try (InputStream in = c.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(f)) {
            if (in == null) throw new IOException("No se pudo leer el archivo");
            transfer(in, out);
            return f;
        } catch (IOException | RuntimeException e) { f.delete(); throw e; }
    }
    static void transfer(InputStream in, OutputStream out) throws IOException {
        byte[] b = new byte[65536]; int n;
        while ((n = in.read(b)) != -1) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
            out.write(b, 0, n);
        }
    }
    static void publish(Context c, File f, Uri dest) throws IOException {
        try (InputStream in = new FileInputStream(f); OutputStream out = c.getContentResolver().openOutputStream(dest, "wt")) {
            if (out == null) throw new IOException("No se pudo abrir el destino");
            transfer(in, out);
        }
    }
    static PDDocument load(Context c, File f) throws IOException {
        PDFBoxResourceLoader.init(c.getApplicationContext());
        return PDDocument.load(f, MemoryUsageSetting.setupMixed(32L * 1024 * 1024));
    }
    static void checkEditable(PDDocument doc) throws IOException {
        if (doc.isEncrypted()) throw new IOException("El PDF está protegido. No se modificará.");
        if (!doc.getSignatureDictionaries().isEmpty()) throw new IOException("El PDF está firmado. No se modificará.");
        PDAcroForm form = doc.getDocumentCatalog().getAcroForm();
        if (form != null && (!form.getFields().isEmpty() || form.hasXFA()))
            throw new IOException("Este PDF contiene formularios. Para proteger sus campos no se pueden reorganizar sus páginas.");
        if (!doc.getCurrentAccessPermission().canAssembleDocument())
            throw new IOException("El PDF no permite reorganizar páginas.");
    }
    static File pages(Context c, File source, List<Integer> indices, Map<Integer,Integer> rotations) throws IOException {
        File f = File.createTempFile("folio-export-", ".pdf", c.getCacheDir());
        boolean done = false;
        try (PDDocument input = load(c, source); PDDocument output = new PDDocument(MemoryUsageSetting.setupMixed(32L * 1024 * 1024))) {
            checkEditable(input);
            if (indices.isEmpty()) throw new IOException("Selecciona al menos una página");
            for (int index : indices) {
                PDPage page = input.getPage(index);
                PDPage copy = output.importPage(page);
                if (page.getResources() != null) copy.setResources(page.getResources());
                copy.setRotation((page.getRotation() + rotations.getOrDefault(index, 0)) % 360);
            }
            output.save(f);
            done = true;
            return f;
        } finally { if (!done) f.delete(); }
    }
    static File merge(Context c, List<Uri> uris) throws IOException {
        File output = File.createTempFile("folio-merge-", ".pdf", c.getCacheDir());
        List<File> files = new ArrayList<>();
        List<PDDocument> docs = new ArrayList<>();
        boolean done = false;
        try (PDDocument merged = new PDDocument(MemoryUsageSetting.setupMixed(32L * 1024 * 1024))) {
            PDFMergerUtility util = new PDFMergerUtility();
            for (Uri uri : uris) {
                File f = local(c, uri); files.add(f);
                PDDocument d = load(c, f); docs.add(d);
                checkEditable(d);
                util.appendDocument(merged, d);
            }
            merged.save(output);
            done = true;
            return output;
        } finally {
            for (PDDocument doc : docs) try { doc.close(); } catch (IOException ignored) {}
            for (File f : files) f.delete();
            if (!done) output.delete();
        }
    }
}
