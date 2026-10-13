/*
 * DOCUMENTACIÓN DEL ARCHIVO
 * Gestiona la carga de modelos Weka, la predicción y la compatibilidad de paquetes serializados.
 */
package com.example.evogmd.clasificacion;

import android.content.Context;


import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;

import weka.classifiers.Classifier;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;

/** Evo-GMD + Weka SMO para Android. */
public final class ClasificadorSVM {

    private static final int NUM_CARACTERISTICAS = 8;

    private static final String VERSION_MODELO =
            "EVOGMD-ANDROID-WEKA381-BEST-80-20-KFOLD5-V1";
    private static final String NOMBRE_ARCHIVO_MODELO =
            "modelo_svm_best_android_v1.dat";
    /** Modelo separado para fotos de cámara segmentadas con ROI automática. */
    private static final String MODELO_CAMARA_ASSET =
            "modelo_svm_best_java8_v1.dat";

    private static volatile Classifier modeloEntrenado;
    private static volatile Instances estructuraDataset;
    private static volatile Classifier modeloCamara;
    private static volatile Instances estructuraCamara;
    private static String reporteCamara;

    private static String ultimoReporteEvaluacion;
    private static String ultimoErrorCarga;

    /**
     * Impide crear instancias de esta clase de utilidades.
     */
    private ClasificadorSVM() {
    }

    /**
     * Clasifica el vector con el modelo general activo y devuelve la etiqueta Weka.
     * 
     * @param vector ocho características en orden media y desviación por filtro.
     * @return texto resultante de la operación descrita.
     * @throws Exception si no puede completarse la lectura, validación o procesamiento solicitado.
     */
    public static String predecirImagen(double[] vector) throws Exception {
        asegurarEntrenado();
        validarVector(vector);
        Instance instancia = construirInstancia(vector, estructuraDataset);
        double indice = modeloEntrenado.classifyInstance(instancia);
        return estructuraDataset.classAttribute().value((int) indice);
    }

    /**
     * Obtiene la distribución del SVM general en el orden Nevus, Melanoma; no expresa certeza diagnóstica.
     * 
     * @param vector ocho características en orden media y desviación por filtro.
     * @return resultado de la operación descrita.
     * @throws Exception si no puede completarse la lectura, validación o procesamiento solicitado.
     */
    public static double[] obtenerDistribucion(double[] vector)
            throws Exception {
        asegurarEntrenado();
        validarVector(vector);
        Instance instancia = construirInstancia(vector, estructuraDataset);
        return modeloEntrenado.distributionForInstance(instancia);
    }

    /**
     * Clasifica el vector con el modelo específico para fotos y ROI automática.
     * 
     * Predicción para fotografías tomadas con cámara y ROI automática.
     * 
     * @param vector ocho características en orden media y desviación por filtro.
     * @return texto resultante de la operación descrita.
     * @throws Exception si no puede completarse la lectura, validación o procesamiento solicitado.
     */
    public static String predecirImagenCamara(double[] vector) throws Exception {
        asegurarModeloCamara();
        validarVector(vector);
        Instance instancia = construirInstancia(vector, estructuraCamara);
        double indice = modeloCamara.classifyInstance(instancia);
        return estructuraCamara.classAttribute().value((int) indice);
    }

    /**
     * Obtiene la distribución Nevus, Melanoma del modelo específico de cámara.
     * 
     * Distribución Nevus/Melanoma del modelo específico de cámara.
     * 
     * @param vector ocho características en orden media y desviación por filtro.
     * @return resultado de la operación descrita.
     * @throws Exception si no puede completarse la lectura, validación o procesamiento solicitado.
     */
    public static double[] obtenerDistribucionCamara(double[] vector)
            throws Exception {
        asegurarModeloCamara();
        validarVector(vector);
        Instance instancia = construirInstancia(vector, estructuraCamara);
        return modeloCamara.distributionForInstance(instancia);
    }

    /**
     * Valida el vector y lo vincula con la estructura Weka utilizada por el modelo.
     * 
     * @param vector ocho características en orden media y desviación por filtro.
     * @param estructura cabecera Weka con los atributos y las clases del modelo.
     * @return resultado de la operación descrita.
     */
    private static Instance construirInstancia(
            double[] vector, Instances estructura) {
        DenseInstance instancia = new DenseInstance(estructura.numAttributes());
        instancia.setDataset(estructura);
        for (int i = 0; i < NUM_CARACTERISTICAS; i++) {
            instancia.setValue(i, vector[i]);
        }
        instancia.setMissing(estructura.classIndex());
        return instancia;
    }

