/*
 * DOCUMENTACIÓN DEL ARCHIVO
 * Coordina redimensionamiento a 720 por 480, grises, ROI, lesión y descriptor Gabor.
 */
package com.example.evogmd.imagen;

import android.graphics.Bitmap;

import com.example.evogmd.descriptor.MSGFB;
import com.example.evogmd.filtrogabor.FiltroGabor;
import com.example.evogmd.transformaciones.Binarizacion;
import com.example.evogmd.transformaciones.Grises;
import com.example.evogmd.transformaciones.RecortarROI;
import com.example.evogmd.transformaciones.TransImgToMatriz;

import java.util.Locale;

/**
 * Procesamiento completo de una imagen dermatológica en Android.
 *
 * Imagen 1: original; Imagen 2: gris; Imagen 3: máscara; Imagen 4: lesión
 * gris; después se calculan cuatro respuestas Gabor y ocho características.
 */
public final class Imagen {

    public static final int ANCHO = 720;
    public static final int ALTO = 480;
    private static final String BANCO_GABOR = "BEST";

    private final int[][] matrizRgb;
    private final int[][] matrizGrisCompleta;
    private final int[][] mascaraRoi;
    private final int[][] matrizLesion;
    private final int[][][] matricesGaborVisuales;
    private final double[] vectorCaracteristicas;
    private final MSGFB msgfb;

    /**
     * Inicializa Imagen con los datos recibidos y establece su estado de trabajo.
     * 
     * Usa segmentación automática; para PH2 se recomienda el otro constructor.
     * 
     * @param original datos originales que se copian o transforman según la firma.
     */
    public Imagen(Bitmap original) {
        this(original, null, true);
    }

    /**
     * Inicializa Imagen con los datos recibidos y establece su estado de trabajo.
     * 
     * @param original datos originales que se copian o transforman según la firma.
     * @param mascaraExperta máscara proporcionada, o null para segmentación automática.
     */
    public Imagen(Bitmap original, Bitmap mascaraExperta) {
        this(original, mascaraExperta, true);
    }

    /**
     * Inicializa Imagen con los datos recibidos y establece su estado de trabajo.
     * 
     * @param generarVisuales false durante entrenamiento por lote para reducir RAM.
     * 
     * @param original datos originales que se copian o transforman según la firma.
     * @param mascaraExperta máscara proporcionada, o null para segmentación automática.
     */
    public Imagen(Bitmap original, Bitmap mascaraExperta,
                  boolean generarVisuales) {
        this(original,mascaraExperta,generarVisuales,false);
    }

    /**
     * Inicializa Imagen con los datos recibidos y establece su estado de trabajo.
     * 
     * @param original datos originales que se copian o transforman según la firma.
     * @param mascaraExperta máscara proporcionada, o null para segmentación automática.
     * @param generarVisuales true para conservar respuestas visuales; false para reducir memoria.
     * @param fotoCamara true para usar la ruta de segmentación de cámara si no hay máscara experta.
     */
    public Imagen(Bitmap original, Bitmap mascaraExperta, boolean generarVisuales, boolean fotoCamara) {
        if (original == null) {
            throw new IllegalArgumentException("La imagen original es nula.");
        }

        Bitmap redimensionada = TransImgToMatriz.redimensionar(
                original, ANCHO, ALTO, true);
        matrizRgb = new int[ALTO][ANCHO];
        matrizGrisCompleta = new int[ALTO][ANCHO];
        TransImgToMatriz.imgToMatriz(matrizRgb, redimensionada);
        Grises.convertirAGris(matrizRgb, matrizGrisCompleta);

        if (mascaraExperta != null) {
            mascaraRoi = convertirMascaraExperta(mascaraExperta);
        } else {
            mascaraRoi = fotoCamara ? RecortarROI.generarMascaraCamara(matrizRgb,matrizGrisCompleta)
                    : RecortarROI.generarMascara(matrizRgb, matrizGrisCompleta);
        }
        validarMascaraNoVacia(mascaraRoi);

        matrizLesion = Binarizacion.crearImagen4(
                matrizGrisCompleta, mascaraRoi);

        msgfb = new MSGFB(BANCO_GABOR);
        vectorCaracteristicas = generarVisuales
                ? msgfb.calcularDescriptor(matrizLesion, mascaraRoi)
                : msgfb.calcularDescriptorLigero(matrizLesion, mascaraRoi);

        if (generarVisuales) {
            double[][][] respuestas = msgfb.getRespuestas();
            matricesGaborVisuales = new int[msgfb.getNf()][][];
            for (int i = 0; i < msgfb.getNf(); i++) {
                matricesGaborVisuales[i] = FiltroGabor.normalizarParaVisualizar(
                        respuestas[i], mascaraRoi);
            }
        } else {
            matricesGaborVisuales = new int[0][][];
        }
    }

