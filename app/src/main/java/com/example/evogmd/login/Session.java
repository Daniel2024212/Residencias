/*
 * DOCUMENTACIÓN DEL ARCHIVO
 * Gestiona la sesión local persistida y el límite de cinco minutos; los callbacks de actividad determinan cuándo cerrar o renovar la sesión.
 */
package com.example.evogmd.login;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

/**
 * Control de sesión local.
 *
 * La marca de última interacción se guarda también en SharedPreferences para que
 * no se pierda cuando Android recrea el proceso mientras está abierta la cámara.
 */
public final class Session {
    public static final long TIMEOUT_MS = 5 * 60 * 1000L;

    private static final String PREFS = "evogmd_login_sesion";
    private static final String KEY_USER_ID = "usuario_id";
    private static final String KEY_LAST_INTERACTION = "ultima_interaccion_elapsed";

    private static volatile long lastInteraction = -1L;

    /**
     * Impide crear instancias de esta clase de utilidades.
     */
    private Session() { }

    /**
     * Obtiene las preferencias privadas que contienen el identificador y la última interacción de sesión.
     * 
     * @param context contexto Android para acceder a recursos y almacenamiento privado.
     * @return resultado de la operación descrita.
     */
    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Incluye interacciones dentro de formularios y diálogos en el temporizador. */
    public static class DialogBuilder extends android.app.AlertDialog.Builder {
        private final Context sessionContext;

        /**
         * Inicializa DialogBuilder con los datos recibidos y establece su estado de trabajo.
         * 
         * @param context contexto Android para acceder a recursos y almacenamiento privado.
         */
        public DialogBuilder(Context context) {
            super(context);
            sessionContext = context.getApplicationContext();
        }

        /**
         * Crea el diálogo e intercepta eventos de toque y teclado para renovar la marca de interacción.
         * 
         * @return resultado de la operación descrita.
         */
        @Override public android.app.AlertDialog create() {
            android.app.AlertDialog dialog = super.create();
            android.view.Window window = dialog.getWindow();
            if (window != null) {
                final android.view.Window.Callback delegate = window.getCallback();
                android.view.Window.Callback callback =
                        (android.view.Window.Callback) java.lang.reflect.Proxy.newProxyInstance(
                                android.view.Window.Callback.class.getClassLoader(),
                                new Class<?>[]{android.view.Window.Callback.class},
                                (proxy, method, args) -> {
                                    if ("dispatchTouchEvent".equals(method.getName())
                                            || "dispatchKeyEvent".equals(method.getName())) {
                                        touch(sessionContext);
                                    }
                                    try {
                                        return method.invoke(delegate, args);
                                    } catch (java.lang.reflect.InvocationTargetException e) {
                                        throw e.getCause();
                                    }
                                });
                window.setCallback(callback);
            }
            return dialog;
        }
    }

    /**
     * Guarda el identificador del usuario y la marca inicial de interacción para una nueva sesión.
     * 
     * @param context contexto Android para acceder a recursos y almacenamiento privado.
     * @param id identificador de la cuenta en SQLite.
     */
    public static void start(Context context, long id) {
        long now = SystemClock.elapsedRealtime();
        lastInteraction = now;
        prefs(context).edit()
                .putLong(KEY_USER_ID, id)
                .putLong(KEY_LAST_INTERACTION, now)
                .apply();
    }

    /**
     * Comprueba que hay usuario y que la marca de actividad no supera el límite de cinco minutos.
     * 
     * Comprueba la sesión usando una marca persistente. SystemClock.elapsedRealtime()
     * continúa avanzando aunque el proceso de la app sea destruido, por lo que es
     * apropiado para recuperar la sesión al regresar de la aplicación de cámara.
     * 
     * @param context contexto Android para acceder a recursos y almacenamiento privado.
     * @return true cuando se cumple la condición descrita; false en caso contrario.
     */
    public static boolean isActive(Context context) {
        SharedPreferences p = prefs(context);
        if (p.getLong(KEY_USER_ID, -1L) < 0L) return false;

        long stored = p.getLong(KEY_LAST_INTERACTION, -1L);
        long memory = lastInteraction;
        long last = Math.max(stored, memory);
        long now = SystemClock.elapsedRealtime();

        // Si el teléfono se reinició, elapsedRealtime vuelve a empezar desde cero.
        if (last < 0L || last > now) return false;

        boolean active = now - last < TIMEOUT_MS;
        if (active) lastInteraction = last;
        return active;
    }

    /**
     * Actualiza la marca de actividad si existe un identificador de usuario; por sí mismo no verifica la contraseña ni exige que la marca anterior siga vigente.
     * 
     * Registra actividad y la hace resistente a recreaciones del proceso.
     * 
     * @param context contexto Android para acceder a recursos y almacenamiento privado.
     */
    public static void touch(Context context) {
        SharedPreferences p = prefs(context);
        if (p.getLong(KEY_USER_ID, -1L) < 0L) return;
        long now = SystemClock.elapsedRealtime();
        lastInteraction = now;
        p.edit().putLong(KEY_LAST_INTERACTION, now).apply();
    }

    /**
     * Obtiene la cuenta de la sesión activa; si caducó, elimina la sesión y devuelve null.
     * 
     * @param context contexto Android para acceder a recursos y almacenamiento privado.
     * @return resultado de la operación descrita.
     */
    public static DatabaseHelper.User current(Context context) {
        if (!isActive(context)) {
            logout(context);
            return null;
        }
        long id = prefs(context).getLong(KEY_USER_ID, -1L);
        try (DatabaseHelper db = new DatabaseHelper(context.getApplicationContext())) {
            return db.findUser(id);
        }
    }

    /**
     * Exige una sesión vigente con rol ADMIN y lanza SecurityException si no se cumple.
     * 
     * @param context contexto Android para acceder a recursos y almacenamiento privado.
     */
    public static void requireAdmin(Context context) {
        DatabaseHelper.User user = current(context);
        if (user == null || !user.isAdmin()) {
            throw new SecurityException("Sesión caducada o acceso no autorizado.");
        }
    }

    /**
     * Elimina el identificador y las marcas de sesión en memoria y preferencias.
     * 
     * @param context contexto Android para acceder a recursos y almacenamiento privado.
     */
    public static void logout(Context context) {
        lastInteraction = -1L;
        prefs(context).edit().clear().apply();
    }
}
