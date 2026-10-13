/*
 * DOCUMENTACIÓN DEL ARCHIVO
 * Centraliza los intents de selección y la lectura de imágenes mediante URI.
 */
package com.example.evogmd.leerimagen;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.IOException;
import java.io.InputStream;

/** Selector y lector de imágenes mediante Storage Access Framework. */
public final class CargarImagen {

    /**
     * Impide crear instancias de esta clase de utilidades.
     */
    private CargarImagen() {
    }

    /**
     * Crea el intent del selector de documentos para elegir una imagen.
     * 
     * @return resultado de la operación descrita.
     */
    public static Intent crearIntentSeleccionImagen() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        return intent;
    }

    /**
     * Decodifica una imagen accesible mediante ContentResolver y comprueba que sea válida.
     * 
     * @param contexto contexto Android para acceder a recursos y almacenamiento privado.
     * @param uri URI del documento que se consulta.
     * @return bitmap resultante de la conversión o consulta.
     * @throws IOException si no puede completarse la lectura, validación o procesamiento solicitado.
     */
    public static Bitmap abrirImagen(Context contexto, Uri uri)
            throws IOException {
        if (uri == null) {
            throw new IllegalArgumentException("No se seleccionó una imagen.");
        }
        ContentResolver resolver = contexto.getContentResolver();
        try (InputStream entrada = resolver.openInputStream(uri)) {
            if (entrada == null) {
                throw new IOException("Android no pudo abrir el documento.");
            }
            Bitmap bitmap = BitmapFactory.decodeStream(entrada);
            if (bitmap == null) {
                throw new IOException("El documento no contiene una imagen válida.");
            }
            return bitmap;
        }
    }

    /**
     * Obtiene el nombre visible del documento identificado por la URI.
     * 
     * @param contexto contexto Android para acceder a recursos y almacenamiento privado.
     * @param uri URI del documento que se consulta.
     * @return texto resultante de la operación descrita.
     */
    public static String obtenerNombre(Context contexto, Uri uri) {
        if (uri == null) {
            return "imagen";
        }
        try (Cursor cursor = contexto.getContentResolver().query(
                uri,
                new String[]{OpenableColumns.DISPLAY_NAME},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int indice = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (indice >= 0) {
                    return cursor.getString(indice);
                }
            }
        } catch (RuntimeException ignorada) {
            // Algunos proveedores no exponen DISPLAY_NAME.
        }
        String ultimo = uri.getLastPathSegment();
        return ultimo == null ? "imagen" : ultimo;
    }

    
}