    /**
     * Escala la máscara sin interpolación suave y convierte sus píxeles a blanco o negro usando el umbral 128.
     * 
     * @param original datos originales que se copian o transforman según la firma.
     * @return resultado de la operación descrita.
     */
    private static int[][] convertirMascaraExperta(Bitmap original) {
        Bitmap mascara = TransImgToMatriz.redimensionar(
                original, ANCHO, ALTO, false);
        int[] pixeles = new int[ANCHO * ALTO];
        mascara.getPixels(pixeles, 0, ANCHO, 0, 0, ANCHO, ALTO);
        int[][] resultado = new int[ALTO][ANCHO];
        for (int y = 0; y < ALTO; y++) {
            for (int x = 0; x < ANCHO; x++) {
                int rgb = pixeles[y * ANCHO + x];
                int r = (rgb >>> 16) & 0xFF;
                int g = (rgb >>> 8) & 0xFF;
                int b = rgb & 0xFF;
                int gris = (r + g + b) / 3;
                resultado[y][x] = gris >= 128
                        ? 0xFFFFFFFF : 0xFF000000;
            }
        }
        return resultado;
    }

    /**
     * Rechaza una máscara que no contiene ningún píxel de lesión.
     * 
     * @param mascara máscara que delimita la región de interés.
     */
    private static void validarMascaraNoVacia(int[][] mascara) {
        int blancos = 0;
        for (int[] fila : mascara) {
            for (int pixel : fila) {
                if ((pixel & 0xFF) >= 128) {
                    blancos++;
                }
            }
        }
        if (blancos == 0) {
            throw new IllegalArgumentException("La máscara ROI no contiene lesión.");
        }
    }

    /**
     * Formatea los seis parámetros de cada filtro y sus dos características calculadas.
     * 
     * @return texto resultante de la operación descrita.
     */
    public String construirReporteVector() {
        StringBuilder texto = new StringBuilder();
        double[][] parametros = msgfb.getParametros();
        for (int i = 0; i < parametros.length; i++) {
            texto.append(String.format(Locale.US,
                    "Filtro %d: m=%d, sigma=%.8f, theta=%.8f, "
                            + "lambda=%.8f, gamma=%.8f, psi=%.8f%n",
                    i + 1,
                    Math.max((int) Math.round(parametros[i][0]), 3),
                    parametros[i][1], parametros[i][2], parametros[i][3],
                    parametros[i][4], parametros[i][5]));
            texto.append(String.format(Locale.US,
                    "  media=% .8f, desviación=% .8f%n",
                    vectorCaracteristicas[i * 2],
                    vectorCaracteristicas[i * 2 + 1]));
        }
        return texto.toString();
    }

    /**
     * Construye un bitmap a partir de la matriz RGB redimensionada a la resolución del procesamiento.
     * 
     * @return bitmap resultante de la conversión o consulta.
     */
    public Bitmap getBitmapOriginal() {
        return TransImgToMatriz.matrizToBitmap(matrizRgb);
    }

    /**
     * Construye la visualización de la imagen completa en escala de grises.
     * 
     * @return bitmap resultante de la conversión o consulta.
     */
    public Bitmap getBitmapGris() {
        return TransImgToMatriz.matrizToBitmap(matrizGrisCompleta);
    }

    /**
     * Construye la visualización binaria de la ROI.
     * 
     * @return bitmap resultante de la conversión o consulta.
     */
    public Bitmap getBitmapMascara() {
        return TransImgToMatriz.matrizToBitmap(mascaraRoi);
    }

    /**
     * Construye la visualización de la lesión gris con fondo negro.
     * 
     * @return bitmap resultante de la conversión o consulta.
     */
    public Bitmap getBitmapLesion() {
        return TransImgToMatriz.matrizToBitmap(matrizLesion);
    }

    /**
     * Convierte a bitmap la respuesta visual normalizada del filtro indicado.
     * 
     * @param indice posición de base cero del elemento solicitado.
     * @return bitmap resultante de la conversión o consulta.
     */
    public Bitmap getBitmapGabor(int indice) {
        if (indice < 0 || indice >= matricesGaborVisuales.length) {
            throw new IllegalArgumentException("Índice Gabor fuera de rango.");
        }
        return TransImgToMatriz.matrizToBitmap(matricesGaborVisuales[indice]);
    }

    /**
     * Devuelve una copia del vector de características para evitar cambios externos sobre el arreglo interno.
     * 
     * @return resultado de la operación descrita.
     */
    public double[] getVectorCaracteristicas() {
        return vectorCaracteristicas.clone();
    }

    /**
     * Devuelve la matriz interna de la máscara; el llamador debe evitar modificarla.
     * 
     * @return resultado de la operación descrita.
     */
    public int[][] getMascaraROI() {
        return mascaraRoi;
    }

    /**
     * Devuelve la matriz interna de grises de toda la imagen.
     * 
     * @return resultado de la operación descrita.
     */
    public int[][] getMatrizGrisCompleta() {
        return matrizGrisCompleta;
    }

    /**
     * Devuelve la matriz interna de píxeles ARGB.
     * 
     * @return resultado de la operación descrita.
     */
    public int[][] getMatrizRGB() {
        return matrizRgb;
    }

    /**
     * Devuelve la matriz interna que conserva gris dentro de la ROI y negro fuera.
     * 
     * @return resultado de la operación descrita.
     */
    public int[][] getMatrizLesion() {
        return matrizLesion;
    }

    /**
     * Devuelve las matrices internas normalizadas para visualización; no son el vector enviado al SVM.
     * 
     * @return resultado de la operación descrita.
     */
    public int[][][] getMatricesGaborVisuales() {
        return matricesGaborVisuales;
    }

    /**
     * Devuelve el descriptor que produjo las respuestas y características.
     * 
     * @return resultado de la operación descrita.
     */
    public MSGFB getMSGFB() {
        return msgfb;
    }
}
