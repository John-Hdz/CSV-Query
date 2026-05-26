package org.example.csvquery;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;


public class PandasRunner {

    public record Resultado(boolean exitoso, String salida, String error) {}


    public static Resultado ejecutar(String scriptPython, int timeoutSeg) {
        File scriptTemp = null;
        try {
            // Escribir el script en un archivo temporal
            scriptTemp = File.createTempFile("csvquery_", ".py");
            scriptTemp.deleteOnExit();
            Files.writeString(scriptTemp.toPath(), scriptPython, StandardCharsets.UTF_8);

            String pythonCmd = encontrarPython();

            ProcessBuilder pb = new ProcessBuilder(pythonCmd, scriptTemp.getAbsolutePath());
            pb.redirectErrorStream(false);      // stderr y stdout separados
            pb.environment().put("PYTHONIOENCODING", "utf-8");

            Process proceso = pb.start();

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
