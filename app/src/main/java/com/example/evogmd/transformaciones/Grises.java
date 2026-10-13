/*
 * DOCUMENTACIÓN DEL ARCHIVO
 * Calcula grises mediante el promedio aritmético de los canales RGB.
 */
package com.example.evogmd.transformaciones;

/** Conversión de una matriz ARGB a escala de grises. */
public final class Grises {

    /**
     * Impide crear instancias de esta clase de utilidades.
     */
    private Grises() {
    }

    /**
     * Calcula el promedio de los canales rojo, verde y azul y lo guarda en la matriz de salida.
     * 
     * @param matrizRgb matriz de píxeles ARGB de entrada.
     * @param matrizProcesada matriz de salida donde se escriben los píxeles grises.
     */
    public static void convertirAGris(int[][] matrizRgb, int[][] matrizProcesada) {
        if (matrizRgb == null || matrizProcesada == null
                || matrizRgb.length != matrizProcesada.length
                || matrizRgb[0].length != matrizProcesada[0].length) {
            throw new IllegalArgumentException("Las matrices deben tener el mismo tamaño.");
        }

        int alto = matrizRgb.length;
        int ancho = matrizRgb[0].length;
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                int rgb = matrizRgb[y][x];
                int r = (rgb >>> 16) & 0xFF;
                int g = (rgb >>> 8) & 0xFF;
                int b = rgb & 0xFF;
                int gris = (r + g + b) / 3;
                matrizProcesada[y][x] = 0xFF000000
                        | (gris << 16) | (gris << 8) | gris;
            }
        }
    }
}
