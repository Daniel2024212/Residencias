/*
 * DOCUMENTACIÓN DEL ARCHIVO
 * Aplica la máscara ROI sobre la imagen gris para aislar la lesión.
 */
package com.example.evogmd.transformaciones;

/** Construye I(x,y): lesión gris dentro de la máscara y fondo negro. */
public final class Binarizacion {

    /**
     * Impide crear instancias de esta clase de utilidades.
     */
    private Binarizacion() {
    }

    /**
     * Conserva el píxel gris donde la máscara indica lesión y pinta negro el fondo.
     * 
     * @param imagenGris matriz de la imagen completa en escala de grises.
     * @param mascaraRoi máscara de la región de interés, blanca para lesión y negra para fondo.
     * @return resultado de la operación descrita.
     */
    public static int[][] crearImagen4(int[][] imagenGris, int[][] mascaraRoi) {
        validarMismoTamano(imagenGris, mascaraRoi);
        int alto = imagenGris.length;
        int ancho = imagenGris[0].length;
        int[][] imagen4 = new int[alto][ancho];

        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                int valorMascara = mascaraRoi[y][x] & 0xFF;
                if (valorMascara >= 128) {
                    int gris = imagenGris[y][x] & 0xFF;
                    imagen4[y][x] = 0xFF000000
                            | (gris << 16) | (gris << 8) | gris;
                } else {
                    imagen4[y][x] = 0xFF000000;
                }
            }
        }
        return imagen4;
    }

    /**
     * Comprueba que las dos matrices tengan dimensiones compatibles.
     * 
     * @param a primera matriz que se compara.
     * @param b segunda matriz que se compara.
     */
    private static void validarMismoTamano(int[][] a, int[][] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0
                || a[0].length == 0 || b[0].length == 0
                || a.length != b.length || a[0].length != b[0].length) {
            throw new IllegalArgumentException(
                    "La imagen gris y la máscara ROI deben tener el mismo tamaño.");
        }
    }
}
