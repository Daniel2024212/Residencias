/*
 * DOCUMENTACIÓN DEL ARCHIVO
 * Controlador de login y registro. Valida los formularios XML y delega credenciales y roles a SQLite.
 */
package com.example.evogmd.login;

import android.app.Activity;
import com.example.evogmd.R;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteConstraintException;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.*;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Controlador del login y registro definidos en res/layout. */
public class LoginActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private SharedPreferences session;
    private TextView message;
    private Button submit, switchScreen;
    private EditText name, email, password, confirmation;
    private boolean registering, busy;
    private int accent = Color.rgb(8,127,140);

    /**
     * Inicializa la actividad, restaura el estado necesario y prepara sus datos y controles.
     * 
     * @param state estado guardado por Android, o null en una apertura nueva.
     */
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        session = getSharedPreferences("evogmd_login_sesion", MODE_PRIVATE);
        setContentView(R.layout.activity_loading);
        applyInsets();
        worker.execute(() -> {
            try (DatabaseHelper db = new DatabaseHelper(getApplicationContext())) {
                db.getWritableDatabase(); // Crea SQLite automáticamente en el primer inicio.
                DatabaseHelper.User user = Session.current(this);
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    if (user == null) { session.edit().clear().apply(); showAuth(false); }
                    else showHome(user);
                });
            } catch (Exception e) { runOnUiThread(() -> {
                if (!isDestroyed()) { showAuth(false); error("No se pudo abrir la base de datos. Intenta de nuevo."); }
            }); }
        });
    }
    /**
     * Aplica los espacios reservados por las barras del sistema al contenido XML.
     */
    private void applyInsets() {
        View root=findViewById(android.R.id.content);
        root.setOnApplyWindowInsetsListener((view,insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        root.requestApplyInsets();
    }
    /**
     * Selecciona el layout de login o registro y conecta los controles con la autenticación.
     * 
     * @param register true para registro; false para inicio de sesión.
     */
    private void showAuth(boolean register) {
        registering=register; busy=false;
        setContentView(register ? R.layout.activity_register : R.layout.activity_login);
        applyInsets();
        name=findViewById(R.id.name); email=findViewById(R.id.email);
        password=findViewById(R.id.password); confirmation=findViewById(R.id.confirmation);
        message=findViewById(R.id.message); submit=findViewById(R.id.submit);
        switchScreen=findViewById(R.id.switch_screen);
        submit.setOnClickListener(v -> authenticate());
        switchScreen.setOnClickListener(v -> showAuth(!registering));
    }
    /**
     * Presenta un mensaje de validación en el formulario de autenticación.
     * 
     * @param value valor recibido para actualizar el estado o normalizar el texto, según la firma.
     */
    private void error(String value) { message.setTextColor(Color.rgb(180,35,55)); message.setText(value); }
    /**
     * Actualiza el estado de trabajo y habilita o bloquea los controles correspondientes.
     * 
     * @param value valor recibido para actualizar el estado o normalizar el texto, según la firma.
     */
    private void setBusy(boolean value) {
        busy=value; submit.setEnabled(!value); switchScreen.setEnabled(!value);
        email.setEnabled(!value); password.setEnabled(!value);
        if(registering) { name.setEnabled(!value); confirmation.setEnabled(!value); }
        submit.setText(value ? "Procesando…" : (registering ? "Crear cuenta" : "Iniciar sesión"));
    }
    /**
     * Valida los datos y ejecuta registro o login fuera del hilo principal; limpia el arreglo de contraseña al terminar.
     */
    private void authenticate() {
        if(busy) return;
        final boolean register=registering;
        final String mail=email.getText().toString().trim();
        final String username=register ? name.getText().toString().trim() : "";
        if(register && (username.isEmpty() || username.length()>80)) { error("Escribe un nombre de 1 a 80 caracteres."); return; }
        if(mail.length()>254 || !Patterns.EMAIL_ADDRESS.matcher(mail).matches()) { error("Escribe un correo válido."); return; }
        if(password.length()==0 || password.length()>128 || (register && password.length()<8)) { error("Revisa la contraseña"+(register ? ": de 8 a 128 caracteres." : ".")); return; }
        if(register && !password.getText().toString().equals(confirmation.getText().toString())) { error("Las contraseñas no coinciden."); return; }
        final char[] secret=password.getText().toString().toCharArray();
        setBusy(true); message.setText("");
        worker.execute(() -> {
            try (DatabaseHelper db=new DatabaseHelper(getApplicationContext())) {
                if(register) {
                    db.register(username,mail,secret);
                        runOnUiThread(() -> {
                        if(isDestroyed()) return;
                        showAuth(false); email.setText(mail); message.setTextColor(accent); message.setText("Cuenta creada. Ahora inicia sesión.");
                    });
                } else {
                    DatabaseHelper.User user=db.login(mail,secret);
                    runOnUiThread(() -> {
                        if(isDestroyed()) return;
                        setBusy(false);
                        if(user==null) { error("Correo o contraseña incorrectos."); return; }
                        Session.start(this,user.id); showHome(user);
                    });
                }
            } catch (SQLiteConstraintException e) { report("Este correo ya está registrado."); }
            catch (Exception e) { report("No se pudo completar la operación. Intenta de nuevo."); }
            finally { Arrays.fill(secret,'\0'); }
        });
    }
    /**
     * Publica un error de autenticación y desbloquea el formulario si la actividad sigue disponible.
     * 
     * @param value valor recibido para actualizar el estado o normalizar el texto, según la firma.
     */
    private void report(String value) { runOnUiThread(() -> { if(!isDestroyed()) { setBusy(false); error(value); } }); }
    /**
     * Abre la actividad correspondiente al rol y elimina el login de la pila de navegación.
     * 
     * @param user cuenta autenticada que se consulta.
     */
    private void showHome(DatabaseHelper.User user) {
        android.content.Intent intent=new android.content.Intent(this,
                user.isAdmin() ? com.example.evogmd.AdminActivity.class : com.example.evogmd.UsuarioActivity.class);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent); finish();
    }
    /**
     * Libera el ejecutor y, cuando corresponde, retira las comprobaciones pendientes de sesión.
     */
    @Override protected void onDestroy() { worker.shutdown(); super.onDestroy(); }
}
