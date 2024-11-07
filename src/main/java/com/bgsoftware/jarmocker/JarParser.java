package com.bgsoftware.jarmocker;

import com.bgsoftware.jarmocker.logger.Log;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public class JarParser {

    private static final String TAG = JarParser.class.getSimpleName();

    private final List<CtClass> parsedClasses = new LinkedList<>();

    private final File file;
    private final List<String> parsePaths;
    private final List<String> includedFiles;

    public JarParser(File file, String[] parsePaths, String[] includedFiles) {
        this.file = file;
        this.parsePaths = new LinkedList<>(Arrays.asList(parsePaths));
        this.includedFiles = includedFiles == null ? new LinkedList<>() : new LinkedList<>(Arrays.asList(includedFiles));
        this.includedFiles.add(file.getAbsolutePath());
    }

    public void forEachClass(IParseConsumer consumer) throws IOException, NotFoundException, CannotCompileException {
        ClassPool classPool = new ClassPool(true);

        for (String fileToInclude : includedFiles) {
            addAllJarFiles(classPool, new File(fileToInclude));
        }

        try (JarFile jarFile = new JarFile(this.file)) {
            Enumeration<JarEntry> jarEntries = jarFile.entries();
            while (jarEntries.hasMoreElements()) {
                JarEntry jarEntry = jarEntries.nextElement();
                if (!jarEntry.isDirectory() && jarEntry.getName().endsWith(".class") &&
                        isValidPath(jarEntry.getName().replace("/", "."))) {
                    try (InputStream input = jarFile.getInputStream(jarEntry)) {
                        CtClass inputClass = classPool.makeClass(input);

                        try {
                            consumer.accept(inputClass);
                        } catch (Exception error) {
                            Log.w(TAG, "Skipping class " + jarEntry.getName(), error);
                        }

                        this.parsedClasses.add(inputClass);
                    } catch (Exception error) {
                        Log.w(TAG, "An error occurred while parsing entry " + jarEntry.getName());
                        throw error;
                    }
                }
            }
        }
    }

    public void saveResult(File outputFile) throws IOException, CannotCompileException {
        if (outputFile.exists())
            outputFile.delete();

        outputFile.createNewFile();

        if (this.parsedClasses.isEmpty())
            return;

        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(outputFile.toPath()))) {
            for (CtClass parsedClass : this.parsedClasses) {
                JarEntry jarEntry = new JarEntry(parsedClass.getName().replace(".", "/") + ".class");
                try {
                    output.putNextEntry(jarEntry);
                    output.write(parsedClass.toBytecode());
                } catch (Exception error) {
                    error.printStackTrace();
                } finally {
                    output.closeEntry();
                }
            }
        }
    }

    public interface IParseConsumer {

        void accept(CtClass ctClass) throws NotFoundException, CannotCompileException;

    }

    private boolean isValidPath(String path) {
        for (String parsePath : parsePaths) {
            if (path.startsWith(parsePath))
                return true;
        }

        return false;
    }

    private void addAllJarFiles(ClassPool classPool, File file) throws NotFoundException {
        if (file.isDirectory()) {
            for (File classPathFile : file.listFiles()) {
                addAllJarFiles(classPool, classPathFile);
            }
        } else if (file.getName().endsWith(".jar")) {
            classPool.appendClassPath(file.getAbsolutePath());
        }
    }

}
