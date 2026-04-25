package org.example.csvquery;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/**
 * Escribe el script Python en un archivo temporal,
 * lo ejecuta con ProcessBuilder y devuelve el resultado
 * como String CSV (o un mensaje de error).
 */
public class PandasRunner {

    /**
     * Resultado de ejecutar el script.
     * @param exitoso  true si Python terminó sin errores
     * @param salida   stdout del proceso (CSV o valor escalar)
     * @param error    stderr del proceso (mensaje de error si exitoso=false)
     */
    public record Resultado(boolean exitoso, String salida, String error) {}

    /**
     * Ejecuta el script Python y espera el resultado.
     * @param scriptPython  código Python generado por PandasTranslator
     * @param timeoutSeg    segundos máximos de espera (recomendado: 30)
     */
    public static Resultado ejecutar(String scriptPython, int timeoutSeg) {
        File scriptTemp = null;
        try {
            // Escribir el script en un archivo temporal
            scriptTemp = File.createTempFile("csvquery_", ".py");
            scriptTemp.deleteOnExit();
            Files.writeString(scriptTemp.toPath(), scriptPython, StandardCharsets.UTF_8);

            // Construir el proceso
            String pythonCmd = encontrarPython();

            ProcessBuilder pb = new ProcessBuilder(pythonCmd, scriptTemp.getAbsolutePath());
            pb.redirectErrorStream(false);      // stderr y stdout separados
            pb.environment().put("PYTHONIOENCODING", "utf-8");

            Process proceso = pb.start();

            //Leer stdout y stderr en paralelo para evitar bloqueos
            StringBuffer stdout = new StringBuffer();
            StringBuffer stderr = new StringBuffer();

            Thread hiloOut = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(proceso.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null)
                        stdout.append(line).append("\n");
                } catch (IOException ignored) {}
            });

            Thread hiloErr = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(proceso.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null)
                        stderr.append(line).append("\n");
                } catch (IOException ignored) {}
            });

            hiloOut.start();
            hiloErr.start();

            //Esperar con timeout
            boolean termino = proceso.waitFor(timeoutSeg, java.util.concurrent.TimeUnit.SECONDS);
            hiloOut.join(2000);
            hiloErr.join(2000);

            if (!termino) {
                proceso.destroyForcibly();
                return new Resultado(false, "", "Timeout: el script tardó más de " + timeoutSeg + " segundos.");
            }

            int exitCode = proceso.exitValue();
            if (exitCode != 0) {
                return new Resultado(false, "", stderr.toString().trim());
            }

            return new Resultado(true, stdout.toString().trim(), "");

        } catch (IOException e) {
            return new Resultado(false, "", "Error al crear el proceso Python: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Resultado(false, "", "Ejecución interrumpida.");
        } finally {
            if (scriptTemp != null) scriptTemp.delete();
        }
    }

    /**
     * Detecta el comando Python disponible en el sistema.
     *
     */
    private static String encontrarPython() {
        for (String cmd : new String[]{"python3", "python"}) {
            try {
                Process p = new ProcessBuilder(cmd, "--version")
                    .redirectErrorStream(true)
                    .start();
                p.waitFor();
                if (p.exitValue() == 0) return cmd;
            } catch (Exception ignored) {}
        }
        // Fallback: en Windows suele estar en este path por defecto
        return "python";
    }
}