    /**
     * Migra el modelo anterior si procede, carga el activo de SQLite y carga el modelo de cámara desde assets; devuelve false y conserva el error si falla.
     * 
     * @param contexto contexto Android para acceder a recursos y almacenamiento privado.
     * @return true cuando se cumple la condición descrita; false en caso contrario.
     */
    public static synchronized boolean cargarModeloGuardado(Context contexto) {
        try (com.example.evogmd.storage.ModelDatabase db = new com.example.evogmd.storage.ModelDatabase(contexto)) {
            if (!db.migrated()) {
                File original=getArchivoModelo(contexto);
                if(original.exists()) {
                    byte[] bytes;
                    try(FileInputStream in=new FileInputStream(original)) {
                        bytes=com.example.evogmd.storage.ModelDatabase.readModel(in);
                    }
                    leerPaquete(bytes); // Si falla, no sustituir silenciosamente el original.
                    db.preserveOriginal(bytes);
                } else db.markMigrated();
            }
            // SQLite es la fuente de verdad: mismo modelo para pantalla,
            // prediccion en galeria y camara, incluso tras reiniciar la app.
            byte[] v1;
            try (java.io.InputStream in = contexto.getAssets().open(MODELO_CAMARA_ASSET)) {
                v1 = com.example.evogmd.storage.ModelDatabase.readModel(in);
            }
            leerPaquete(v1); // Validar antes de modificar la base.
            db.upgradeLegacyV5(v1);
            ModeloPersistido activo = leerPaquete(db.activeModel());
            activar(activo);
            activarCamara(activo);
            return true;
        } catch(Exception error) {
            modeloEntrenado=null;estructuraDataset=null;ultimoReporteEvaluacion=null;
            modeloCamara=null;estructuraCamara=null;reporteCamara=null;
            ultimoErrorCarga=error.getMessage();return false;
        }
    }

    /**
     * Exige permisos, valida el paquete serializado y actualiza el modelo general activo en SQLite y memoria.
     * 
     * @param context contexto Android para acceder a recursos y almacenamiento privado.
     * @param bytes contenido binario del modelo.
     * @param nombre nombre de la muestra, conjunto o recurso según la firma.
     * @throws Exception si no puede completarse la lectura, validación o procesamiento solicitado.
     */
    public static synchronized void importarModeloSql(Context context, byte[] bytes, String nombre) throws Exception {
        com.example.evogmd.login.Session.requireAdmin(context);
        ModeloPersistido model=leerPaquete(bytes);
        try(com.example.evogmd.storage.ModelDatabase db=new com.example.evogmd.storage.ModelDatabase(context)) {
            db.importAdmin(context,bytes,nombre);
        }
        activar(model);
        activarCamara(model);
    }

    /**
     * Asigna a memoria el clasificador general, su estructura y el reporte del paquete validado.
     * 
     * @param model paquete de modelo previamente validado.
     */
    private static void activar(ModeloPersistido model) {
        modeloEntrenado=model.modelo;estructuraDataset=model.estructura;
        ultimoReporteEvaluacion=model.reporte;ultimoErrorCarga=null;
    }

    /**
     * Asigna a memoria el clasificador específico de cámara, su estructura y reporte.
     * 
     * @param model paquete de modelo previamente validado.
     */
    private static void activarCamara(ModeloPersistido model) {
        modeloCamara = model.modelo;
        estructuraCamara = model.estructura;
        reporteCamara = model.reporte;
    }

    /**
     * Deserializa los formatos compatibles y comprueba versión, ocho atributos, orden de clases y capacidad básica de predicción.
     * 
     * @param bytes contenido binario del modelo.
     * @return resultado de la operación descrita.
     * @throws Exception si no puede completarse la lectura, validación o procesamiento solicitado.
     */
    private static ModeloPersistido leerPaquete(byte[] bytes) throws Exception {
        try(ObjectInputStream in=new ObjectInputStream(new java.io.ByteArrayInputStream(bytes))) {
            Object obj=in.readObject();
            ModeloPersistido result;
            if(obj instanceof ModeloPersistido) {
                result=(ModeloPersistido)obj;
                if(!VERSION_MODELO.equals(result.version)) throw new IllegalArgumentException("Versión Android incompatible");
            } else if(obj instanceof clasificacion.ClasificadorSVM.ModeloPersistido) {
                clasificacion.ClasificadorSVM.ModeloPersistido legacy=(clasificacion.ClasificadorSVM.ModeloPersistido)obj;
                if(!"EVOGMD-JAVA8-BEST-80-20-KFOLD5-V1".equals(legacy.version)) throw new IllegalArgumentException("Solo se admite el modelo Java 8 BEST V1");
                result=new ModeloPersistido(legacy.version,legacy.modelo,legacy.estructura,legacy.reporte);
            } else throw new IllegalArgumentException("Formato de modelo incompatible");
            if(result.modelo==null || result.estructura==null) throw new IllegalArgumentException("Modelo incompleto");
            if(result.estructura.numAttributes()!=9) throw new IllegalArgumentException("Se requieren ocho características y la clase");
            result.estructura.setClassIndex(8);
            for(int i=0;i<8;i++) {
                String expected=(i%2==0 ? "Media_Filtro_" : "Desviacion_Filtro_")+(i/2+1);
                if(!result.estructura.attribute(i).isNumeric() || !expected.equals(result.estructura.attribute(i).name()))
                    throw new IllegalArgumentException("Orden de características incompatible");
            }
            if(!result.estructura.classAttribute().isNominal() || result.estructura.numClasses()!=2
                    || !"Nevus".equals(result.estructura.classAttribute().value(0))
                    || !"Melanoma".equals(result.estructura.classAttribute().value(1)))
                throw new IllegalArgumentException("Etiquetas incompatibles");
            double[] prueba=new double[8];
            double prediction=result.modelo.classifyInstance(construirInstancia(prueba,result.estructura));
            if(prediction!=0.0 && prediction!=1.0) throw new IllegalArgumentException("El modelo no puede predecir");
            return result;
        }
    }

