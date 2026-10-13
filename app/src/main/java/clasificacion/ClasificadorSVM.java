/*
 * DOCUMENTACIÓN DEL ARCHIVO
 * Puente de compatibilidad de serialización con el paquete Java de escritorio. Conservar nombre de paquete, clases y serialVersionUID para leer modelos existentes.
 */
package clasificacion;
import java.io.Serializable;
import weka.classifiers.Classifier;
import weka.core.Instances;
public final class ClasificadorSVM {
 public static final class ModeloPersistido implements Serializable {
  private static final long serialVersionUID=1L;
  public Instances estructura;
  public Classifier modelo;
  public String reporte;
  public String version;
 }
}
