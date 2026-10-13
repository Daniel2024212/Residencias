/*
 * DOCUMENTACIÓN DEL ARCHIVO
 * Pruebas unitarias de Binarizacion.
 */
package com.example.evogmd.transformaciones;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BinarizacionTest {

    /**
     * Verifica que conserva gris solo dentro de roi.
     */
    @Test
    public void conservaGrisSoloDentroDeRoi() {
        int[][] gris = {
            {0xFF202020, 0xFF404040},
            {0xFF808080, 0xFFFFFFFF}
        };
        int[][] mascara = {
            {0xFFFFFFFF, 0xFF000000},
            {0xFF000000, 0xFFFFFFFF}
        };

        int[][] resultado = Binarizacion.crearImagen4(gris, mascara);

        assertEquals(0xFF202020, resultado[0][0]);
        assertEquals(0xFF000000, resultado[0][1]);
        assertEquals(0xFF000000, resultado[1][0]);
        assertEquals(0xFFFFFFFF, resultado[1][1]);
    }
}
