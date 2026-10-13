import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import weka.core.*;
import clasificacion.ClasificadorSVM.ModeloPersistido;

public class ModelCompatibilityCheck {
 public static void main(String[] args) throws Exception {
  byte[] bytes=Files.readAllBytes(Paths.get(args[0]));
  ModeloPersistido original;
  try(ObjectInputStream in=new ObjectInputStream(new ByteArrayInputStream(bytes))) { original=(ModeloPersistido)in.readObject(); }
  Class<?> svm=com.example.evogmd.clasificacion.ClasificadorSVM.class;
  Method parse=svm.getDeclaredMethod("leerPaquete",byte[].class);parse.setAccessible(true);
  Object parsed=parse.invoke(null,(Object)bytes);
  boolean camera=args.length>1;
  Method activate=svm.getDeclaredMethod(camera?"activarCamara":"activar",parsed.getClass());activate.setAccessible(true);activate.invoke(null,parsed);
  for(int k=0;k<16;k++) {
   double[] vector=new double[8];DenseInstance instance=new DenseInstance(9);instance.setDataset(original.estructura);
   for(int j=0;j<8;j++) { vector[j]=(k*7+j*13)%130;instance.setValue(j,vector[j]); }
   instance.setMissing(8);
   String expected=original.estructura.classAttribute().value((int)original.modelo.classifyInstance(instance));
   String actual=(camera?com.example.evogmd.clasificacion.ClasificadorSVM.predecirImagenCamara(vector):com.example.evogmd.clasificacion.ClasificadorSVM.predecirImagen(vector));
   if(!expected.equals(actual)) throw new AssertionError("Prediction changed");
   if(!Arrays.equals(original.modelo.distributionForInstance(instance),(camera?com.example.evogmd.clasificacion.ClasificadorSVM.obtenerDistribucionCamara(vector):com.example.evogmd.clasificacion.ClasificadorSVM.obtenerDistribucion(vector))))
    throw new AssertionError("Distribution changed");
  }
  if(!original.reporte.equals((camera?com.example.evogmd.clasificacion.ClasificadorSVM.getReporteCamara():com.example.evogmd.clasificacion.ClasificadorSVM.getReporteEvaluacion()))) throw new AssertionError("Report changed");
  try { parse.invoke(null,(Object)new byte[]{1,2,3});throw new AssertionError("Accepted corrupt model"); } catch(InvocationTargetException expected) { }
  // Compatibility with native Android serialized payload (same nested class name and UID).
  Constructor<?> ctor=parsed.getClass().getDeclaredConstructor(String.class, weka.classifiers.Classifier.class, Instances.class, String.class);ctor.setAccessible(true);
  Object nativeModel=ctor.newInstance("EVOGMD-ANDROID-WEKA381-BEST-80-20-KFOLD5-V1",original.modelo,original.estructura,original.reporte);
  ByteArrayOutputStream output=new ByteArrayOutputStream();try(ObjectOutputStream out=new ObjectOutputStream(output)) { out.writeObject(nativeModel); }
  parse.invoke(null,(Object)output.toByteArray());
  System.out.println("PASS: 16 vectores, clases y distribuciones idénticas al modelo original; reporte idéntico; modelo Android nativo aceptado; archivo corrupto rechazado.");
 }
}