    /**
     * Obtiene la ruta privada del archivo de modelo original y crea su carpeta si hace falta.
     * 
     * @param contexto contexto Android para acceder a recursos y almacenamiento privado.
     * @return resultado de la operación descrita.
     */
    private static File getArchivoModelo(Context contexto) {
        File carpeta = new File(contexto.getFilesDir(), "modelos");
        if (!carpeta.exists() && !carpeta.mkdirs()) {
            throw new IllegalStateException("No se pudo crear la carpeta de modelos.");
        }
        return new File(carpeta, NOMBRE_ARCHIVO_MODELO);
    }

    /**
     * Rechaza la predicción si no existe modelo general y estructura cargados.
     */
    private static void asegurarEntrenado() {
        if (!estaEntrenado()) {
            throw new IllegalStateException("No existe un modelo entrenado.");
        }
    }

    /**
     * Rechaza la predicción si no existe modelo y estructura específicos de cámara.
     */
    private static void asegurarModeloCamara() {
        if (modeloCamara == null || estructuraCamara == null) {
            throw new IllegalStateException(
                    "No se pudo cargar el modelo específico para cámara.");
        }
    }

    /**
     * Comprueba que el vector contenga exactamente ocho características finitas.
     * 
     * @param vector ocho características en orden media y desviación por filtro.
     */
    private static void validarVector(double[] vector) {
        if (vector == null || vector.length != NUM_CARACTERISTICAS) {
            throw new IllegalArgumentException(
                    "Weka requiere ocho características Gabor.");
        }
        for (int i = 0; i < vector.length; i++) {
            if (Double.isNaN(vector[i]) || Double.isInfinite(vector[i])) {
                throw new IllegalArgumentException(
                        "F[" + i + "] contiene un valor inválido.");
            }
        }
    }

    /**
     * Indica si el clasificador general y su estructura están disponibles en memoria.
     * 
     * @return true cuando se cumple la condición descrita; false en caso contrario.
     */
    public static boolean estaEntrenado() {
        return modeloEntrenado != null && estructuraDataset != null;
    }

    /**
     * Devuelve el reporte almacenado con el modelo general.
     * 
     * @return texto resultante de la operación descrita.
     */
    public static String getReporteEvaluacion() {
        return ultimoReporteEvaluacion;
    }

    /**
     * Devuelve el reporte almacenado con el modelo de cámara.
     * 
     * @return texto resultante de la operación descrita.
     */
    public static String getReporteCamara() {
        return reporteCamara;
    }

    /**
     * Devuelve el último error registrado al cargar el modelo.
     * 
     * @return texto resultante de la operación descrita.
     */
    public static String getUltimoErrorCarga() {
        return ultimoErrorCarga;
    }

    private static final class ModeloPersistido implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String version;
        private final Classifier modelo;
        private final Instances estructura;
        private final String reporte;

        /**
         * Inicializa ModeloPersistido con los datos recibidos y establece su estado de trabajo.
         * 
         * @param version identificador de formato del paquete serializado.
         * @param modelo clasificador entrenado que se serializa o activa.
         * @param estructura cabecera Weka con los atributos y las clases del modelo.
         * @param reporte texto de métricas asociado al modelo.
         */
        private ModeloPersistido(String version, Classifier modelo,
                                 Instances estructura, String reporte) {
            this.version = version;
            this.modelo = modelo;
            this.estructura = estructura;
            this.reporte = reporte;
        }
    }
}
