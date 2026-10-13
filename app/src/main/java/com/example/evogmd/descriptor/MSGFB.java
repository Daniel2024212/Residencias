/*
 * DOCUMENTACIÓN DEL ARCHIVO
 * Banco multiescala de cuatro filtros Gabor; produce ocho características en orden media, desviación por filtro.
 */
package com.example.evogmd.descriptor;

import com.example.evogmd.filtrogabor.FiltroGabor;
import com.example.evogmd.transformaciones.ParametrosGabor;

import java.util.Locale;

/**
 * Multi-Scale Gabor Filter Bank. Cada filtro genera media y desviación dentro
 * de la ROI; cuatro filtros producen las ocho características usadas por SVM.
 */
public final class MSGFB {

    private final int nf;
    private final double[][] parametros;
    private final double[][][] respuestas;
    private final double[] vectorF;

    /**
     * Inicializa MSGFB con los datos recibidos y establece su estado de trabajo.
     * 
     * @param tipoParametros nombre del banco de parámetros Gabor.
     */
    public MSGFB(String tipoParametros) {
        parametros = ParametrosGabor.obtenerParametros(tipoParametros);
        nf = parametros.length;
        respuestas = new double[nf][][];
        vectorF = new double[nf * 2];
    }

    /**
     * Aplica el banco Gabor y ordena el vector como media y desviación de cada filtro dentro de la ROI.
     * 
     * Calcula el descriptor y conserva las respuestas para visualización.
     * 
     * @param imagen imagen de entrada representada por el tipo de la firma.
     * @param mascaraRoi máscara de la región de interés, blanca para lesión y negra para fondo.
     * @return resultado de la operación descrita.
     */
    public double[] calcularDescriptor(int[][] imagen, int[][] mascaraRoi) {
        return calcularDescriptor(imagen, mascaraRoi, true);
    }

    /**
     * Calcula el mismo vector sin conservar las matrices de respuesta, para reducir memoria en lotes.
     * 
     * Calcula el descriptor sin retener matrices grandes durante el lote.
     * 
     * @param imagen imagen de entrada representada por el tipo de la firma.
     * @param mascaraRoi máscara de la región de interés, blanca para lesión y negra para fondo.
     * @return resultado de la operación descrita.
     */
    public double[] calcularDescriptorLigero(int[][] imagen, int[][] mascaraRoi) {
        return calcularDescriptor(imagen, mascaraRoi, false);
    }

    /**
     * Aplica el banco Gabor y ordena el vector como media y desviación de cada filtro dentro de la ROI.
     * 
     * @param imagen imagen de entrada representada por el tipo de la firma.
     * @param mascaraRoi máscara de la región de interés, blanca para lesión y negra para fondo.
     * @param guardarRespuestas indica si se conservan las matrices de convolución.
     * @return resultado de la operación descrita.
     */
    private double[] calcularDescriptor(int[][] imagen, int[][] mascaraRoi,
                                        boolean guardarRespuestas) {
        validarEntradas(imagen, mascaraRoi);
        for (int j = 0; j < nf; j++) {
            int m = normalizarM(parametros[j][0]);
            double[][] kernel = FiltroGabor.generarKernel(
                    m,
                    parametros[j][1],
                    parametros[j][2],
                    parametros[j][3],
                    parametros[j][4],
                    parametros[j][5]);

            double[][] respuesta = FiltroGabor.aplicarConvolucion(imagen, kernel);
            respuestas[j] = guardarRespuestas ? respuesta : null;

            double media = FiltroGabor.calcularMediaEnROI(respuesta, mascaraRoi);
            double desviacion = FiltroGabor.calcularDesviacionEnROI(
                    respuesta, media, mascaraRoi);
            vectorF[j * 2] = media;
            vectorF[j * 2 + 1] = desviacion;
        }
        return vectorF.clone();
    }

    /**
     * Redondea el tamaño del kernel y aplica un mínimo de tres; conserva tamaños pares.
     * 
     * Conserva los valores pares de m presentes en la tabla publicada. Para un
     * kernel par, el centro utilizado por la convolución es m/2.
     * 
     * @param valor valor numérico que se transforma o valida.
     * @return resultado de la operación descrita.
     */
    private static int normalizarM(double valor) {
        return Math.max((int) Math.round(valor), 3);
    }

    /**
     * Comprueba que imagen y máscara sean no vacías y tengan las mismas dimensiones.
     * 
     * @param imagen imagen de entrada representada por el tipo de la firma.
     * @param mascaraRoi máscara de la región de interés, blanca para lesión y negra para fondo.
     */
    private static void validarEntradas(int[][] imagen, int[][] mascaraRoi) {
        if (imagen == null || mascaraRoi == null
                || imagen.length == 0 || mascaraRoi.length == 0
                || imagen[0].length == 0 || mascaraRoi[0].length == 0) {
            throw new IllegalArgumentException("La imagen y la máscara son obligatorias.");
        }
        if (imagen.length != mascaraRoi.length
                || imagen[0].length != mascaraRoi[0].length) {
            throw new IllegalArgumentException(
                    "La imagen y la máscara deben tener el mismo tamaño.");
        }
    }

    /**
     * Devuelve las matrices de respuesta conservadas; pueden contener null si se calculó el descriptor ligero.
     * 
     * @return resultado de la operación descrita.
     */
    public double[][][] getRespuestas() {
        return respuestas;
    }

    /**
     * Devuelve una copia del vector de características calculado.
     * 
     * @return resultado de la operación descrita.
     */
    public double[] getVectorF() {
        return vectorF.clone();
    }

    /**
     * Devuelve el número de filtros del banco.
     * 
     * @return resultado de la operación descrita.
     */
    public int getNf() {
        return nf;
    }

    /**
     * Devuelve una copia de las filas de parámetros del banco.
     * 
     * @return resultado de la operación descrita.
     */
    public double[][] getParametros() {
        double[][] copia = new double[parametros.length][];
        for (int i = 0; i < parametros.length; i++) {
            copia[i] = parametros[i].clone();
        }
        return copia;
    }

    /**
     * Construye una representación textual del estado de este objeto.
     * 
     * @return texto resultante de la operación descrita.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("F = [");
        for (int i = 0; i < vectorF.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(String.format(Locale.US, "%.4f", vectorF[i]));
        }
        return sb.append(']').toString();
    }
}
