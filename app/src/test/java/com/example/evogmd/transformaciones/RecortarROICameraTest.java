/*
 * DOCUMENTACIÓN DEL ARCHIVO
 * Pruebas unitarias de RecortarROICamera.
 */
package com.example.evogmd.transformaciones;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Regresiones específicas de la ROI usada con fotografías de cámara. */
public class RecortarROICameraTest {

    /**
     * Verifica que sombra grande pegada al borde no debe ganar ala lesion central.
     */
    @Test
    public void sombraGrandePegadaAlBordeNoDebeGanarALaLesionCentral() {
        final int ancho = 160;
        final int alto = 120;
        int[][] rgb = new int[alto][ancho];
        int[][] gris = new int[alto][ancho];

        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                // Piel/fondo.
                int r = 185;
                int g = 155;
                int b = 145;

                // Sombra grande que toca los bordes derecho e inferior.
                if (x > 118 || y > 100) {
                    r = 120;
                    g = 101;
                    b = 95;
                }

                // Lesión central con contraste y cromaticidad distintos.
                double dx = (x - 78) / 30.0;
                double dy = (y - 61) / 24.0;
                if (dx * dx + dy * dy <= 1.0) {
                    r = 75;
                    g = 82;
                    b = 110;
                }

                int p = 0xFF000000 | (r << 16) | (g << 8) | b;
                rgb[y][x] = p;
                int v = (r + g + b) / 3;
                gris[y][x] = 0xFF000000 | (v << 16) | (v << 8) | v;
            }
        }

        int[][] mascara = RecortarROI.generarMascaraCamara(rgb, gris);
        int blancos = 0;
        for (int[] fila : mascara) {
            for (int pixel : fila) {
                if ((pixel & 0xFF) >= 128) blancos++;
            }
        }

        double fraccion = blancos / (double) (ancho * alto);
        assertTrue("El centro de la lesión debe quedar dentro de la ROI",
                (mascara[61][78] & 0xFF) >= 128);
        assertFalse("La sombra de la esquina no debe formar parte de la ROI",
                (mascara[110][150] & 0xFF) >= 128);
        assertTrue("La ROI quedó demasiado pequeña: " + fraccion,
                fraccion > 0.03);
        assertTrue("La ROI está tomando demasiado fondo: " + fraccion,
                fraccion < 0.45);
    }
}
