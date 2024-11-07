package com.bgsoftware.jarmocker;

import com.bgsoftware.jarmocker.logger.Log;
import javassist.CannotCompileException;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.bytecode.AttributeInfo;
import javassist.bytecode.MethodInfo;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.io.File;
import java.util.Arrays;

public class JarMocker {

    private static final String TAG = JarMocker.class.getSimpleName();

    public static void main(String[] args) {
        Options options = new Options();

        {
            Option input = new Option("i", "input", true, "input file path");
            input.setRequired(true);
            options.addOption(input);
        }

        {
            Option output = new Option("o", "output", true, "output file");
            output.setRequired(true);
            options.addOption(output);
        }

        {
            Option path = new Option("p", "path", true, "mock paths");
            path.setRequired(true);
            options.addOption(path);
        }

        {
            Option includedFiles = new Option("f", "included-files", true, "included files");
            options.addOption(includedFiles);
        }

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException error) {
            Log.w(TAG, error.getMessage());

            new HelpFormatter().printHelp(" ", options);

            System.exit(1);
            return;
        }

        run(cmd.getOptionValue("input"), cmd.getOptionValue("output"), cmd.getOptionValue("path").split(","),
                cmd.getOptionValue("included-files").split(","));
    }

    private static void run(String inputFilePath, String outputFilePath, String[] parsePaths, String[] includedFiles) {
        File inputFile = new File(inputFilePath);

        if (!inputFile.exists()) {
            Log.w(TAG, "The input file does not exist.");
            return;
        }

        JarParser parser = new JarParser(inputFile, parsePaths, includedFiles);

        try {
            parser.forEachClass(ctClass -> {
                boolean checkForImplementation = ctClass.isInterface();

                // Remove static initializer
                MethodInfo staticInitializer = ctClass.getClassFile().getStaticInitializer();
                if (staticInitializer != null)
                    staticInitializer.removeCodeAttribute();

                // We copy all constructors with a stub implementation
                for (CtConstructor constructor : ctClass.getDeclaredConstructors()) {
                    if (!constructor.isEmpty()) {
                        try {
                            constructor.setBody("throw new RuntimeException(\"Stub\");");
                        } catch (CannotCompileException error) {
                            if (error.toString().contains("NotFoundException")) {
                                // An error occurred due to a non-found thing, let's just delete this constructor?
                                ctClass.removeConstructor(constructor);
                            }
                        }
                    }
                }

                // We copy all methods with a stub implementation
                for (CtMethod method : ctClass.getDeclaredMethods()) {
                    if (!method.getName().contains("lambda")) {
                        if (method.getMethodInfo2().isStaticInitializer()) {
                            System.out.println("Static Initializer: " + ctClass.getName() + " " + method.getName());
                            ctClass.removeMethod(method);
                        } else if (!checkForImplementation || !Modifier.isAbstract(method.getModifiers())) {
                            try {
                                method.setBody("throw new RuntimeException(\"Stub\");");
                            } catch (CannotCompileException error) {
                                if (error.toString().contains("NotFoundException")) {
                                    // An error occurred due to a non-found thing, let's just delete this method?
                                    System.out.println("Deleting method: " + ctClass.getName() + " " + method.getName());
                                    ctClass.removeMethod(method);
                                }
                            }
                        }
                    }
                }

                // Remove default field values
                for (CtField field : ctClass.getDeclaredFields()) {
                    Object defaultValue = field.getConstantValue();

                    if (defaultValue == null) {
                        continue;
                    }

                    field.getFieldInfo().removeAttribute("ConstantValue");
                }

            });
        } catch (Exception error) {
            Log.w(TAG, "An error occurred while parsing the jar file", error);
            return;
        }

        try {
            parser.saveResult(new File(outputFilePath));
        } catch (Exception error) {
            Log.w(TAG, "An error occurred while saving output jar file", error);
        }
    }

}
