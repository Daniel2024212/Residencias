/*
 * DOCUMENTACIÓN DEL ARCHIVO
 * Convierte entre Bitmap y matrices ARGB indexadas como [fila][columna].
 */
package com.example.evogmd.transformaciones;

import android.graphics.Bitmap;

/** Conversión entre Bitmap de Android y matrices ARGB. */
public final class TransImgToMatriz {

    /**
     * Impide crear instancias de esta clase de utilidades.
     */
    private TransImgToMatriz() {
    }

    /**
     * Copia los píxeles ARGB del bitmap a una matriz indexada por fila y columna.
     * 
     * @param matrizArgb matriz de destino de los píxeles ARGB.
     * @param imagen imagen de entrada representada por el tipo de la firma.
     */
    public static void imgToMatriz(int[][] matrizArgb, Bitmap imagen) {
        if (imagen == null || matrizArgb == null) {
            throw new IllegalArgumentException("La imagen y la matriz son obligatorias.");
        }
        int ancho = imagen.getWidth();
        int alto = imagen.getHeight();
        if (matrizArgb.length != alto || matrizArgb[0].length != ancho) {
            throw new IllegalArgumentException("La matriz no coincide con el Bitmap.");
        }

        int[] pixeles = new int[ancho * alto];
        imagen.getPixels(pixeles, 0, ancho, 0, 0, ancho, alto);
        for (int y = 0; y < alto; y++) {
            System.arraycopy(pixeles, y * ancho, matrizArgb[y], 0, ancho);
        }
    }

    /**
     * Convierte una matriz de píxeles ARGB a Bitmap.
     * 
     * @param matriz matriz de píxeles que se convertirá.
     * @return bitmap resultante de la conversión o consulta.
     */
    public static Bitmap matrizToBitmap(int[][] matriz) {
        if (matriz == null || matriz.length == 0 || matriz[0].length == 0) {
            throw new IllegalArgumentException("La matriz está vacía.");
        }
        int alto = matriz.length;
        int ancho = matriz[0].length;
        int[] pixeles = new int[ancho * alto];
        for (int y = 0; y < alto; y++) {
            System.arraycopy(matriz[y], 0, pixeles, y * ancho, ancho);
        }
        return Bitmap.createBitmap(pixeles, ancho, alto, Bitmap.Config.ARGB_8888);
    }

    /**
     * Escala un bitmap al ancho y alto solicitados con el modo de filtrado indicado.
     * 
     * @param original datos originales que se copian o transforman según la firma.
     * @param ancho número de columnas de destino.
     * @param alto número de filas de destino.
     * @param filtrar true para interpolar al escalar; false para conservar bordes discretos de máscara.
     * @return bitmap resultante de la conversión o consulta.
     */
    public static Bitmap redimensionar(Bitmap original, int ancho, int alto,
                                       boolean filtrar) {
        if (original == null) {
            throw new IllegalArgumentException("El Bitmap original es nulo.");
        }
        return Bitmap.createScaledBitmap(original, ancho, alto, filtrar);
    }
}
